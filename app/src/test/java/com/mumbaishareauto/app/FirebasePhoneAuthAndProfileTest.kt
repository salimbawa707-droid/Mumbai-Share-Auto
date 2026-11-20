package com.mumbaishareauto.app

import android.app.Activity
import com.mumbaishareauto.app.auth.AuthDomainValidator
import com.mumbaishareauto.app.auth.AuthFieldValidation
import com.mumbaishareauto.app.auth.AuthUserRole
import com.mumbaishareauto.app.auth.AuthenticatedUserProfile
import com.mumbaishareauto.app.auth.FirebaseAuthSessionUser
import com.mumbaishareauto.app.auth.FirestoreErrorInfo
import com.mumbaishareauto.app.auth.FirestoreOperationType
import com.mumbaishareauto.app.auth.FirestoreProfileResult
import com.mumbaishareauto.app.auth.OtpDispatchResult
import com.mumbaishareauto.app.auth.OtpVerificationResult
import com.mumbaishareauto.app.auth.PhoneAuthGateway
import com.mumbaishareauto.app.auth.UserProfileRemoteDataSource
import com.mumbaishareauto.app.data.local.FavoriteRouteRepository
import com.mumbaishareauto.app.data.local.RoomBookingRepository
import com.mumbaishareauto.app.data.local.RoomDriverRouteRepository
import com.mumbaishareauto.app.data.local.RoomNotificationsRepository
import com.mumbaishareauto.app.data.local.RoomUserPreferencesRepository
import com.mumbaishareauto.app.ui.MumbaiShareAutoViewModel
import com.mumbaishareauto.app.ui.PhoneAuthStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirebasePhoneAuthAndProfileTest {

    @Test
    fun indianMobileValidation_acceptsValidTenDigitNumbersAndRejectsInvalidInputs() {
        assertTrue(AuthDomainValidator.validateIndianMobileDigits("9820123456").isValid)
        assertTrue(AuthDomainValidator.validateIndianMobileDigits("6000000001").isValid)
        assertEquals("+919820123456", AuthDomainValidator.normalizeIndianE164("9820123456"))
        assertEquals("+919820123456", AuthDomainValidator.normalizeIndianE164("+91 98201-23456"))

        assertTrue(AuthDomainValidator.validateIndianMobileDigits("") is AuthFieldValidation.Invalid)
        assertTrue(AuthDomainValidator.validateIndianMobileDigits("982012345") is AuthFieldValidation.Invalid)
        assertTrue(AuthDomainValidator.validateIndianMobileDigits("5820123456") is AuthFieldValidation.Invalid)
        assertNull(AuthDomainValidator.normalizeIndianE164("12345"))
    }

    @Test
    fun driverAutoRegValidation_acceptsMaharashtraRtoFormatAndRejectsInvalidPlates() {
        assertTrue(AuthDomainValidator.validateDriverAutoRegNumber("MH 02 AB 1234").isValid)
        assertTrue(AuthDomainValidator.validateDriverAutoRegNumber("mh03c9999").isValid)
        assertEquals("MH02AB1234", AuthDomainValidator.normalizeAutoRegNumber("MH 02 AB 1234"))
        assertEquals("MH 02 AB 1234", AuthDomainValidator.formatAutoRegForDisplay("MH02AB1234"))

        assertTrue(AuthDomainValidator.validateDriverAutoRegNumber("") is AuthFieldValidation.Invalid)
        assertTrue(AuthDomainValidator.validateDriverAutoRegNumber("INVALID") is AuthFieldValidation.Invalid)
        assertTrue(AuthDomainValidator.validateDriverAutoRegNumber("1234567890") is AuthFieldValidation.Invalid)
    }

    @Test
    fun authenticatedUserProfile_enforcesRoleSpecificAutoRegistrationRules() {
        val passengerProfile = AuthenticatedUserProfile(
            uid = "uid_pax_01",
            phoneNumber = "+919820123456",
            role = AuthUserRole.PASSENGER,
            driverAutoRegNumber = null
        )
        assertTrue(passengerProfile.validate().isValid)
        val paxMap = passengerProfile.toBaseFirestoreFields()
        assertFalse(paxMap.containsKey(AuthenticatedUserProfile.FIELD_DRIVER_AUTO_REG_NUMBER))

        val driverMissingReg = AuthenticatedUserProfile(
            uid = "uid_drv_01",
            phoneNumber = "+919820123457",
            role = AuthUserRole.DRIVER,
            driverAutoRegNumber = null
        )
        assertTrue(driverMissingReg.validate() is AuthFieldValidation.Invalid)

        val validDriver = driverMissingReg.copy(driverAutoRegNumber = "MH 04 XY 4321")
        assertTrue(validDriver.validate().isValid)
        val drvMap = validDriver.toBaseFirestoreFields()
        assertEquals("MH04XY4321", drvMap[AuthenticatedUserProfile.FIELD_DRIVER_AUTO_REG_NUMBER])

        val parsed = AuthenticatedUserProfile.fromFirestoreFields("uid_drv_01", drvMap)
        assertNotNull(parsed)
        assertEquals(AuthUserRole.DRIVER, parsed?.role)
        assertEquals("MH04XY4321", parsed?.driverAutoRegNumber)
    }

    @Test
    fun firestoreErrorInfo_formatsDiagnosticJsonCleanly() {
        val json = FirestoreErrorInfo(
            error = "PERMISSION_DENIED",
            operationType = FirestoreOperationType.WRITE,
            path = "users/uid_1",
            uid = "uid_1",
            phoneNumber = "+919820123456"
        ).toDiagnosticJson()
        assertTrue(json.contains("\"operationType\":\"write\""))
        assertTrue(json.contains("\"path\":\"users/uid_1\""))
        assertTrue(json.contains("\"userId\":\"uid_1\""))
    }

    @Test
    fun viewModelPhoneOtpFlow_verifiesDriverSavesFirestoreProfileAndSyncsPreferredMode() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val prefs = InMemorySharedPreferences()
            val favDao = FakeFavoriteRouteDao()
            val prefsDao = FakeUserPreferencesDao()
            val bookingDao = FakeBookingDao()
            val driverDao = FakeDriverRouteDao()
            val notificationDao = FakeNotificationDao()
            val txRunner = FakeTransactionRunner()
            val repoScope = CoroutineScope(SupervisorJob() + dispatcher)

            val fakeAuth = FakePhoneAuthGateway(isFirebaseConfigured = true)
            val fakeProfileStore = FakeUserProfileRemoteDataSource(isFirestoreConfigured = true)

            val vm = MumbaiShareAutoViewModel(
                favoriteRoutesRepo = FavoriteRouteRepository(prefs, favDao, prefsDao, txRunner, dispatcher, repoScope),
                bookingsRepo = RoomBookingRepository(bookingDao, prefsDao, txRunner, null, dispatcher, repoScope),
                driverRoutesRepo = RoomDriverRouteRepository(driverDao, txRunner, dispatcher, repoScope),
                userPreferencesRepo = RoomUserPreferencesRepository(prefsDao, txRunner, dispatcher, repoScope),
                notificationsRepo = RoomNotificationsRepository(notificationDao, txRunner, dispatcher, repoScope),
                phoneAuthGateway = fakeAuth,
                userProfileRemoteDataSource = fakeProfileStore
            )
            advanceUntilIdle()

            // Select Driver role without plate -> validation blocks OTP send cleanly
            vm.selectAuthRole(AuthUserRole.DRIVER)
            vm.updatePhoneDigitsInput("9876543210")
            vm.sendOtpInternal(
                isResend = false,
                dispatch = { _, _ -> OtpDispatchResult.CodeSent("verif_123") }
            )
            assertEquals(PhoneAuthStep.ENTER_DETAILS, vm.phoneAuthState.value.step)
            assertNotNull(vm.phoneAuthState.value.errorMessage)

            // Provide valid MH auto registration number -> OTP sends cleanly
            vm.updateDriverAutoRegInput("mh 02 ab 1234")
            vm.sendOtpInternal(
                isResend = false,
                dispatch = { phone, _ ->
                    assertEquals("+919876543210", phone)
                    OtpDispatchResult.CodeSent("verif_123")
                }
            )
            advanceTimeBy(1_500L)
            assertEquals(PhoneAuthStep.ENTER_OTP, vm.phoneAuthState.value.step)
            assertEquals("verif_123", vm.phoneAuthState.value.verificationId)

            // Verify 6-digit OTP and save profile to Firestore
            var callbackFired = false
            vm.updateOtpCodeInput("654321")
            vm.verifyOtpAndSaveProfile { callbackFired = true }
            advanceUntilIdle()

            assertTrue(callbackFired)
            assertEquals(PhoneAuthStep.AUTHENTICATED, vm.phoneAuthState.value.step)
            val saved = vm.phoneAuthState.value.authenticatedProfile
            assertNotNull(saved)
            assertEquals("uid_9876543210", saved?.uid)
            assertEquals("+919876543210", saved?.phoneNumber)
            assertEquals(AuthUserRole.DRIVER, saved?.role)
            assertEquals("MH02AB1234", saved?.driverAutoRegNumber)
            assertEquals(UserMode.DRIVER.name, vm.userPreferences.value.preferredMode)
            assertNotNull(fakeProfileStore.storedProfiles["uid_9876543210"])

            // Sign out resets session cleanly
            vm.signOutPhoneAuth()
            advanceUntilIdle()
            assertEquals(PhoneAuthStep.ENTER_DETAILS, vm.phoneAuthState.value.step)
            assertNull(vm.phoneAuthState.value.authenticatedProfile)

            repoScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun viewModelPhoneOtpFlow_blocksCleanlyWhenFirebaseUnconfigured() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val prefs = InMemorySharedPreferences()
            val prefsDao = FakeUserPreferencesDao()
            val txRunner = FakeTransactionRunner()
            val repoScope = CoroutineScope(SupervisorJob() + dispatcher)
            val fakeAuth = FakePhoneAuthGateway(isFirebaseConfigured = false)
            val fakeProfileStore = FakeUserProfileRemoteDataSource(isFirestoreConfigured = false)

            val vm = MumbaiShareAutoViewModel(
                favoriteRoutesRepo = FavoriteRouteRepository(prefs, FakeFavoriteRouteDao(), prefsDao, txRunner, dispatcher, repoScope),
                bookingsRepo = RoomBookingRepository(FakeBookingDao(), prefsDao, txRunner, null, dispatcher, repoScope),
                driverRoutesRepo = RoomDriverRouteRepository(FakeDriverRouteDao(), txRunner, dispatcher, repoScope),
                userPreferencesRepo = RoomUserPreferencesRepository(prefsDao, txRunner, dispatcher, repoScope),
                notificationsRepo = RoomNotificationsRepository(FakeNotificationDao(), txRunner, dispatcher, repoScope),
                phoneAuthGateway = fakeAuth,
                userProfileRemoteDataSource = fakeProfileStore
            )
            advanceUntilIdle()

            vm.selectAuthRole(AuthUserRole.PASSENGER)
            vm.updatePhoneDigitsInput("9820123456")
            vm.sendOtpInternal(
                isResend = false,
                dispatch = { _, _ -> OtpDispatchResult.CodeSent("unused") }
            )
            assertFalse(vm.phoneAuthState.value.isFirebaseConfigured)
            assertTrue(vm.phoneAuthState.value.errorMessage?.contains("google-services.json") == true)
            repoScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}

internal class FakePhoneAuthGateway(
    override val isFirebaseConfigured: Boolean
) : PhoneAuthGateway {
    private val _session = MutableStateFlow<FirebaseAuthSessionUser?>(null)
    override val authSessionUser: StateFlow<FirebaseAuthSessionUser?> = _session.asStateFlow()

    fun emitTestUser(uid: String, phoneNumber: String) {
        _session.value = FirebaseAuthSessionUser(uid = uid, phoneNumber = phoneNumber)
    }

    override suspend fun sendOtp(
        activity: Activity,
        e164PhoneNumber: String,
        resendToken: Any?
    ): OtpDispatchResult = OtpDispatchResult.CodeSent("verif_test")

    override suspend fun verifyOtpCode(
        verificationId: String,
        otpCode: String
    ): OtpVerificationResult {
        val user = FirebaseAuthSessionUser(
            uid = "uid_9876543210",
            phoneNumber = "+919876543210"
        )
        _session.value = user
        return OtpVerificationResult.Verified(user)
    }

    override fun signOut() {
        _session.value = null
    }
}

internal class FakeUserProfileRemoteDataSource(
    override val isFirestoreConfigured: Boolean
) : UserProfileRemoteDataSource {
    val storedProfiles = linkedMapOf<String, AuthenticatedUserProfile>()

    override suspend fun saveAuthenticatedProfile(
        profile: AuthenticatedUserProfile
    ): FirestoreProfileResult<AuthenticatedUserProfile> {
        storedProfiles[profile.uid] = profile
        return FirestoreProfileResult.Success(profile)
    }

    override suspend fun fetchAuthenticatedProfile(
        uid: String
    ): FirestoreProfileResult<AuthenticatedUserProfile?> =
        FirestoreProfileResult.Success(storedProfiles[uid])
}
