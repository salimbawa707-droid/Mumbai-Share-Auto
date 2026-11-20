package com.mumbaishareauto.app.auth

import android.app.Activity
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

data class FirebaseAuthSessionUser(
    val uid: String,
    val phoneNumber: String
)

sealed class OtpDispatchResult {
    data class CodeSent(
        val verificationId: String,
        val resendToken: Any? = null
    ) : OtpDispatchResult()

    data class AutoSignedIn(
        val user: FirebaseAuthSessionUser
    ) : OtpDispatchResult()

    data class Failure(
        val message: String,
        val isConfigurationError: Boolean = false
    ) : OtpDispatchResult()
}

sealed class OtpVerificationResult {
    data class Verified(val user: FirebaseAuthSessionUser) : OtpVerificationResult()
    data class Failure(
        val message: String,
        val isConfigurationError: Boolean = false
    ) : OtpVerificationResult()
}

sealed class FirestoreProfileResult<out T> {
    data class Success<T>(val data: T) : FirestoreProfileResult<T>()
    data class Failure(
        val message: String,
        val diagnosticJson: String? = null
    ) : FirestoreProfileResult<Nothing>()
}

interface PhoneAuthGateway {
    val isFirebaseConfigured: Boolean
    val authSessionUser: StateFlow<FirebaseAuthSessionUser?>
    suspend fun sendOtp(
        activity: Activity,
        e164PhoneNumber: String,
        resendToken: Any? = null
    ): OtpDispatchResult

    suspend fun verifyOtpCode(
        verificationId: String,
        otpCode: String
    ): OtpVerificationResult

    fun signOut()
}

interface UserProfileRemoteDataSource {
    val isFirestoreConfigured: Boolean
    suspend fun saveAuthenticatedProfile(
        profile: AuthenticatedUserProfile
    ): FirestoreProfileResult<AuthenticatedUserProfile>

    suspend fun fetchAuthenticatedProfile(
        uid: String
    ): FirestoreProfileResult<AuthenticatedUserProfile?>
}

object FirebaseRuntimeInspector {
    const val UNCONFIGURED_MESSAGE =
        "Firebase is not configured in this build yet. Add google-services.json inside MumbaiShareAuto/app/, register your Android SHA-1/SHA-256 certificates, and enable Phone Number sign-in in Firebase Console."

    fun isFirebaseInitialized(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context.applicationContext).isNotEmpty() }
            .getOrDefault(false)
}

class FirebasePhoneAuthGateway(
    private val context: Context
) : PhoneAuthGateway {

    override val isFirebaseConfigured: Boolean
        get() = FirebaseRuntimeInspector.isFirebaseInitialized(context)

    private val auth: FirebaseAuth?
        get() = if (isFirebaseConfigured) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null

    private val _authSessionUser = MutableStateFlow(readCurrentUser())
    override val authSessionUser: StateFlow<FirebaseAuthSessionUser?> = _authSessionUser.asStateFlow()

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _authSessionUser.value = user?.let {
            FirebaseAuthSessionUser(
                uid = it.uid,
                phoneNumber = it.phoneNumber.orEmpty()
            )
        }
    }

    init {
        auth?.addAuthStateListener(authStateListener)
    }

    fun unregister() {
        auth?.removeAuthStateListener(authStateListener)
    }

    private fun readCurrentUser(): FirebaseAuthSessionUser? {
        val current = auth?.currentUser ?: return null
        return FirebaseAuthSessionUser(
            uid = current.uid,
            phoneNumber = current.phoneNumber.orEmpty()
        )
    }

    override suspend fun sendOtp(
        activity: Activity,
        e164PhoneNumber: String,
        resendToken: Any?
    ): OtpDispatchResult {
        val firebaseAuth = auth
            ?: return OtpDispatchResult.Failure(
                message = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE,
                isConfigurationError = true
            )

        val cleanPhone = AuthDomainValidator.normalizeIndianE164(e164PhoneNumber)
            ?: return OtpDispatchResult.Failure("Invalid Indian (+91) mobile number.")

        val initialOutcome = suspendCancellableCoroutine<InternalCallbackOutcome> { continuation ->
            val completed = AtomicBoolean(false)

            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(InternalCallbackOutcome.AutoCredential(credential))
                    }
                }

                override fun onVerificationFailed(exception: FirebaseException) {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(
                            InternalCallbackOutcome.Failed(mapFirebasePhoneException(exception))
                        )
                    }
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(InternalCallbackOutcome.CodeSent(verificationId, token))
                    }
                }
            }

            val builder = PhoneAuthOptions.newBuilder(firebaseAuth)
                .setPhoneNumber(cleanPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)

            if (resendToken is PhoneAuthProvider.ForceResendingToken) {
                builder.setForceResendingToken(resendToken)
            }

            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        }

        return when (initialOutcome) {
            is InternalCallbackOutcome.CodeSent -> {
                OtpDispatchResult.CodeSent(
                    verificationId = initialOutcome.verificationId,
                    resendToken = initialOutcome.token
                )
            }
            is InternalCallbackOutcome.AutoCredential -> {
                when (val signIn = signInWithCredential(firebaseAuth, initialOutcome.credential)) {
                    is OtpVerificationResult.Verified -> OtpDispatchResult.AutoSignedIn(signIn.user)
                    is OtpVerificationResult.Failure -> OtpDispatchResult.Failure(
                        message = signIn.message,
                        isConfigurationError = signIn.isConfigurationError
                    )
                }
            }
            is InternalCallbackOutcome.Failed -> {
                OtpDispatchResult.Failure(initialOutcome.message)
            }
        }
    }

    override suspend fun verifyOtpCode(
        verificationId: String,
        otpCode: String
    ): OtpVerificationResult {
        val firebaseAuth = auth
            ?: return OtpVerificationResult.Failure(
                message = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE,
                isConfigurationError = true
            )

        if (verificationId.isBlank()) {
            return OtpVerificationResult.Failure("OTP verification session missing. Request a fresh OTP.")
        }
        val otpValidation = AuthDomainValidator.validateOtpCode(otpCode)
        if (otpValidation is AuthFieldValidation.Invalid) {
            return OtpVerificationResult.Failure(otpValidation.message)
        }

        val credential = PhoneAuthProvider.getCredential(verificationId.trim(), otpCode.trim())
        return signInWithCredential(firebaseAuth, credential)
    }

    override fun signOut() {
        auth?.signOut()
        _authSessionUser.value = null
    }

    private suspend fun signInWithCredential(
        firebaseAuth: FirebaseAuth,
        credential: PhoneAuthCredential
    ): OtpVerificationResult = try {
        val result = firebaseAuth.signInWithCredential(credential).await()
        val signedInUser = result.user
            ?: return OtpVerificationResult.Failure("Firebase Authentication returned no signed-in user.")
        val session = FirebaseAuthSessionUser(
            uid = signedInUser.uid,
            phoneNumber = signedInUser.phoneNumber.orEmpty()
        )
        _authSessionUser.value = session
        OtpVerificationResult.Verified(session)
    } catch (c: CancellationException) {
        throw c
    } catch (e: Exception) {
        OtpVerificationResult.Failure(mapFirebasePhoneException(e))
    }

    private sealed class InternalCallbackOutcome {
        data class CodeSent(
            val verificationId: String,
            val token: PhoneAuthProvider.ForceResendingToken
        ) : InternalCallbackOutcome()

        data class AutoCredential(val credential: PhoneAuthCredential) : InternalCallbackOutcome()
        data class Failed(val message: String) : InternalCallbackOutcome()
    }

    private fun mapFirebasePhoneException(exception: Exception): String = when (exception) {
        is FirebaseAuthInvalidCredentialsException -> {
            "Invalid verification code or mobile number format. Check the 6-digit OTP and try again."
        }
        is FirebaseTooManyRequestsException -> {
            "Too many SMS OTP requests from this device or number. Please wait before retrying."
        }
        is FirebaseNetworkException -> {
            "Network error while contacting Firebase Authentication. Check mobile data or Wi-Fi."
        }
        else -> {
            val raw = exception.message.orEmpty()
            when {
                raw.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) ->
                    "Phone Number sign-in provider is not enabled in Firebase Authentication Console."
                raw.contains("APP_NOT_AUTHORIZED", ignoreCase = true) ||
                    raw.contains("SHA", ignoreCase = true) ->
                    "App fingerprint (SHA-1/SHA-256) is not authorized for Phone Auth in Firebase Console."
                raw.isNotBlank() -> raw
                else -> "Phone OTP authentication failed."
            }
        }
    }
}

class FirestoreUserProfileRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : UserProfileRemoteDataSource {

    override val isFirestoreConfigured: Boolean
        get() = FirebaseRuntimeInspector.isFirebaseInitialized(context)

    private val firestore: FirebaseFirestore?
        get() = if (isFirestoreConfigured) runCatching { FirebaseFirestore.getInstance() }.getOrNull() else null

    private val auth: FirebaseAuth?
        get() = if (isFirestoreConfigured) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null

    override suspend fun saveAuthenticatedProfile(
        profile: AuthenticatedUserProfile
    ): FirestoreProfileResult<AuthenticatedUserProfile> = withContext(ioDispatcher) {
        val cleanProfile = profile.normalized()
        val validation = cleanProfile.validate()
        if (validation is AuthFieldValidation.Invalid) {
            return@withContext FirestoreProfileResult.Failure(validation.message)
        }

        val db = firestore ?: return@withContext FirestoreProfileResult.Failure(
            message = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE
        )
        val path = "${AuthenticatedUserProfile.COLLECTION_USERS}/${cleanProfile.uid}"

        try {
            val docRef = db.collection(AuthenticatedUserProfile.COLLECTION_USERS).document(cleanProfile.uid)
            val existingSnapshot = docRef.get().await()

            if (!existingSnapshot.exists()) {
                val payload = cleanProfile.toBaseFirestoreFields().toMutableMap().apply {
                    put(AuthenticatedUserProfile.FIELD_CREATED_AT, FieldValue.serverTimestamp())
                    put(AuthenticatedUserProfile.FIELD_UPDATED_AT, FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    AuthenticatedUserProfile.FIELD_ROLE to cleanProfile.role.name,
                    AuthenticatedUserProfile.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                )
                if (cleanProfile.role == AuthUserRole.DRIVER && !cleanProfile.driverAutoRegNumber.isNullOrBlank()) {
                    updatePayload[AuthenticatedUserProfile.FIELD_DRIVER_AUTO_REG_NUMBER] =
                        cleanProfile.driverAutoRegNumber
                } else {
                    updatePayload[AuthenticatedUserProfile.FIELD_DRIVER_AUTO_REG_NUMBER] =
                        FieldValue.delete()
                }
                docRef.update(updatePayload).await()
            }

            val savedSnap = docRef.get().await()
            val savedData = savedSnap.data
            val restored = if (savedData != null) {
                AuthenticatedUserProfile.fromFirestoreFields(
                    documentId = cleanProfile.uid,
                    data = savedData,
                    createdAtMs = (savedSnap.getTimestamp(AuthenticatedUserProfile.FIELD_CREATED_AT) as? Timestamp)?.toDate()?.time,
                    updatedAtMs = (savedSnap.getTimestamp(AuthenticatedUserProfile.FIELD_UPDATED_AT) as? Timestamp)?.toDate()?.time
                )
            } else {
                null
            }
            FirestoreProfileResult.Success(restored ?: cleanProfile)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            handleFirestoreException(
                exception = e,
                operationType = FirestoreOperationType.WRITE,
                path = path
            )
        }
    }

    override suspend fun fetchAuthenticatedProfile(
        uid: String
    ): FirestoreProfileResult<AuthenticatedUserProfile?> = withContext(ioDispatcher) {
        val cleanUid = uid.trim()
        if (cleanUid.isEmpty() || !AuthDomainValidator.UID_REGEX.matches(cleanUid)) {
            return@withContext FirestoreProfileResult.Failure("Invalid user UID.")
        }

        val db = firestore ?: return@withContext FirestoreProfileResult.Failure(
            message = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE
        )
        val path = "${AuthenticatedUserProfile.COLLECTION_USERS}/$cleanUid"

        try {
            val snap = db.collection(AuthenticatedUserProfile.COLLECTION_USERS)
                .document(cleanUid)
                .get()
                .await()
            if (!snap.exists()) {
                return@withContext FirestoreProfileResult.Success(null)
            }
            val data = snap.data ?: return@withContext FirestoreProfileResult.Success(null)
            val parsed = AuthenticatedUserProfile.fromFirestoreFields(
                documentId = cleanUid,
                data = data,
                createdAtMs = (snap.getTimestamp(AuthenticatedUserProfile.FIELD_CREATED_AT) as? Timestamp)?.toDate()?.time,
                updatedAtMs = (snap.getTimestamp(AuthenticatedUserProfile.FIELD_UPDATED_AT) as? Timestamp)?.toDate()?.time
            )
            FirestoreProfileResult.Success(parsed)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            handleFirestoreException(
                exception = e,
                operationType = FirestoreOperationType.GET,
                path = path
            )
        }
    }

    private fun <T> handleFirestoreException(
        exception: Exception,
        operationType: FirestoreOperationType,
        path: String
    ): FirestoreProfileResult<T> {
        val currentUser = auth?.currentUser
        val info = FirestoreErrorInfo(
            error = exception.message ?: exception.javaClass.simpleName,
            operationType = operationType,
            path = path,
            uid = currentUser?.uid,
            phoneNumber = currentUser?.phoneNumber
        )
        val friendlyMessage = when (exception) {
            is FirebaseFirestoreException -> when (exception.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Cloud Firestore permission denied while saving profile. Deploy firestore.rules for /users/{uid}."
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Cloud Firestore is currently unreachable or offline. Check internet connectivity."
                FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                    "Firestore quota exceeded. Free tier daily read/write limits reset the next day."
                else -> exception.message ?: "Cloud Firestore request failed."
            }
            else -> exception.message ?: "Unable to communicate with Cloud Firestore."
        }
        return FirestoreProfileResult.Failure(
            message = friendlyMessage,
            diagnosticJson = info.toDiagnosticJson()
        )
    }
}
