package com.mumbaishareauto.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.mumbaishareauto.app.data.MumbaiStationDirectory
import com.mumbaishareauto.app.data.local.AppPreferencesRepository
import com.mumbaishareauto.app.data.local.FavoriteRouteRepository
import com.mumbaishareauto.app.data.local.LocalRouteDraft
import com.mumbaishareauto.app.data.remote.AndroidNetworkConnectivityManager
import com.mumbaishareauto.app.data.remote.HttpUrlConnectionMumbaiShareAutoApiService
import com.mumbaishareauto.app.data.remote.LiveRoutesStreamState
import com.mumbaishareauto.app.notifications.MumbaiShareAutoNotificationManager
import com.mumbaishareauto.app.ui.LiveRouteActionState
import com.mumbaishareauto.app.ui.MumbaiShareAutoViewModel
import com.mumbaishareauto.app.ui.PhoneAuthUiState
import com.mumbaishareauto.app.ui.components.accessibleTouchTarget
import com.mumbaishareauto.app.ui.navigation.AppRoute
import com.mumbaishareauto.app.ui.screens.BookingDetailsScreen
import com.mumbaishareauto.app.ui.screens.BookingHistoryScreen
import com.mumbaishareauto.app.ui.screens.DriverModeScreen
import com.mumbaishareauto.app.ui.screens.DriverRoutePostingScreen
import com.mumbaishareauto.app.ui.screens.FavoritesScreen
import com.mumbaishareauto.app.ui.screens.FareNegotiationPanel
import com.mumbaishareauto.app.ui.screens.FindSharedAutoScreen
import com.mumbaishareauto.app.ui.screens.HelpAboutScreen
import com.mumbaishareauto.app.ui.screens.HomeDashboardScreen
import com.mumbaishareauto.app.ui.screens.LoginOtpScreen
import com.mumbaishareauto.app.ui.screens.NotificationsScreen
import com.mumbaishareauto.app.ui.screens.OnboardingScreen
import com.mumbaishareauto.app.ui.screens.PassengerBookingScreen
import com.mumbaishareauto.app.ui.screens.PassengerModeScreen
import com.mumbaishareauto.app.ui.screens.ProfileSettingsScreen
import com.mumbaishareauto.app.ui.screens.SplashScreen
import com.mumbaishareauto.app.ui.screens.StationDirectoryScreen
import com.mumbaishareauto.app.ui.theme.MumbaiShareAutoTheme

/**
 * Operating mode selection.
 */
enum class UserMode {
    PASSENGER,
    DRIVER
}

internal fun normalizeDeepLinkBookingId(rawBookingId: String?): String? =
    rawBookingId?.trim()?.takeIf { it.isNotEmpty() }

internal fun extractDeepLinkBookingId(intent: Intent?): String? =
    normalizeDeepLinkBookingId(
        intent?.getStringExtra(MumbaiShareAutoNotificationManager.EXTRA_DEEP_LINK_BOOKING_ID)
    )

class MainActivity : ComponentActivity() {

    private lateinit var favoriteRepo: FavoriteRouteRepository
    private lateinit var prefsRepo: AppPreferencesRepository
    private lateinit var viewModel: MumbaiShareAutoViewModel
    private lateinit var connectivityManager: AndroidNetworkConnectivityManager
    private val apiService by lazy { HttpUrlConnectionMumbaiShareAutoApiService() }
    private var pendingDeepLinkBookingId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MumbaiShareAutoNotificationManager.createNotificationChannels(applicationContext)
        connectivityManager = AndroidNetworkConnectivityManager(applicationContext)
        favoriteRepo = FavoriteRouteRepository(applicationContext)
        prefsRepo = AppPreferencesRepository(applicationContext)
        viewModel = ViewModelProvider(
            this,
            MumbaiShareAutoViewModel.Factory(applicationContext, favoriteRepo)
        )[MumbaiShareAutoViewModel::class.java]

        pendingDeepLinkBookingId = extractDeepLinkBookingId(intent)

        setContent {
            val themePref by prefsRepo.themePreference.collectAsState()
            val isNetworkOnline by connectivityManager.isConnected.collectAsState()
            val connectionType by connectivityManager.connectionType.collectAsState()

            MumbaiShareAutoTheme(themePreference = themePref) {
                MumbaiShareAutoApp(
                    favoriteRepo = favoriteRepo,
                    prefsRepo = prefsRepo,
                    viewModel = viewModel,
                    deepLinkBookingId = pendingDeepLinkBookingId,
                    onDeepLinkConsumed = { pendingDeepLinkBookingId = null },
                    isNetworkOnline = isNetworkOnline,
                    connectionType = connectionType,
                    isBackendConfigured = apiService.isBackendConfigured
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val deepLinkId = extractDeepLinkBookingId(intent)
        if (deepLinkId != null) {
            pendingDeepLinkBookingId = deepLinkId
        }
    }

    override fun onDestroy() {
        if (::connectivityManager.isInitialized) {
            connectivityManager.unregister()
        }
        super.onDestroy()
    }
}

private data class BottomNavTab(
    val route: AppRoute,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MumbaiShareAutoApp(
    favoriteRepo: FavoriteRouteRepository,
    prefsRepo: AppPreferencesRepository,
    viewModel: MumbaiShareAutoViewModel? = null,
    deepLinkBookingId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
    isNetworkOnline: Boolean = false,
    connectionType: String = "Offline",
    isBackendConfigured: Boolean = false
) {
    val backStack = remember { mutableStateListOf(AppRoute.SPLASH) }
    val currentRoute = backStack.lastOrNull() ?: AppRoute.HOME

    val onboardingCompleted by prefsRepo.onboardingCompleted.collectAsState()
    val preferredMode by prefsRepo.preferredMode.collectAsState()
    val favoriteKeys by favoriteRepo.favoriteKeys.collectAsState()
    val localDrafts by prefsRepo.localDrafts.collectAsState()

    val persistedNotifications = viewModel?.notifications?.collectAsState()?.value ?: emptyList()
    val unreadNotifications = viewModel?.unreadNotificationCount?.collectAsState()?.value ?: 0
    val persistedBookings = viewModel?.bookings?.collectAsState()?.value ?: emptyList()
    val persistedDriverRoutes = viewModel?.driverRoutes?.collectAsState()?.value ?: emptyList()
    val persistenceError = viewModel?.persistenceError?.collectAsState()?.value
    val phoneAuthState = viewModel?.phoneAuthState?.collectAsState()?.value ?: PhoneAuthUiState()
    val liveRoutesState = viewModel?.liveRoutesState?.collectAsState()?.value ?: LiveRoutesStreamState.NotConfigured()
    val driverLiveRoutes = viewModel?.driverLiveRoutes?.collectAsState()?.value ?: emptyList()
    val liveRouteActionState = viewModel?.liveRouteActionState?.collectAsState()?.value ?: LiveRouteActionState()
    val fareNegotiationState = viewModel?.fareNegotiationState?.collectAsState()?.value ?: com.mumbaishareauto.app.ui.FareNegotiationUiState()
    val effectiveFirebaseConfigured = phoneAuthState.isFirebaseConfigured
    val liveRoutesCount = (liveRoutesState as? LiveRoutesStreamState.Live)?.routes?.size ?: 0

    var preselectedStandId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDraftId by rememberSaveable { mutableStateOf<String?>(null) }

    val preselectedStand = remember(preselectedStandId) {
        MumbaiStationDirectory.allStationRoutes.firstOrNull { it.id == preselectedStandId }
    }
    val selectedDraft = remember(selectedDraftId, localDrafts) {
        localDrafts.firstOrNull { it.id == selectedDraftId } ?: localDrafts.firstOrNull()
    }

    fun navigateTo(route: AppRoute, clearToRoot: Boolean = false) {
        if (clearToRoot) {
            backStack.clear()
            backStack.add(route)
            return
        }
        if (route.isTopLevelTab) {
            backStack.clear()
            backStack.add(AppRoute.HOME)
            if (route != AppRoute.HOME) {
                backStack.add(route)
            }
        } else if (backStack.lastOrNull() != route) {
            backStack.add(route)
        }
    }

    fun navigateBack() {
        if (backStack.size > 1) {
            val popped = backStack.removeAt(backStack.lastIndex)
            if (popped == AppRoute.PASSENGER_BOOKING) {
                preselectedStandId = null
            }
        }
    }

    LaunchedEffect(deepLinkBookingId, currentRoute) {
        val bookingId = deepLinkBookingId?.trim()
        if (!bookingId.isNullOrEmpty() && currentRoute != AppRoute.SPLASH && currentRoute != AppRoute.ONBOARDING) {
            val matchingDraft = localDrafts.firstOrNull { it.id == bookingId }
            backStack.clear()
            backStack.add(AppRoute.HOME)
            if (matchingDraft != null) {
                selectedDraftId = matchingDraft.id
                backStack.add(AppRoute.BOOKING_DETAILS)
            } else {
                backStack.add(AppRoute.BOOKING_HISTORY)
            }
            onDeepLinkConsumed()
        }
    }

    BackHandler(enabled = backStack.size > 1 && currentRoute.showChrome) {
        navigateBack()
    }

    val bottomTabs = listOf(
        BottomNavTab(AppRoute.HOME, "Home", Icons.Default.Home),
        BottomNavTab(AppRoute.STATION_DIRECTORY, "Stands", Icons.Default.Train),
        BottomNavTab(AppRoute.BOOKING_HISTORY, "Bookings", Icons.Default.History),
        BottomNavTab(AppRoute.FAVORITES, "Favorites", Icons.Default.Bookmark),
        BottomNavTab(AppRoute.PROFILE_SETTINGS, "Settings", Icons.Default.Settings)
    )

    Scaffold(
        topBar = {
            if (currentRoute.showChrome) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = currentRoute.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() }
                            )
                            Text(
                                text = if (currentRoute == AppRoute.HOME) {
                                    "${if (preferredMode == UserMode.PASSENGER) "Passenger" else "Driver"} Mode • ${if (isBackendConfigured && isNetworkOnline) "Online ($connectionType)" else "Phase 1 Offline"}"
                                } else {
                                    currentRoute.subtitle
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        if (backStack.size > 1) {
                            IconButton(
                                onClick = { navigateBack() },
                                modifier = Modifier.accessibleTouchTarget(
                                    label = "Navigate back",
                                    role = Role.Button
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { navigateTo(AppRoute.NOTIFICATIONS) },
                            modifier = Modifier.accessibleTouchTarget(
                                label = "Open notifications and service status",
                                role = Role.Button
                            )
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotifications > 0) {
                                        Badge {
                                            Text(unreadNotifications.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Notifications, contentDescription = null)
                            }
                        }
                        IconButton(
                            onClick = { navigateTo(AppRoute.HELP_ABOUT) },
                            modifier = Modifier.accessibleTouchTarget(
                                label = "Open help and about",
                                role = Role.Button
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (currentRoute.showChrome) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTo(tab.route) },
                            icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                            label = { Text(tab.label, maxLines = 1) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (persistenceError != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = persistenceError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { viewModel?.dismissPersistenceError() },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
                AnimatedContent(
                    targetState = currentRoute,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(150))
                    },
                    label = "screen_transition"
                ) { route ->
                    when (route) {
                        AppRoute.SPLASH -> SplashScreen(
                            routeCount = MumbaiStationDirectory.allStationRoutes.size,
                            onSplashFinished = {
                                val nextRoute = when {
                                    !deepLinkBookingId.isNullOrBlank() && onboardingCompleted -> AppRoute.HOME
                                    onboardingCompleted -> AppRoute.HOME
                                    else -> AppRoute.ONBOARDING
                                }
                                navigateTo(nextRoute, clearToRoot = true)
                            }
                        )

                        AppRoute.ONBOARDING -> OnboardingScreen(
                            onCompleteOnboarding = {
                                prefsRepo.setOnboardingCompleted(true)
                                viewModel?.updateOnboardingCompleted(true)
                                navigateTo(AppRoute.HOME, clearToRoot = true)
                            }
                        )

                        AppRoute.HOME -> HomeDashboardScreen(
                            currentMode = preferredMode,
                            totalStandsCount = MumbaiStationDirectory.allStationRoutes.size,
                            favoriteCount = favoriteKeys.size,
                            localDraftCount = localDrafts.size,
                            onModeChange = {
                                prefsRepo.setPreferredMode(it)
                                viewModel?.updatePreferredMode(it)
                            },
                            onNavigate = { navigateTo(it) },
                            authenticatedProfile = phoneAuthState.authenticatedProfile,
                            isFirebaseConfigured = effectiveFirebaseConfigured,
                            liveActiveRoutesCount = liveRoutesCount
                        )

                        AppRoute.PASSENGER_MODE -> PassengerModeScreen(
                            favoriteCount = favoriteKeys.size,
                            localDraftCount = localDrafts.size,
                            onNavigate = { navigateTo(it) }
                        )

                        AppRoute.DRIVER_MODE -> DriverModeScreen(
                            localDrafts = localDrafts,
                            onNavigate = { navigateTo(it) },
                            onInspectDraft = { draft: LocalRouteDraft ->
                                selectedDraftId = draft.id
                                navigateTo(AppRoute.BOOKING_DETAILS)
                            },
                            isFirebaseConfigured = effectiveFirebaseConfigured,
                            driverLiveRoutes = driverLiveRoutes,
                            liveRouteActionState = liveRouteActionState,
                            onDeactivateLiveRoute = { postingId ->
                                viewModel?.deactivateDriverLiveRoute(postingId)
                            },
                            onSelectRouteForQueue = { postingId ->
                                viewModel?.observeRouteQueue(postingId)
                            }
                        )

                        AppRoute.FIND_SHARED_AUTO -> FindSharedAutoScreen(
                            favoriteRepo = favoriteRepo,
                            onSelectStandForOffer = { stand ->
                                preselectedStandId = stand.id
                                navigateTo(AppRoute.PASSENGER_BOOKING)
                            },
                            liveRoutesState = liveRoutesState,
                            liveRouteActionState = liveRouteActionState,
                            currentAuthUid = phoneAuthState.signedInUser?.uid,
                            onLockSeat = { postingId, seats ->
                                viewModel?.lockSeatOnLiveRoute(postingId, seats)
                            },
                            onReleaseSeat = { postingId ->
                                viewModel?.releaseSeatOnLiveRoute(postingId)
                            },
                            onSelectRouteForQueue = { postingId ->
                                viewModel?.observeRouteQueue(postingId)
                            },
                            onOpenPhoneAuth = { navigateTo(AppRoute.PHONE_AUTH) }
                        )

                        AppRoute.STATION_DIRECTORY -> StationDirectoryScreen(
                            favoriteRepo = favoriteRepo,
                            onSelectStandForOffer = { stand ->
                                preselectedStandId = stand.id
                                navigateTo(AppRoute.PASSENGER_BOOKING)
                            }
                        )

                        AppRoute.PASSENGER_BOOKING -> PassengerBookingScreen(
                            initialStand = preselectedStand,
                            prefsRepo = prefsRepo,
                            onInspectDraft = { draft: LocalRouteDraft ->
                                selectedDraftId = draft.id
                                navigateTo(AppRoute.BOOKING_DETAILS)
                            },
                            onBrowseStands = { navigateTo(AppRoute.FIND_SHARED_AUTO) },
                            liveRoutesState = liveRoutesState,
                            liveRouteActionState = liveRouteActionState,
                            currentAuthUid = phoneAuthState.signedInUser?.uid,
                            onLockSeat = { postingId, seats ->
                                viewModel?.lockSeatOnLiveRoute(postingId, seats)
                            },
                            onReleaseSeat = { postingId ->
                                viewModel?.releaseSeatOnLiveRoute(postingId)
                            },
                            onSelectRouteForQueue = { postingId ->
                                viewModel?.observeRouteQueue(postingId)
                            },
                            onOpenPhoneAuth = { navigateTo(AppRoute.PHONE_AUTH) }
                        )

                        AppRoute.DRIVER_ROUTE_POSTING -> DriverRoutePostingScreen(
                            prefsRepo = prefsRepo,
                            onInspectDraft = { draft: LocalRouteDraft ->
                                selectedDraftId = draft.id
                                navigateTo(AppRoute.BOOKING_DETAILS)
                            },
                            isFirebaseConfigured = effectiveFirebaseConfigured,
                            signedInDriverUid = phoneAuthState.signedInUser?.uid,
                            driverAutoRegNumber = phoneAuthState.authenticatedProfile?.driverAutoRegNumber
                                ?: phoneAuthState.driverAutoRegInput.takeIf { it.isNotBlank() },
                            liveRouteActionState = liveRouteActionState,
                            onPublishLiveRoute = { command ->
                                viewModel?.publishLiveRouteToFirestore(
                                    pickupStation = command.pickupStation,
                                    pickupSide = command.pickupSide,
                                    designatedStand = command.designatedStand,
                                    destination = command.destination,
                                    viaPoints = command.viaPoints,
                                    totalBookingFare = command.totalBookingFare,
                                    availableSeats = command.availableSeats
                                )
                            },
                            onOpenPhoneAuth = { navigateTo(AppRoute.PHONE_AUTH) }
                        )

                        AppRoute.BOOKING_DETAILS -> BookingDetailsScreen(
                            draft = selectedDraft,
                            prefsRepo = prefsRepo,
                            onDraftDeleted = {
                                selectedDraftId = null
                                navigateBack()
                            },
                            onCreateOffer = { navigateTo(AppRoute.PASSENGER_BOOKING) }
                        )

                        AppRoute.BOOKING_HISTORY -> BookingHistoryScreen(
                            prefsRepo = prefsRepo,
                            onSelectDraft = { draft ->
                                selectedDraftId = draft.id
                                navigateTo(AppRoute.BOOKING_DETAILS)
                            },
                            onStartOffer = { navigateTo(AppRoute.PASSENGER_BOOKING) }
                        )

                        AppRoute.FAVORITES -> FavoritesScreen(
                            favoriteRepo = favoriteRepo,
                            onSelectStandForOffer = { stand ->
                                preselectedStandId = stand.id
                                navigateTo(AppRoute.PASSENGER_BOOKING)
                            },
                            onExploreDirectory = { navigateTo(AppRoute.STATION_DIRECTORY) }
                        )

                        AppRoute.NOTIFICATIONS -> NotificationsScreen(
                            favoriteCount = favoriteKeys.size,
                            draftCount = localDrafts.size,
                            onNavigate = { navigateTo(it) },
                            persistedNotifications = persistedNotifications,
                            persistedBookingsCount = persistedBookings.size,
                            persistedDriverRoutesCount = persistedDriverRoutes.size,
                            isNetworkOnline = isNetworkOnline,
                            connectionType = connectionType,
                            isBackendConfigured = isBackendConfigured,
                            onMarkRead = { id -> viewModel?.markNotificationRead(id) },
                            onMarkAllRead = { viewModel?.markAllNotificationsRead() }
                        )

                        AppRoute.PROFILE_SETTINGS -> ProfileSettingsScreen(
                            prefsRepo = prefsRepo,
                            favoriteRepo = favoriteRepo,
                            onReplayOnboarding = {
                                prefsRepo.setOnboardingCompleted(false)
                                viewModel?.updateOnboardingCompleted(false)
                                navigateTo(AppRoute.ONBOARDING, clearToRoot = true)
                            },
                            onOpenHelp = { navigateTo(AppRoute.HELP_ABOUT) },
                            onOpenNotifications = { navigateTo(AppRoute.NOTIFICATIONS) },
                            isNetworkOnline = isNetworkOnline,
                            connectionType = connectionType,
                            isBackendConfigured = isBackendConfigured,
                            onModeChanged = { viewModel?.updatePreferredMode(it) },
                            onThemeChanged = { viewModel?.updateThemePreference(it) },
                            onResetRoomData = { viewModel?.resetAllLocalData() },
                            signedInUser = phoneAuthState.signedInUser,
                            authenticatedProfile = phoneAuthState.authenticatedProfile,
                            onOpenPhoneAuth = { navigateTo(AppRoute.PHONE_AUTH) },
                            onSignOutPhoneAuth = { viewModel?.signOutPhoneAuth() }
                        )

                        AppRoute.PHONE_AUTH -> LoginOtpScreen(
                            authState = phoneAuthState,
                            onSelectRole = { role -> viewModel?.selectAuthRole(role) },
                            onPhoneDigitsChange = { digits -> viewModel?.updatePhoneDigitsInput(digits) },
                            onDriverAutoRegChange = { reg -> viewModel?.updateDriverAutoRegInput(reg) },
                            onOtpCodeChange = { otp -> viewModel?.updateOtpCodeInput(otp) },
                            onSendOtp = { activity ->
                                viewModel?.sendOtp(
                                    activity = activity,
                                    isResend = false,
                                    onAutoSignedIn = {
                                        prefsRepo.setPreferredMode(
                                            viewModel.phoneAuthState.value.selectedRole.toUserMode()
                                        )
                                    }
                                )
                            },
                            onResendOtp = { activity ->
                                viewModel?.sendOtp(activity = activity, isResend = true)
                            },
                            onVerifyOtp = {
                                viewModel?.verifyOtpAndSaveProfile(
                                    onSuccess = {
                                        prefsRepo.setPreferredMode(
                                            viewModel.phoneAuthState.value.selectedRole.toUserMode()
                                        )
                                    }
                                )
                            },
                            onChangePhoneNumber = { viewModel?.changePhoneNumber() },
                            onRetrySaveProfile = {
                                viewModel?.retrySaveAuthenticatedProfile(
                                    onSuccess = {
                                        prefsRepo.setPreferredMode(
                                            viewModel.phoneAuthState.value.selectedRole.toUserMode()
                                        )
                                    }
                                )
                            },
                            onSignOut = { viewModel?.signOutPhoneAuth() },
                            onContinueToHome = { navigateTo(AppRoute.HOME) }
                        )

                        AppRoute.HELP_ABOUT -> HelpAboutScreen()
                    }
                }
                if (viewModel != null && currentRoute in setOf(
                        AppRoute.PASSENGER_BOOKING,
                        AppRoute.FIND_SHARED_AUTO,
                        AppRoute.DRIVER_MODE,
                        AppRoute.DRIVER_ROUTE_POSTING
                    )) {
                    FareNegotiationPanel(
                        viewModel = viewModel,
                        authState = phoneAuthState,
                        state = fareNegotiationState,
                        availableRoutes = (liveRoutesState as? LiveRoutesStreamState.Live)?.routes.orEmpty()
                    )
                }
            }
        }
    }
}
