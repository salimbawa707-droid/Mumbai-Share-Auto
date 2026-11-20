package com.mumbaishareauto.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mumbaishareauto.app.BookingAgreement
import com.mumbaishareauto.app.DriverDashboardTab
import com.mumbaishareauto.app.DriverRequestsFeedResult
import com.mumbaishareauto.app.DriverWorkflowCoordinator
import com.mumbaishareauto.app.OfflineDriverBookingDataSource
import com.mumbaishareauto.app.UserMode
import com.mumbaishareauto.app.auth.AuthDomainValidator
import com.mumbaishareauto.app.auth.AuthUserRole
import com.mumbaishareauto.app.auth.AuthenticatedUserProfile
import com.mumbaishareauto.app.data.local.LocalRouteDraft
import com.mumbaishareauto.app.data.remote.LiveAutoRoute
import com.mumbaishareauto.app.ui.LiveRouteActionState
import com.mumbaishareauto.app.ui.components.BadgeTone
import com.mumbaishareauto.app.ui.components.EmptyStateCard
import com.mumbaishareauto.app.ui.components.LockedRulesNoticeCard
import com.mumbaishareauto.app.ui.components.ResponsiveContentColumn
import com.mumbaishareauto.app.ui.components.SectionHeader
import com.mumbaishareauto.app.ui.components.StatusPill
import com.mumbaishareauto.app.ui.components.UnavailableServiceBanner
import com.mumbaishareauto.app.ui.navigation.AppRoute

/**
 * Screen 3: Home Dashboard
 */
@Composable
fun HomeDashboardScreen(
    currentMode: UserMode,
    totalStandsCount: Int,
    favoriteCount: Int,
    localDraftCount: Int,
    onModeChange: (UserMode) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    authenticatedProfile: AuthenticatedUserProfile? = null,
    isFirebaseConfigured: Boolean = false,
    liveActiveRoutesCount: Int = 0
) {
    ResponsiveContentColumn(modifier = Modifier.fillMaxSize()) { isWide ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ola / Uber / Rapido-Style Hero "Where are you going?" Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (currentMode == UserMode.PASSENGER) {
                                "Commuter · Offline Reference"
                            } else {
                                "Driver · Offline Reference"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
                        )
                        Text(
                            text = "$totalStandsCount Mumbai Stands",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    Text(
                        text = if (currentMode == UserMode.PASSENGER) {
                            "Where are you going?"
                        } else {
                            "Share your stand route"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )

                    // 1-Tap Search Pill Button ("Where to?" Rapido/Uber style)
                    Button(
                        onClick = {
                            onNavigate(
                                if (currentMode == UserMode.PASSENGER) {
                                    AppRoute.FIND_SHARED_AUTO
                                } else {
                                    AppRoute.DRIVER_ROUTE_POSTING
                                }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (currentMode == UserMode.PASSENGER) {
                                Icons.Default.Search
                            } else {
                                Icons.Default.DirectionsCar
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = if (currentMode == UserMode.PASSENGER) {
                                "Find Shared Auto"
                            } else {
                                "Post My Route"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Clean 2-Way Role Switcher
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeChipButton(
                            label = "Passenger Mode",
                            icon = Icons.Default.Person,
                            isActive = currentMode == UserMode.PASSENGER,
                            onClick = { onModeChange(UserMode.PASSENGER) },
                            modifier = Modifier.weight(1f)
                        )
                        ModeChipButton(
                            label = "Driver Mode",
                            icon = Icons.Default.DirectionsCar,
                            isActive = currentMode == UserMode.DRIVER,
                            onClick = { onModeChange(UserMode.DRIVER) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Secondary Route Proposal / Passenger Search Action
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProminentActionCard(
                        title = "Offer My Route",
                        subtitle = "Send your party route offer (1–3 commuters, total booking fare)",
                        icon = Icons.Default.Route,
                        accentTone = BadgeTone.AMBER,
                        onClick = { onNavigate(AppRoute.PASSENGER_BOOKING) },
                        modifier = Modifier.weight(1f)
                    )
                    ProminentActionCard(
                        title = if (currentMode == UserMode.PASSENGER) "Open Full Passenger Workspace" else "Open Full Driver Workspace",
                        subtitle = "Inspect saved drafts, fare lock status & offline guidelines",
                        icon = Icons.Default.DirectionsCar,
                        accentTone = BadgeTone.NAVY,
                        onClick = {
                            onNavigate(
                                if (currentMode == UserMode.PASSENGER) AppRoute.PASSENGER_MODE
                                else AppRoute.DRIVER_MODE
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                ProminentActionCard(
                    title = "Offer My Route",
                    subtitle = "Validate party route offer (1–3 seats · fixed party fare)",
                    icon = Icons.Default.Route,
                    accentTone = BadgeTone.AMBER,
                    onClick = { onNavigate(AppRoute.PASSENGER_BOOKING) }
                )
            }

            // Compact 2x2 Quick Access Shortcuts
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickAccessTile(
                        title = "Nearby Station Reference",
                        metric = "$totalStandsCount Stands",
                        icon = Icons.Default.Train,
                        onClick = { onNavigate(AppRoute.STATION_DIRECTORY) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickAccessTile(
                        title = "Favorite Routes",
                        metric = "$favoriteCount Saved",
                        icon = Icons.Default.Bookmark,
                        onClick = { onNavigate(AppRoute.FAVORITES) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickAccessTile(
                        title = "My Bookings",
                        metric = "$localDraftCount Drafts",
                        icon = Icons.Default.History,
                        onClick = { onNavigate(AppRoute.BOOKING_HISTORY) },
                        modifier = Modifier.weight(1f)
                    )
                    QuickAccessTile(
                        title = "Driver Dashboard",
                        metric = "Post & Validate",
                        icon = Icons.Default.DirectionsCar,
                        onClick = { onNavigate(AppRoute.DRIVER_MODE) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            ProminentActionCard(
                title = if (authenticatedProfile != null) {
                    "Verified: ${authenticatedProfile.phoneNumber}"
                } else {
                    "Sign In with +91 Mobile OTP"
                },
                subtitle = if (authenticatedProfile != null) {
                    buildString {
                        append("Role: ${authenticatedProfile.role.label}")
                        if (authenticatedProfile.role == AuthUserRole.DRIVER && authenticatedProfile.driverAutoRegNumber != null) {
                            append(" • Auto: ${AuthDomainValidator.formatAutoRegForDisplay(authenticatedProfile.driverAutoRegNumber)}")
                        }
                    }
                } else {
                    "Verify your 10-digit Indian mobile number & select Passenger or Driver"
                },
                icon = Icons.Default.Person,
                accentTone = if (authenticatedProfile != null) BadgeTone.NAVY else BadgeTone.AMBER,
                onClick = { onNavigate(AppRoute.PHONE_AUTH) }
            )

            if (!isFirebaseConfigured) {
                UnavailableServiceBanner(
                    title = "Live Driver Matching Offline",
                    message = "Real-time driver sync connects in Phase 2. Station directory, favorites & BookingDomain validation are active locally."
                )
            } else {
                ProminentActionCard(
                    title = "Live Firestore Sync Active ($liveActiveRoutesCount route(s))",
                    subtitle = "Tap Find Shared Auto for live seats, real-time queues & instant seat locking",
                    icon = Icons.Default.DirectionsCar,
                    accentTone = BadgeTone.EMERALD,
                    onClick = { onNavigate(AppRoute.FIND_SHARED_AUTO) }
                )
            }

            LockedRulesNoticeCard()
        }
    }
}

/**
 * Screen 4: Passenger Mode
 */
@Composable
fun PassengerModeScreen(
    favoriteCount: Int,
    localDraftCount: Int,
    onNavigate: (AppRoute) -> Unit
) {
    ResponsiveContentColumn(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = "Passenger Mode Workspace",
                subtitle = "One booking = one passenger party. Drivers quote total booking fare."
            )

            ProminentActionCard(
                title = "Find Shared Auto Stand",
                subtitle = "Search Western, Central & Harbour railway station pickup stands",
                icon = Icons.Default.Search,
                accentTone = BadgeTone.NAVY,
                onClick = { onNavigate(AppRoute.FIND_SHARED_AUTO) }
            )

            ProminentActionCard(
                title = "Offer My Route",
                subtitle = "Create & validate a passenger party route offer via BookingDomain",
                icon = Icons.Default.Route,
                accentTone = BadgeTone.AMBER,
                onClick = { onNavigate(AppRoute.PASSENGER_BOOKING) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAccessTile(
                    title = "Saved Favorites",
                    metric = "$favoriteCount Bookmarked",
                    icon = Icons.Default.Bookmark,
                    onClick = { onNavigate(AppRoute.FAVORITES) },
                    modifier = Modifier.weight(1f)
                )
                QuickAccessTile(
                    title = "My Bookings",
                    metric = "$localDraftCount Local Drafts",
                    icon = Icons.Default.History,
                    onClick = { onNavigate(AppRoute.BOOKING_HISTORY) },
                    modifier = Modifier.weight(1f)
                )
            }

            UnavailableServiceBanner(
                title = "Live Driver Matching Not Connected",
                message = "Passenger party offers are validated locally via BookingDomain. Automated party merging is permanently disabled in V1."
            )

            LockedRulesNoticeCard()
        }
    }
}

/**
 * Screen 5: Driver Mode (Phase 3 Driver Dashboard)
 * Provides:
 * - Post My Route
 * - My Active Route
 * - Booking Requests
 * - Accepted Bookings
 * - Completed Bookings
 * - Cancelled Bookings
 */
@Composable
fun DriverModeScreen(
    localDrafts: List<LocalRouteDraft>,
    onNavigate: (AppRoute) -> Unit,
    onInspectDraft: (LocalRouteDraft) -> Unit,
    isFirebaseConfigured: Boolean = false,
    driverLiveRoutes: List<LiveAutoRoute> = emptyList(),
    liveRouteActionState: LiveRouteActionState = LiveRouteActionState(),
    onDeactivateLiveRoute: (String) -> Unit = {},
    onSelectRouteForQueue: (String?) -> Unit = {}
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = DriverDashboardTab.entries[selectedTabIndex.coerceIn(0, DriverDashboardTab.entries.lastIndex)]
    val driverFeed = remember { OfflineDriverBookingDataSource.fetchDriverBookings() }
    val driverDrafts = remember(localDrafts) {
        localDrafts.filter { it.roleLabel.contains("Driver", ignoreCase = true) }
    }

    ResponsiveContentColumn(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = "Driver Dashboard",
                subtitle = "Post one total booking fare — never calculated per seat"
            )

            ProminentActionCard(
                title = "Post My Route",
                subtitle = "Station, East/West, designated stand, destination, via points & total fare",
                icon = Icons.Default.DirectionsCar,
                accentTone = BadgeTone.AMBER,
                onClick = { onNavigate(AppRoute.DRIVER_ROUTE_POSTING) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAccessTile(
                    title = "Stand Directory",
                    metric = "Reference Map",
                    icon = Icons.Default.Train,
                    onClick = { onNavigate(AppRoute.STATION_DIRECTORY) },
                    modifier = Modifier.weight(1f)
                )
                QuickAccessTile(
                    title = "Local Drafts",
                    metric = "${driverDrafts.size} Driver Drafts",
                    icon = Icons.Default.History,
                    onClick = { onNavigate(AppRoute.BOOKING_HISTORY) },
                    modifier = Modifier.weight(1f)
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(DriverDashboardTab.entries) { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTabIndex = tab.ordinal },
                        label = { Text(tab.label) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }

            when (selectedTab) {
                DriverDashboardTab.MY_ACTIVE_ROUTE -> {
                    SectionHeader(
                        title = "My Active Route",
                        subtitle = if (isFirebaseConfigured) {
                            "Real-time routes broadcast to Cloud Firestore & passenger queue updates"
                        } else {
                            "Live broadcasting requires backend sync; local evaluation drafts appear below"
                        }
                    )
                    if (driverLiveRoutes.isNotEmpty()) {
                        driverLiveRoutes.forEach { liveRoute ->
                            val isSelectedQueue = liveRouteActionState.selectedQueuePostingId == liveRoute.postingId
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StatusPill(
                                            text = if (liveRoute.isActive) "LIVE ON FIRESTORE" else "CLOSED",
                                            tone = if (liveRoute.isActive) BadgeTone.EMERALD else BadgeTone.NEUTRAL
                                        )
                                        StatusPill(
                                            text = "${liveRoute.availableSeats}/${liveRoute.totalCapacitySeats} Seats Left",
                                            tone = if (liveRoute.availableSeats > 0) BadgeTone.NAVY else BadgeTone.RED
                                        )
                                    }
                                    Text(
                                        text = "${liveRoute.pickupStation} (${liveRoute.pickupSide.label}): ${liveRoute.designatedStand} → ${liveRoute.destination}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Flat Booking Fare: ₹${liveRoute.totalBookingFare.toInt()} • Live Queue Locks: ${liveRoute.activeQueueCount}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                onSelectRouteForQueue(if (isSelectedQueue) null else liveRoute.postingId)
                                            },
                                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                                        ) {
                                            Text(if (isSelectedQueue) "Hide Queue" else "Live Queue (${liveRoute.activeQueueCount})")
                                        }
                                        if (liveRoute.isActive) {
                                            OutlinedButton(
                                                onClick = { onDeactivateLiveRoute(liveRoute.postingId) },
                                                enabled = liveRouteActionState.deactivatingPostingId != liveRoute.postingId,
                                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                            ) {
                                                Text("End Route", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                    if (isSelectedQueue) {
                                        HorizontalDivider()
                                        if (liveRouteActionState.selectedQueueLocks.isEmpty()) {
                                            Text(
                                                text = "No passengers have locked a seat yet.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            liveRouteActionState.selectedQueueLocks.forEach { lock ->
                                                Text(
                                                    text = "• Queue #${lock.queuePosition}: ${lock.seatsLocked} seat(s) — ${lock.status}",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (driverDrafts.isEmpty() && driverLiveRoutes.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Default.Route,
                            title = "No Active Driver Route",
                            description = "Live route broadcasting is unavailable while offline. Use Post My Route to validate your stand, destination, capacity, and total booking fare.",
                            actionLabel = "Post My Route",
                            onAction = { onNavigate(AppRoute.DRIVER_ROUTE_POSTING) }
                        )
                    } else {
                        driverDrafts.forEach { draft ->
                            val farePreview = draft.totalFareInr?.let { fare ->
                                DriverWorkflowCoordinator.formatDriverTotalFarePreview(
                                    totalBookingFare = fare,
                                    availableCapacity = draft.partyOrSeatCount.coerceAtLeast(1)
                                )
                            }
                            Card(
                                onClick = { onInspectDraft(draft) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StatusPill(text = "Local Draft • Not Live", tone = BadgeTone.AMBER)
                                        StatusPill(text = "Capacity: ${draft.partyOrSeatCount}", tone = BadgeTone.NAVY)
                                    }
                                    Text(
                                        text = "${draft.pickupStand} → ${draft.destination}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (draft.viaPointsSummary.isNotBlank()) {
                                        Text(
                                            text = "Via: ${draft.viaPointsSummary}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (farePreview != null) {
                                        Text(
                                            text = farePreview.farePolicyNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                DriverDashboardTab.BOOKING_REQUESTS -> {
                    SectionHeader(
                        title = "Booking Requests",
                        subtitle = "Genuine commuter party requests require backend integration"
                    )
                    when (driverFeed) {
                        is DriverRequestsFeedResult.BackendUnavailable -> {
                            UnavailableServiceBanner(
                                title = "Booking Requests Unavailable (Offline)",
                                message = driverFeed.message
                            )
                            EmptyStateCard(
                                icon = Icons.Default.Lock,
                                title = "No Genuine Booking Requests",
                                description = "Accept and Reject controls unlock only for real backend-verified booking offers. A local click never simulates a real booking request."
                            )
                        }
                        is DriverRequestsFeedResult.AvailableRequests -> {
                            if (driverFeed.pendingRequests.isEmpty()) {
                                EmptyStateCard(
                                    icon = Icons.Default.DirectionsCar,
                                    title = "No Pending Requests",
                                    description = "No commuter parties have requested your route yet."
                                )
                            } else {
                                driverFeed.pendingRequests.forEach { request ->
                                    DriverBookingRequestCard(
                                        agreement = request,
                                        isBackendConnected = true,
                                        onAccept = {
                                            DriverWorkflowCoordinator.respondToGenuineBookingRequest(
                                                agreement = request,
                                                accept = true,
                                                isBackendConnected = true
                                            )
                                        },
                                        onReject = {
                                            DriverWorkflowCoordinator.respondToGenuineBookingRequest(
                                                agreement = request,
                                                accept = false,
                                                isBackendConnected = true
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                DriverDashboardTab.ACCEPTED_BOOKINGS -> {
                    SectionHeader(
                        title = "Accepted Bookings",
                        subtitle = "Bookings accepted by you awaiting or holding passenger confirmation"
                    )
                    UnavailableServiceBanner(
                        title = "Accepted Bookings Unavailable (Offline)",
                        message = DriverRequestsFeedResult.DRIVER_BACKEND_UNAVAILABLE_MESSAGE
                    )
                    EmptyStateCard(
                        icon = Icons.Default.CheckCircle,
                        title = "No Accepted Bookings",
                        description = "Accepted commuter party bookings will appear here when backend integration is active."
                    )
                }

                DriverDashboardTab.COMPLETED_BOOKINGS -> {
                    SectionHeader(
                        title = "Completed Bookings",
                        subtitle = "Verified completed rides with total booking fare settlement"
                    )
                    UnavailableServiceBanner(
                        title = "Completed History Unavailable (Offline)",
                        message = DriverRequestsFeedResult.DRIVER_BACKEND_UNAVAILABLE_MESSAGE
                    )
                    EmptyStateCard(
                        icon = Icons.Default.History,
                        title = "No Completed Bookings",
                        description = "No simulated or fabricated past trips are displayed while offline."
                    )
                }

                DriverDashboardTab.CANCELLED_BOOKINGS -> {
                    SectionHeader(
                        title = "Cancelled Bookings",
                        subtitle = "Rejected or cancelled booking requests"
                    )
                    UnavailableServiceBanner(
                        title = "Cancelled Bookings Unavailable (Offline)",
                        message = DriverRequestsFeedResult.DRIVER_BACKEND_UNAVAILABLE_MESSAGE
                    )
                    EmptyStateCard(
                        icon = Icons.Default.Cancel,
                        title = "No Cancelled Bookings",
                        description = "Declined or cancelled requests appear here only from genuine booking records."
                    )
                }
            }

            LockedRulesNoticeCard(title = "Driver Rules & Fare Invariants")
        }
    }
}

@Composable
private fun DriverBookingRequestCard(
    agreement: BookingAgreement,
    isBackendConnected: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val farePreview = DriverWorkflowCoordinator.formatDriverTotalFarePreview(
        totalBookingFare = agreement.agreedTotalFare,
        availableCapacity = agreement.passengerParty.passengerCount
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = "Party of ${agreement.passengerParty.passengerCount}",
                    tone = BadgeTone.NAVY
                )
                StatusPill(text = agreement.state.name, tone = BadgeTone.AMBER)
            }
            Text(
                text = "${agreement.designatedStationStand} → ${agreement.destination}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (farePreview != null) {
                Text(
                    text = farePreview.farePolicyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    enabled = isBackendConnected,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Reject")
                }
                Button(
                    onClick = onAccept,
                    enabled = isBackendConnected,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Accept")
                }
            }
        }
    }
}

@Composable
private fun ModeChipButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics {
                selected = isActive
                contentDescription = if (isActive) "$label, currently selected" else "Switch to $label"
            },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
            contentColor = if (isActive) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun ProminentActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentTone: BadgeTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (badgeBg, badgeFg) = when (accentTone) {
        BadgeTone.AMBER -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 76.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color = badgeBg, shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = badgeFg)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun QuickAccessTile(
    title: String,
    metric: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 96.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                StatusPill(text = metric, tone = BadgeTone.NEUTRAL)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
