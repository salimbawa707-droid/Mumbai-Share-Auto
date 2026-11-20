package com.mumbaishareauto.app.ui

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mumbaishareauto.app.BookingAgreement
import com.mumbaishareauto.app.BookingLifecycleState
import com.mumbaishareauto.app.FareNegotiationSession
import com.mumbaishareauto.app.data.remote.FareNegotiationFirestoreRepository
import com.mumbaishareauto.app.UserMode
import com.mumbaishareauto.app.auth.AuthDomainValidator
import com.mumbaishareauto.app.auth.AuthFieldValidation
import com.mumbaishareauto.app.auth.AuthUserRole
import com.mumbaishareauto.app.auth.AuthenticatedUserProfile
import com.mumbaishareauto.app.auth.FirebaseAuthSessionUser
import com.mumbaishareauto.app.auth.FirebasePhoneAuthGateway
import com.mumbaishareauto.app.auth.FirebaseRuntimeInspector
import com.mumbaishareauto.app.auth.FirestoreProfileResult
import com.mumbaishareauto.app.auth.FirestoreUserProfileRepository
import com.mumbaishareauto.app.auth.OtpDispatchResult
import com.mumbaishareauto.app.auth.OtpVerificationResult
import com.mumbaishareauto.app.auth.PhoneAuthGateway
import com.mumbaishareauto.app.auth.UserProfileRemoteDataSource
import com.mumbaishareauto.app.data.local.BookingsDataSource
import com.mumbaishareauto.app.data.local.DriverRoutesDataSource
import com.mumbaishareauto.app.data.local.FavoriteRouteRepository
import com.mumbaishareauto.app.data.local.FavoriteRoutesDataSource
import com.mumbaishareauto.app.data.local.NotificationsDataSource
import com.mumbaishareauto.app.data.local.PersistenceResult
import com.mumbaishareauto.app.data.local.RoomBookingRepository
import com.mumbaishareauto.app.data.local.RoomDriverRouteRepository
import com.mumbaishareauto.app.data.local.RoomNotificationsRepository
import com.mumbaishareauto.app.data.local.RoomUserPreferencesRepository
import com.mumbaishareauto.app.data.local.UserPreferencesDataSource
import com.mumbaishareauto.app.data.local.db.DriverRouteEntity
import com.mumbaishareauto.app.data.local.db.MumbaiShareAutoDatabase
import com.mumbaishareauto.app.data.local.db.NotificationEntity
import com.mumbaishareauto.app.data.local.db.RoomTransactionRunner
import com.mumbaishareauto.app.data.local.db.UserPreferencesEntity
import com.mumbaishareauto.app.data.remote.FirebaseFirestoreRepository
import com.mumbaishareauto.app.data.remote.FirestoreActionResult
import com.mumbaishareauto.app.data.remote.LiveAutoRoute
import com.mumbaishareauto.app.data.remote.LiveRouteTransactionEngine
import com.mumbaishareauto.app.data.remote.LiveRoutesDataSource
import com.mumbaishareauto.app.data.remote.LiveRoutesStreamState
import com.mumbaishareauto.app.data.remote.PublishRouteCommand
import com.mumbaishareauto.app.data.remote.RouteSeatLock
import com.mumbaishareauto.app.model.StationSide
import com.mumbaishareauto.app.ui.theme.ThemePreference
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PhoneAuthStep {
    ENTER_DETAILS,
    ENTER_OTP,
    AUTHENTICATED
}

data class PhoneAuthUiState(
    val step: PhoneAuthStep = PhoneAuthStep.ENTER_DETAILS,
    val selectedRole: AuthUserRole = AuthUserRole.PASSENGER,
    val phoneDigitsInput: String = "",
    val driverAutoRegInput: String = "",
    val otpCodeInput: String = "",
    val verificationId: String? = null,
    val isLoading: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val signedInUser: FirebaseAuthSessionUser? = null,
    val authenticatedProfile: AuthenticatedUserProfile? = null,
    val isFirebaseConfigured: Boolean = false
)

data class FareNegotiationUiState(
    val sessions: List<FareNegotiationSession> = emptyList(),
    val activeSession: FareNegotiationSession? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

data class LiveRouteActionState(
    val isPublishingRoute: Boolean = false,
    val lockingPostingId: String? = null,
    val releasingPostingId: String? = null,
    val deactivatingPostingId: String? = null,
    val selectedQueuePostingId: String? = null,
    val selectedQueueLocks: List<RouteSeatLock> = emptyList(),
    val lastActionMessage: String? = null,
    val lastActionError: String? = null,
    val lastSeatLock: RouteSeatLock? = null
)

/**
 * Central ViewModel coordinating reactive Room-backed repositories, Firebase Phone OTP Auth,
 * Cloud Firestore user profile persistence, and real-time share-auto route & transaction sync.
 */
class MumbaiShareAutoViewModel(
    private val favoriteRoutesRepo: FavoriteRoutesDataSource,
    private val bookingsRepo: BookingsDataSource,
    private val driverRoutesRepo: DriverRoutesDataSource,
    private val userPreferencesRepo: UserPreferencesDataSource,
    private val notificationsRepo: NotificationsDataSource,
    private val phoneAuthGateway: PhoneAuthGateway? = null,
    private val userProfileRemoteDataSource: UserProfileRemoteDataSource? = null,
    private val liveRoutesGateway: LiveRoutesDataSource? = null,
    private val fareNegotiationRepository: FareNegotiationFirestoreRepository? = null
) : ViewModel() {

    val favoriteKeys: StateFlow<Set<String>> = favoriteRoutesRepo.favoriteKeys
    val bookings: StateFlow<List<BookingAgreement>> = bookingsRepo.bookings
    val driverRoutes: StateFlow<List<DriverRouteEntity>> = driverRoutesRepo.driverRoutes
    val userPreferences: StateFlow<UserPreferencesEntity> = userPreferencesRepo.preferences
    val notifications: StateFlow<List<NotificationEntity>> = notificationsRepo.notifications
    val unreadNotificationCount: StateFlow<Int> = notificationsRepo.unreadCount

    private val _phoneAuthState = MutableStateFlow(
        PhoneAuthUiState(
            isFirebaseConfigured = phoneAuthGateway?.isFirebaseConfigured == true,
            signedInUser = phoneAuthGateway?.authSessionUser?.value,
            step = if (phoneAuthGateway?.authSessionUser?.value != null) {
                PhoneAuthStep.AUTHENTICATED
            } else {
                PhoneAuthStep.ENTER_DETAILS
            }
        )
    )
    val phoneAuthState: StateFlow<PhoneAuthUiState> = _phoneAuthState.asStateFlow()

    private val _liveRoutesState = MutableStateFlow<LiveRoutesStreamState>(
        if (liveRoutesGateway?.isFirestoreConfigured == true) {
            LiveRoutesStreamState.AuthRequired()
        } else {
            LiveRoutesStreamState.NotConfigured()
        }
    )
    val liveRoutesState: StateFlow<LiveRoutesStreamState> = _liveRoutesState.asStateFlow()

    private val _driverLiveRoutes = MutableStateFlow<List<LiveAutoRoute>>(emptyList())
    val driverLiveRoutes: StateFlow<List<LiveAutoRoute>> = _driverLiveRoutes.asStateFlow()

    private val _liveRouteActionState = MutableStateFlow(LiveRouteActionState())
    val liveRouteActionState: StateFlow<LiveRouteActionState> = _liveRouteActionState.asStateFlow()

    private val _fareNegotiationState = MutableStateFlow(FareNegotiationUiState())
    val fareNegotiationState: StateFlow<FareNegotiationUiState> = _fareNegotiationState.asStateFlow()
    private var fareSessionsObserverJob: Job? = null
    private var activeFareSessionObserverJob: Job? = null

    private var lastResendToken: Any? = null
    private var cooldownJob: Job? = null
    private var driverRoutesObserverJob: Job? = null
    private var queueObserverJob: Job? = null

    val persistenceError: StateFlow<String?> = combine(
        favoriteRoutesRepo.lastError,
        bookingsRepo.lastError,
        driverRoutesRepo.lastError,
        userPreferencesRepo.lastError,
        notificationsRepo.lastError
    ) { favErr, bookErr, driverErr, prefErr, notifErr ->
        listOfNotNull(favErr, bookErr, driverErr, prefErr, notifErr).firstOrNull()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    init {
        viewModelScope.launch {
            notificationsRepo.seedLocalNoticesIfEmpty()
        }
        if (liveRoutesGateway != null) {
            viewModelScope.launch {
                liveRoutesGateway.observeActiveRoutes().collect { streamState ->
                    _liveRoutesState.value = streamState
                }
            }
        }
        if (phoneAuthGateway != null) {
            viewModelScope.launch {
                phoneAuthGateway.authSessionUser.collect { sessionUser ->
                    if (sessionUser != null) {
                        _phoneAuthState.update { state ->
                            state.copy(
                                signedInUser = sessionUser,
                                step = PhoneAuthStep.AUTHENTICATED,
                                isFirebaseConfigured = phoneAuthGateway.isFirebaseConfigured
                            )
                        }
                        attachDriverRoutesListener(sessionUser.uid)
                        attachFareNegotiationListener(sessionUser.uid)
                        refreshAuthenticatedProfileFromFirestore(sessionUser.uid)
                    } else {
                        driverRoutesObserverJob?.cancel()
                        fareSessionsObserverJob?.cancel()
                        fareSessionsObserverJob = null
                        activeFareSessionObserverJob?.cancel()
                        activeFareSessionObserverJob = null
                        _fareNegotiationState.value = FareNegotiationUiState()
                        driverRoutesObserverJob = null
                        _driverLiveRoutes.value = emptyList()
                        queueObserverJob?.cancel()
                        queueObserverJob = null
                        _liveRouteActionState.update { it.copy(selectedQueueLocks = emptyList(), selectedQueuePostingId = null) }
                        _phoneAuthState.update { state ->
                            state.copy(
                                signedInUser = null,
                                authenticatedProfile = null,
                                step = if (state.step == PhoneAuthStep.AUTHENTICATED) {
                                    PhoneAuthStep.ENTER_DETAILS
                                } else {
                                    state.step
                                },
                                isFirebaseConfigured = phoneAuthGateway.isFirebaseConfigured
                            )
                        }
                    }
                }
            }
        }
    }

    private fun attachDriverRoutesListener(uid: String) {
        val gateway = liveRoutesGateway ?: return
        driverRoutesObserverJob?.cancel()
        driverRoutesObserverJob = viewModelScope.launch {
            gateway.observeDriverActiveRoutes(uid).collect { routes ->
                _driverLiveRoutes.value = routes
            }
        }
    }

    fun selectAuthRole(role: AuthUserRole) {
        _phoneAuthState.update { state ->
            state.copy(
                selectedRole = role,
                errorMessage = null
            )
        }
    }

    fun updatePhoneDigitsInput(rawInput: String) {
        val sanitized = AuthDomainValidator.sanitizeTenDigitInput(rawInput)
        _phoneAuthState.update { state ->
            state.copy(
                phoneDigitsInput = sanitized,
                errorMessage = null
            )
        }
    }

    fun updateDriverAutoRegInput(rawInput: String) {
        val cleaned = rawInput.uppercase()
            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' }
            .take(15)
        _phoneAuthState.update { state ->
            state.copy(
                driverAutoRegInput = cleaned,
                errorMessage = null
            )
        }
    }

    fun updateOtpCodeInput(rawInput: String) {
        val sanitized = AuthDomainValidator.sanitizeOtpInput(rawInput)
        _phoneAuthState.update { state ->
            state.copy(
                otpCodeInput = sanitized,
                errorMessage = null
            )
        }
    }

    fun changePhoneNumber() {
        cooldownJob?.cancel()
        lastResendToken = null
        _phoneAuthState.update { state ->
            state.copy(
                step = PhoneAuthStep.ENTER_DETAILS,
                otpCodeInput = "",
                verificationId = null,
                resendCooldownSeconds = 0,
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    fun sendOtp(
        activity: Activity,
        isResend: Boolean = false,
        onAutoSignedIn: () -> Unit = {}
    ) {
        val gateway = phoneAuthGateway
        if (gateway == null || !gateway.isFirebaseConfigured) {
            _phoneAuthState.update {
                it.copy(
                    isFirebaseConfigured = false,
                    errorMessage = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE
                )
            }
            return
        }
        sendOtpInternal(
            isResend = isResend,
            dispatch = { e164Phone, resendToken ->
                gateway.sendOtp(activity, e164Phone, resendToken)
            },
            onAutoSignedIn = onAutoSignedIn
        )
    }

    internal fun sendOtpInternal(
        isResend: Boolean = false,
        dispatch: suspend (e164Phone: String, resendToken: Any?) -> OtpDispatchResult,
        onAutoSignedIn: () -> Unit = {}
    ) {
        val currentState = _phoneAuthState.value
        val gateway = phoneAuthGateway
        if (gateway == null || !gateway.isFirebaseConfigured) {
            _phoneAuthState.update {
                it.copy(
                    isFirebaseConfigured = false,
                    errorMessage = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE
                )
            }
            return
        }

        val validation = AuthDomainValidator.validateSendOtpRequest(
            role = currentState.selectedRole,
            tenDigitMobile = currentState.phoneDigitsInput,
            driverAutoRegInput = currentState.driverAutoRegInput
        )
        if (validation is AuthFieldValidation.Invalid) {
            _phoneAuthState.update { it.copy(errorMessage = validation.message) }
            return
        }

        val e164Phone = AuthDomainValidator.normalizeIndianE164(currentState.phoneDigitsInput)
        if (e164Phone == null) {
            _phoneAuthState.update { it.copy(errorMessage = "Enter a valid 10-digit +91 mobile number.") }
            return
        }

        viewModelScope.launch {
            _phoneAuthState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = "Sending OTP to $e164Phone..."
                )
            }

            when (val result = dispatch(e164Phone, if (isResend) lastResendToken else null)) {
                is OtpDispatchResult.CodeSent -> {
                    lastResendToken = result.resendToken
                    _phoneAuthState.update {
                        it.copy(
                            step = PhoneAuthStep.ENTER_OTP,
                            verificationId = result.verificationId,
                            otpCodeInput = "",
                            isLoading = false,
                            errorMessage = null,
                            statusMessage = "6-digit SMS verification code sent to $e164Phone."
                        )
                    }
                    startResendCooldown()
                }

                is OtpDispatchResult.AutoSignedIn -> {
                    persistVerifiedProfile(
                        sessionUser = result.user,
                        fallbackE164Phone = e164Phone,
                        onSuccess = onAutoSignedIn
                    )
                }

                is OtpDispatchResult.Failure -> {
                    _phoneAuthState.update {
                        it.copy(
                            isLoading = false,
                            isFirebaseConfigured = !result.isConfigurationError && gateway.isFirebaseConfigured,
                            statusMessage = null,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    fun verifyOtpAndSaveProfile(onSuccess: () -> Unit = {}) {
        val currentState = _phoneAuthState.value
        val gateway = phoneAuthGateway
        if (gateway == null || !gateway.isFirebaseConfigured) {
            _phoneAuthState.update {
                it.copy(
                    isFirebaseConfigured = false,
                    errorMessage = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE
                )
            }
            return
        }

        val verificationId = currentState.verificationId
        if (verificationId.isNullOrBlank()) {
            _phoneAuthState.update {
                it.copy(errorMessage = "OTP verification session missing. Tap Resend OTP or go back.")
            }
            return
        }

        val otpValidation = AuthDomainValidator.validateOtpCode(currentState.otpCodeInput)
        if (otpValidation is AuthFieldValidation.Invalid) {
            _phoneAuthState.update { it.copy(errorMessage = otpValidation.message) }
            return
        }

        val detailsValidation = AuthDomainValidator.validateSendOtpRequest(
            role = currentState.selectedRole,
            tenDigitMobile = currentState.phoneDigitsInput,
            driverAutoRegInput = currentState.driverAutoRegInput
        )
        if (detailsValidation is AuthFieldValidation.Invalid) {
            _phoneAuthState.update { it.copy(errorMessage = detailsValidation.message) }
            return
        }

        val fallbackPhone = AuthDomainValidator.normalizeIndianE164(currentState.phoneDigitsInput).orEmpty()

        viewModelScope.launch {
            _phoneAuthState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = "Verifying OTP with Firebase Authentication..."
                )
            }
            when (val verifyResult = gateway.verifyOtpCode(verificationId, currentState.otpCodeInput)) {
                is OtpVerificationResult.Verified -> {
                    cooldownJob?.cancel()
                    persistVerifiedProfile(
                        sessionUser = verifyResult.user,
                        fallbackE164Phone = fallbackPhone,
                        onSuccess = onSuccess
                    )
                }

                is OtpVerificationResult.Failure -> {
                    _phoneAuthState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = null,
                            errorMessage = verifyResult.message
                        )
                    }
                }
            }
        }
    }

    fun retrySaveAuthenticatedProfile(onSuccess: () -> Unit = {}) {
        val currentState = _phoneAuthState.value
        val session = currentState.signedInUser
        if (session == null) {
            _phoneAuthState.update { it.copy(errorMessage = "Sign in with Phone OTP first.") }
            return
        }
        val detailsValidation = if (currentState.selectedRole == AuthUserRole.DRIVER) {
            AuthDomainValidator.validateDriverAutoRegNumber(currentState.driverAutoRegInput)
        } else {
            AuthFieldValidation.Valid
        }
        if (detailsValidation is AuthFieldValidation.Invalid) {
            _phoneAuthState.update { it.copy(errorMessage = detailsValidation.message) }
            return
        }

        viewModelScope.launch {
            _phoneAuthState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    statusMessage = "Saving profile to Cloud Firestore..."
                )
            }
            persistVerifiedProfile(
                sessionUser = session,
                fallbackE164Phone = AuthDomainValidator.normalizeIndianE164(currentState.phoneDigitsInput).orEmpty(),
                onSuccess = onSuccess
            )
        }
    }

    private suspend fun persistVerifiedProfile(
        sessionUser: FirebaseAuthSessionUser,
        fallbackE164Phone: String,
        onSuccess: () -> Unit
    ) {
        val currentState = _phoneAuthState.value
        val verifiedPhone = AuthDomainValidator.normalizeIndianE164(sessionUser.phoneNumber)
            ?: AuthDomainValidator.normalizeIndianE164(fallbackE164Phone)
            ?: sessionUser.phoneNumber

        val regNumber = if (currentState.selectedRole == AuthUserRole.DRIVER) {
            AuthDomainValidator.normalizeAutoRegNumber(currentState.driverAutoRegInput)
        } else {
            null
        }

        val profile = AuthenticatedUserProfile(
            uid = sessionUser.uid,
            phoneNumber = verifiedPhone,
            role = currentState.selectedRole,
            driverAutoRegNumber = regNumber
        )

        val remote = userProfileRemoteDataSource
        if (remote == null || !remote.isFirestoreConfigured) {
            _phoneAuthState.update {
                it.copy(
                    step = PhoneAuthStep.AUTHENTICATED,
                    signedInUser = sessionUser,
                    isLoading = false,
                    statusMessage = "Phone OTP verified.",
                    errorMessage = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE
                )
            }
            return
        }

        when (val saveResult = remote.saveAuthenticatedProfile(profile)) {
            is FirestoreProfileResult.Success -> {
                val saved = saveResult.data
                userPreferencesRepo.updatePreferredMode(saved.role.toUserMode())
                _phoneAuthState.update {
                    it.copy(
                        step = PhoneAuthStep.AUTHENTICATED,
                        signedInUser = sessionUser,
                        authenticatedProfile = saved,
                        selectedRole = saved.role,
                        driverAutoRegInput = saved.driverAutoRegNumber.orEmpty(),
                        otpCodeInput = "",
                        verificationId = null,
                        isLoading = false,
                        resendCooldownSeconds = 0,
                        errorMessage = null,
                        statusMessage = "Phone verified & profile saved to Cloud Firestore."
                    )
                }
                onSuccess()
            }

            is FirestoreProfileResult.Failure -> {
                _phoneAuthState.update {
                    it.copy(
                        step = PhoneAuthStep.AUTHENTICATED,
                        signedInUser = sessionUser,
                        isLoading = false,
                        statusMessage = "Phone verified (${verifiedPhone}), but Firestore profile save failed.",
                        errorMessage = saveResult.message
                    )
                }
            }
        }
    }

    private suspend fun refreshAuthenticatedProfileFromFirestore(uid: String) {
        val remote = userProfileRemoteDataSource ?: return
        if (!remote.isFirestoreConfigured) return
        when (val fetchResult = remote.fetchAuthenticatedProfile(uid)) {
            is FirestoreProfileResult.Success -> {
                val profile = fetchResult.data
                if (profile != null) {
                    userPreferencesRepo.updatePreferredMode(profile.role.toUserMode())
                    _phoneAuthState.update { state ->
                        state.copy(
                            authenticatedProfile = profile,
                            selectedRole = profile.role,
                            driverAutoRegInput = profile.driverAutoRegNumber.orEmpty(),
                            errorMessage = null
                        )
                    }
                    attachFareNegotiationListener(uid)
                }
            }

            is FirestoreProfileResult.Failure -> {
                _phoneAuthState.update { state ->
                    state.copy(errorMessage = fetchResult.message)
                }
            }
        }
    }

    private fun startResendCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            for (remaining in 60 downTo 0) {
                _phoneAuthState.update { it.copy(resendCooldownSeconds = remaining) }
                if (remaining > 0) delay(1_000L)
            }
        }
    }

    fun signOutPhoneAuth() {
        cooldownJob?.cancel()
        lastResendToken = null
        phoneAuthGateway?.signOut()
        _phoneAuthState.update { state ->
            PhoneAuthUiState(
                step = PhoneAuthStep.ENTER_DETAILS,
                selectedRole = state.selectedRole,
                isFirebaseConfigured = phoneAuthGateway?.isFirebaseConfigured == true
            )
        }
    }

    fun toggleFavorite(routeKey: String) {
        viewModelScope.launch {
            favoriteRoutesRepo.toggleFavoritePersist(routeKey)
        }
    }

    fun updateOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepo.updateOnboardingCompleted(completed)
        }
    }

    fun updatePreferredMode(mode: UserMode) {
        viewModelScope.launch {
            userPreferencesRepo.updatePreferredMode(mode)
        }
    }

    fun updateThemePreference(theme: ThemePreference) {
        viewModelScope.launch {
            userPreferencesRepo.updateThemePreference(theme)
        }
    }

    fun resetAllLocalData() {
        viewModelScope.launch {
            favoriteRoutesRepo.clearAllFavoritesPersist()
            bookingsRepo.clearAllBookings()
            driverRoutesRepo.clearAllRoutes()
            userPreferencesRepo.resetPreferences()
        }
    }

    fun saveLocalDriverRoute(
        entity: DriverRouteEntity,
        onResult: (PersistenceResult<DriverRouteEntity>) -> Unit = {}
    ) {
        viewModelScope.launch {
            onResult(driverRoutesRepo.saveLocalRoutePosting(entity))
        }
    }

    fun registerLocalBooking(
        agreement: BookingAgreement,
        onResult: (PersistenceResult<BookingAgreement>) -> Unit = {}
    ) {
        viewModelScope.launch {
            onResult(bookingsRepo.registerNewBooking(agreement))
        }
    }

    fun advanceBookingState(
        bookingId: String,
        nextState: BookingLifecycleState,
        reason: String? = null,
        onResult: (PersistenceResult<BookingAgreement>) -> Unit = {}
    ) {
        viewModelScope.launch {
            onResult(bookingsRepo.transitionBooking(bookingId, nextState, reason))
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            notificationsRepo.markRead(id)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            notificationsRepo.markAllRead()
        }
    }

    private fun attachFareNegotiationListener(uid: String) {
        val repository = fareNegotiationRepository ?: return
        fareSessionsObserverJob?.cancel()
        fareSessionsObserverJob = viewModelScope.launch {
            val role = _phoneAuthState.value.authenticatedProfile?.role
            val stream = if (role == AuthUserRole.DRIVER) repository.observeDriverSessions(uid)
                else repository.observePassengerSessions(uid)
            stream.collect { sessions ->
                _fareNegotiationState.update { it.copy(sessions = sessions, error = null) }
            }
        }
    }

    fun createPassengerFareOffer(
        routeId: String,
        passengerCount: Int,
        pickupStand: String,
        destination: String,
        totalGroupFareInr: Double
    ) {
        val repository = fareNegotiationRepository
        val auth = _phoneAuthState.value
        if (repository == null || auth.authenticatedProfile?.role != AuthUserRole.PASSENGER) {
            _fareNegotiationState.update { it.copy(error = "Passenger sign-in and Firebase configuration are required.") }
            return
        }
        viewModelScope.launch {
            _fareNegotiationState.update { it.copy(isLoading = true, error = null, message = null) }
            repository.createPassengerOffer(routeId, passengerCount, pickupStand, destination, totalGroupFareInr)
                .onSuccess { session ->
                    _fareNegotiationState.update { it.copy(isLoading = false, activeSession = session, message = "Total group fare offer sent to driver.") }
                    observeFareNegotiation(session.bookingId)
                }.onFailure { error ->
                    _fareNegotiationState.update { it.copy(isLoading = false, error = error.message ?: "Could not send fare offer.") }
                }
        }
    }

    fun observeFareNegotiation(bookingId: String) {
        val repository = fareNegotiationRepository ?: return
        activeFareSessionObserverJob?.cancel()
        activeFareSessionObserverJob = viewModelScope.launch {
            repository.observeSession(bookingId).collect { session ->
                _fareNegotiationState.update { it.copy(activeSession = session) }
            }
        }
    }

    private fun runFareAction(action: suspend (FareNegotiationFirestoreRepository) -> Result<FareNegotiationSession>) {
        val repository = fareNegotiationRepository ?: run {
            _fareNegotiationState.update { it.copy(error = "Fare negotiation service is not configured.") }
            return
        }
        viewModelScope.launch {
            _fareNegotiationState.update { it.copy(isLoading = true, error = null, message = null) }
            action(repository).onSuccess { session ->
                _fareNegotiationState.update { it.copy(isLoading = false, activeSession = session, message = "Fare negotiation updated.") }
            }.onFailure { error ->
                _fareNegotiationState.update { it.copy(isLoading = false, error = error.message ?: "Fare action failed.") }
            }
        }
    }

    fun driverAcceptFare(bookingId: String) = runFareAction { it.driverAccept(bookingId) }
    fun driverCounterFare(bookingId: String, totalGroupFareInr: Double) = runFareAction { it.driverCounter(bookingId, totalGroupFareInr) }
    fun driverDeclineFare(bookingId: String) = runFareAction { it.driverDecline(bookingId) }
    fun passengerAcceptCounter(bookingId: String) = runFareAction { it.passengerAcceptCounter(bookingId) }
    fun passengerDeclineCounter(bookingId: String) = runFareAction { it.passengerDeclineCounter(bookingId) }
    fun cancelFareNegotiation(bookingId: String) = runFareAction { it.cancel(bookingId) }

    fun publishLiveRouteToFirestore(
        pickupStation: String,
        pickupSide: StationSide?,
        designatedStand: String,
        destination: String,
        viaPoints: List<String>,
        totalBookingFare: Double,
        availableSeats: Int,
        onSuccess: (LiveAutoRoute) -> Unit = {}
    ) {
        val gateway = liveRoutesGateway
        if (gateway == null || !gateway.isFirestoreConfigured) {
            _liveRouteActionState.update {
                it.copy(lastActionError = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE, lastActionMessage = null)
            }
            return
        }
        val currentAuth = _phoneAuthState.value
        val signedInUid = currentAuth.signedInUser?.uid
        if (signedInUid.isNullOrBlank()) {
            _liveRouteActionState.update {
                it.copy(
                    lastActionError = "Sign in with +91 Mobile OTP (Driver role) to publish a live route.",
                    lastActionMessage = null
                )
            }
            return
        }
        if (currentAuth.authenticatedProfile?.role != AuthUserRole.DRIVER) {
            _liveRouteActionState.update {
                it.copy(
                    lastActionError = "A saved Driver profile is required before publishing a live route. Sign in and complete Driver profile setup.",
                    lastActionMessage = null
                )
            }
            return
        }

        val command = PublishRouteCommand(
            driverUid = signedInUid,
            driverAutoRegNumber = currentAuth.authenticatedProfile?.driverAutoRegNumber
                ?: currentAuth.driverAutoRegInput.takeIf { it.isNotBlank() },
            pickupStation = pickupStation,
            pickupSide = pickupSide,
            designatedStand = designatedStand,
            destination = destination,
            viaPoints = viaPoints,
            totalBookingFare = totalBookingFare,
            availableSeats = availableSeats
        )

        val preCheck = LiveRouteTransactionEngine.validatePublishCommand(command)
        if (preCheck is FirestoreActionResult.Failure) {
            _liveRouteActionState.update {
                it.copy(lastActionError = preCheck.message, lastActionMessage = null)
            }
            return
        }

        viewModelScope.launch {
            _liveRouteActionState.update {
                it.copy(isPublishingRoute = true, lastActionError = null, lastActionMessage = null)
            }
            when (val result = gateway.publishActiveRoute(command)) {
                is FirestoreActionResult.Success -> {
                    _liveRouteActionState.update {
                        it.copy(
                            isPublishingRoute = false,
                            lastActionError = null,
                            lastActionMessage = "Live route published to Cloud Firestore (${result.data.availableSeats} seat(s) available)."
                        )
                    }
                    observeRouteQueue(result.data.postingId)
                    onSuccess(result.data)
                }
                is FirestoreActionResult.Failure -> {
                    _liveRouteActionState.update {
                        it.copy(
                            isPublishingRoute = false,
                            lastActionError = result.message,
                            lastActionMessage = null
                        )
                    }
                }
            }
        }
    }

    fun lockSeatOnLiveRoute(
        postingId: String,
        seatsRequested: Int,
        agreedTotalFare: Double? = null,
        onSuccess: (RouteSeatLock) -> Unit = {}
    ) {
        val gateway = liveRoutesGateway
        if (gateway == null || !gateway.isFirestoreConfigured) {
            _liveRouteActionState.update {
                it.copy(lastActionError = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE, lastActionMessage = null)
            }
            return
        }
        val authState = _phoneAuthState.value
        val passengerUid = authState.signedInUser?.uid
        if (passengerUid.isNullOrBlank()) {
            _liveRouteActionState.update {
                it.copy(lastActionError = "Sign in with +91 Mobile OTP to lock a seat.", lastActionMessage = null)
            }
            return
        }
        if (authState.authenticatedProfile?.role != AuthUserRole.PASSENGER) {
            _liveRouteActionState.update {
                it.copy(
                    lastActionError = "A saved Passenger profile is required before locking a seat.",
                    lastActionMessage = null
                )
            }
            return
        }

        viewModelScope.launch {
            _liveRouteActionState.update {
                it.copy(lockingPostingId = postingId, lastActionError = null, lastActionMessage = null)
            }
            when (val result = gateway.lockSeatTransaction(postingId, passengerUid, seatsRequested, agreedTotalFare)) {
                is FirestoreActionResult.Success -> {
                    _liveRouteActionState.update {
                        it.copy(
                            lockingPostingId = null,
                            lastSeatLock = result.data,
                            lastActionError = null,
                            lastActionMessage = "Seat locked atomically! Queue #${result.data.queuePosition} • ${result.data.seatsLocked} seat(s) reserved at ₹${result.data.agreedTotalFare.toInt()} total fare."
                        )
                    }
                    observeRouteQueue(postingId)
                    onSuccess(result.data)
                }
                is FirestoreActionResult.Failure -> {
                    _liveRouteActionState.update {
                        it.copy(
                            lockingPostingId = null,
                            lastActionError = result.message,
                            lastActionMessage = null
                        )
                    }
                }
            }
        }
    }

    fun releaseSeatOnLiveRoute(postingId: String) {
        val gateway = liveRoutesGateway ?: return
        val passengerUid = _phoneAuthState.value.signedInUser?.uid ?: return
        viewModelScope.launch {
            _liveRouteActionState.update {
                it.copy(releasingPostingId = postingId, lastActionError = null, lastActionMessage = null)
            }
            when (val result = gateway.releaseSeatTransaction(postingId, passengerUid)) {
                is FirestoreActionResult.Success -> {
                    _liveRouteActionState.update {
                        it.copy(
                            releasingPostingId = null,
                            lastActionMessage = "Seat lock released and seat returned to live stand inventory.",
                            lastActionError = null
                        )
                    }
                }
                is FirestoreActionResult.Failure -> {
                    _liveRouteActionState.update {
                        it.copy(
                            releasingPostingId = null,
                            lastActionError = result.message,
                            lastActionMessage = null
                        )
                    }
                }
            }
        }
    }

    fun deactivateDriverLiveRoute(postingId: String) {
        val gateway = liveRoutesGateway ?: return
        val driverUid = _phoneAuthState.value.signedInUser?.uid ?: return
        viewModelScope.launch {
            _liveRouteActionState.update {
                it.copy(deactivatingPostingId = postingId, lastActionError = null, lastActionMessage = null)
            }
            when (val result = gateway.deactivateDriverRoute(postingId, driverUid)) {
                is FirestoreActionResult.Success -> {
                    _liveRouteActionState.update {
                        it.copy(
                            deactivatingPostingId = null,
                            lastActionMessage = "Live route closed and removed from passenger search.",
                            lastActionError = null
                        )
                    }
                }
                is FirestoreActionResult.Failure -> {
                    _liveRouteActionState.update {
                        it.copy(
                            deactivatingPostingId = null,
                            lastActionError = result.message,
                            lastActionMessage = null
                        )
                    }
                }
            }
        }
    }

    fun observeRouteQueue(postingId: String?) {
        val cleanId = postingId?.trim()?.takeIf { it.isNotEmpty() }
        queueObserverJob?.cancel()
        if (cleanId == null || liveRoutesGateway == null || !liveRoutesGateway.isFirestoreConfigured) {
            _liveRouteActionState.update {
                it.copy(selectedQueuePostingId = null, selectedQueueLocks = emptyList())
            }
            return
        }
        _liveRouteActionState.update { it.copy(selectedQueuePostingId = cleanId) }
        queueObserverJob = viewModelScope.launch {
            liveRoutesGateway.observeRouteQueueLocks(cleanId).collect { locks ->
                _liveRouteActionState.update {
                    it.copy(selectedQueuePostingId = cleanId, selectedQueueLocks = locks)
                }
            }
        }
    }

    fun clearLiveRouteActionFeedback() {
        _liveRouteActionState.update {
            it.copy(lastActionMessage = null, lastActionError = null)
        }
    }

    fun dismissPersistenceError() {
        favoriteRoutesRepo.clearError()
        bookingsRepo.clearError()
        driverRoutesRepo.clearError()
        userPreferencesRepo.clearError()
        notificationsRepo.clearError()
    }

    override fun onCleared() {
        cooldownJob?.cancel()
        driverRoutesObserverJob?.cancel()
        queueObserverJob?.cancel()
        fareSessionsObserverJob?.cancel()
        activeFareSessionObserverJob?.cancel()
        phoneAuthGateway?.unregister()
        super.onCleared()
    }

    class Factory(
        private val context: Context,
        private val favoriteRouteRepository: FavoriteRouteRepository,
        private val phoneAuthGateway: PhoneAuthGateway? = null,
        private val userProfileRemoteDataSource: UserProfileRemoteDataSource? = null,
        private val liveRoutesGateway: LiveRoutesDataSource? = null,
        private val fareNegotiationRepository: FareNegotiationFirestoreRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val db = MumbaiShareAutoDatabase.getInstance(appContext)
            val txRunner = RoomTransactionRunner(db)
            val legacyBookingsPrefs = appContext.getSharedPreferences(
                com.mumbaishareauto.app.data.local.BookingAgreementRepository.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            return MumbaiShareAutoViewModel(
                favoriteRoutesRepo = favoriteRouteRepository,
                bookingsRepo = RoomBookingRepository(
                    bookingDao = db.bookingDao(),
                    userPreferencesDao = db.userPreferencesDao(),
                    transactionRunner = txRunner,
                    legacyPrefs = legacyBookingsPrefs
                ),
                driverRoutesRepo = RoomDriverRouteRepository(
                    driverRouteDao = db.driverRouteDao(),
                    transactionRunner = txRunner
                ),
                userPreferencesRepo = RoomUserPreferencesRepository(
                    userPreferencesDao = db.userPreferencesDao(),
                    transactionRunner = txRunner
                ),
                notificationsRepo = RoomNotificationsRepository(
                    notificationDao = db.notificationDao(),
                    transactionRunner = txRunner
                ),
                phoneAuthGateway = phoneAuthGateway ?: FirebasePhoneAuthGateway(appContext),
                userProfileRemoteDataSource = userProfileRemoteDataSource ?: FirestoreUserProfileRepository(appContext),
                liveRoutesGateway = liveRoutesGateway ?: FirebaseFirestoreRepository(appContext),
                fareNegotiationRepository = fareNegotiationRepository ?: runCatching {
                    if (FirebaseRuntimeInspector.isFirebaseInitialized(appContext)) {
                        FareNegotiationFirestoreRepository(
                            com.google.firebase.firestore.FirebaseFirestore.getInstance(),
                            com.google.firebase.auth.FirebaseAuth.getInstance()
                        )
                    } else null
                }.getOrNull()
            ) as T
        }
    }
}
