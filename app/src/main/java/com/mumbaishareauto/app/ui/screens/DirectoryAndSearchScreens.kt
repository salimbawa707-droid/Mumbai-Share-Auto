package com.mumbaishareauto.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mumbaishareauto.app.LiveOfferFeedResult
import com.mumbaishareauto.app.OfflineDriverOfferDataSource
import com.mumbaishareauto.app.PassengerWorkflowCoordinator
import com.mumbaishareauto.app.auth.AuthDomainValidator
import com.mumbaishareauto.app.data.MumbaiStationDirectory
import com.mumbaishareauto.app.data.local.FavoriteRouteRepository
import com.mumbaishareauto.app.data.remote.LiveAutoRoute
import com.mumbaishareauto.app.data.remote.LiveRoutesStreamState
import com.mumbaishareauto.app.data.remote.RouteSeatLock
import com.mumbaishareauto.app.model.RailwayLine
import com.mumbaishareauto.app.model.StationSide
import com.mumbaishareauto.app.model.StationStandRoute
import com.mumbaishareauto.app.ui.LiveRouteActionState
import com.mumbaishareauto.app.ui.components.BadgeTone
import com.mumbaishareauto.app.ui.components.DestructiveConfirmDialog
import com.mumbaishareauto.app.ui.components.EmptyStateCard
import com.mumbaishareauto.app.ui.components.LoadingStateCard
import com.mumbaishareauto.app.ui.components.ResponsiveContentColumn
import com.mumbaishareauto.app.ui.components.SectionHeader
import com.mumbaishareauto.app.ui.components.StandRouteCard
import com.mumbaishareauto.app.ui.components.StatusPill
import com.mumbaishareauto.app.ui.components.UnavailableServiceBanner
import com.mumbaishareauto.app.ui.components.ValidationFeedbackCard
import com.mumbaishareauto.app.ui.share.ShareUtils
import java.util.Locale

/**
 * Screen 6: Find Shared Auto
 * Step-by-step passenger corridor & stand discovery:
 * Railway Line -> Searchable Station -> East/West Side -> Designated Stand -> Destination,
 * combined with real-time Cloud Firestore active routes, live queue counts, and atomic seat locking.
 */
@Composable
fun FindSharedAutoScreen(
    favoriteRepo: FavoriteRouteRepository,
    onSelectStandForOffer: (StationStandRoute) -> Unit,
    liveRoutesState: LiveRoutesStreamState = LiveRoutesStreamState.NotConfigured(),
    liveRouteActionState: LiveRouteActionState = LiveRouteActionState(),
    currentAuthUid: String? = null,
    onLockSeat: (postingId: String, seatsRequested: Int) -> Unit = { _, _ -> },
    onReleaseSeat: (postingId: String) -> Unit = {},
    onSelectRouteForQueue: (postingId: String?) -> Unit = {},
    onOpenPhoneAuth: () -> Unit = {}
) {
    val context = LocalContext.current
    var corridorQuery by rememberSaveable { mutableStateOf("") }
    var stationSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedLineName by rememberSaveable { mutableStateOf(RailwayLine.ALL.name) }
    var selectedStation by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSideName by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedLine = RailwayLine.entries.firstOrNull { it.name == selectedLineName } ?: RailwayLine.ALL
    val selectedSide = StationSide.entries.firstOrNull { it.name == selectedSideName }
    val favoriteKeys by favoriteRepo.favoriteKeys.collectAsState()

    val searchableStations = remember(selectedLine, stationSearchQuery) {
        PassengerWorkflowCoordinator.searchStations(selectedLine, stationSearchQuery)
    }

    val filteredRoutes = remember(corridorQuery, selectedLine, selectedStation, selectedSide) {
        PassengerWorkflowCoordinator.filterStands(
            line = selectedLine,
            stationName = selectedStation,
            side = selectedSide,
            query = corridorQuery
        )
    }

    val matchingLiveRoutes = remember(liveRoutesState, selectedStation, stationSearchQuery, selectedSide, corridorQuery) {
        val routes = (liveRoutesState as? LiveRoutesStreamState.Live)?.routes.orEmpty()
        val effectiveStationQuery = selectedStation ?: stationSearchQuery
        routes.filter { route ->
            route.matchesSearch(
                stationQuery = effectiveStationQuery,
                sideFilter = selectedSide,
                destinationOrCorridorQuery = corridorQuery
            )
        }
    }

    val liveOfferFeed = remember(selectedStation, corridorQuery) {
        OfflineDriverOfferDataSource.fetchOffers(
            pickupStandQuery = selectedStation ?: "",
            destinationQuery = corridorQuery
        )
    }

    ResponsiveContentColumn(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(
                    title = "Find a Shared Auto",
                    subtitle = "1. Line • 2. Searchable Station • 3. East/West • 4. Pickup Stand & Destination"
                )
            }

            // Step 2: Railway Line selector
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RailwayLine.entries) { line ->
                        FilterChip(
                            selected = selectedLine == line,
                            onClick = {
                                selectedLineName = line.name
                                selectedStation = null
                            },
                            label = { Text(line.displayName) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }
            }

            // Step 3: Searchable Station selector
            item {
                OutlinedTextField(
                    value = stationSearchQuery,
                    onValueChange = { stationSearchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Search station name" },
                    label = { Text("Search station (e.g. Bandra, Andheri, Kurla, Thane)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (stationSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { stationSearchQuery = "" },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear station search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedStation == null,
                            onClick = { selectedStation = null },
                            label = { Text("All Stations") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                    items(searchableStations) { station ->
                        FilterChip(
                            selected = selectedStation.equals(station, ignoreCase = true),
                            onClick = {
                                selectedStation = if (selectedStation.equals(station, ignoreCase = true)) {
                                    null
                                } else {
                                    station
                                }
                            },
                            label = { Text(station) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }
            }

            // Step 4: East / West station exit filter
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedSide == null,
                            onClick = { selectedSideName = null },
                            label = { Text("East & West") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                    items(StationSide.entries) { side ->
                        FilterChip(
                            selected = selectedSide == side,
                            onClick = {
                                selectedSideName = if (selectedSide == side) null else side.name
                            },
                            label = { Text("${side.label} Exit") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }
            }

            // Destination / landmark filter
            item {
                OutlinedTextField(
                    value = corridorQuery,
                    onValueChange = { corridorQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Filter destination corridor or landmark" },
                    label = { Text("Filter destination, stand, or via point") },
                    placeholder = { Text("e.g. BKC, SEEPZ, Lokhandwala, Powai") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (corridorQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { corridorQuery = "" },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear destination filter")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        text = "${filteredRoutes.size} Reference Stand(s)",
                        tone = BadgeTone.NAVY
                    )
                    if (corridorQuery.isNotBlank() || stationSearchQuery.isNotBlank() ||
                        selectedLine != RailwayLine.ALL || selectedStation != null || selectedSide != null
                    ) {
                        OutlinedButton(
                            onClick = {
                                corridorQuery = ""
                                stationSearchQuery = ""
                                selectedLineName = RailwayLine.ALL.name
                                selectedStation = null
                                selectedSideName = null
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Reset Filters")
                        }
                    }
                }
            }

            if (!liveRouteActionState.lastActionMessage.isNullOrBlank()) {
                item {
                    ValidationFeedbackCard(
                        message = liveRouteActionState.lastActionMessage,
                        isSuccess = true
                    )
                }
            }
            if (!liveRouteActionState.lastActionError.isNullOrBlank()) {
                item {
                    ValidationFeedbackCard(
                        message = liveRouteActionState.lastActionError,
                        isSuccess = false
                    )
                }
            }

            // Real-time Firestore live auto routes & seat availability feed
            when (liveRoutesState) {
                is LiveRoutesStreamState.NotConfigured -> {
                    item {
                        UnavailableServiceBanner(
                            title = "Live Driver Offers",
                            message = (liveOfferFeed as? LiveOfferFeedResult.BackendUnavailable)?.message
                                ?: LiveOfferFeedResult.EMPTY_LIVE_OFFERS_MESSAGE
                        )
                    }
                }

                is LiveRoutesStreamState.AuthRequired -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Live Firestore Auto Availability",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = liveRoutesState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Button(
                                    onClick = onOpenPhoneAuth,
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                ) {
                                    Text("Sign In with +91 OTP")
                                }
                            }
                        }
                    }
                }

                LiveRoutesStreamState.Connecting -> {
                    item {
                        LoadingStateCard(label = "Syncing live share-auto routes and seats from Cloud Firestore...")
                    }
                }

                is LiveRoutesStreamState.Error -> {
                    item {
                        ValidationFeedbackCard(
                            message = liveRoutesState.message,
                            isSuccess = false
                        )
                    }
                }

                is LiveRoutesStreamState.Live -> {
                    item {
                        SectionHeader(
                            title = "Live Share-Auto Routes (${matchingLiveRoutes.size})",
                            subtitle = "Real-time seat availability & queue updates via Firestore"
                        )
                    }
                    if (matchingLiveRoutes.isEmpty()) {
                        item {
                            UnavailableServiceBanner(
                                title = "No Matching Live Autos Right Now",
                                message = "Drivers posting active routes at your selected station stand will appear here immediately."
                            )
                        }
                    } else {
                        items(matchingLiveRoutes, key = { "live_${it.postingId}" }) { liveRoute ->
                            val isSelectedQueue = liveRouteActionState.selectedQueuePostingId == liveRoute.postingId
                            LiveAutoRouteCard(
                                route = liveRoute,
                                currentAuthUid = currentAuthUid,
                                isLocking = liveRouteActionState.lockingPostingId == liveRoute.postingId,
                                isReleasing = liveRouteActionState.releasingPostingId == liveRoute.postingId,
                                isQueueExpanded = isSelectedQueue,
                                queueLocks = if (isSelectedQueue) liveRouteActionState.selectedQueueLocks else emptyList(),
                                allowSeatLock = false,
                                onLockSeats = { seats -> onLockSeat(liveRoute.postingId, seats) },
                                onReleaseSeat = { onReleaseSeat(liveRoute.postingId) },
                                onToggleQueueView = {
                                    onSelectRouteForQueue(
                                        if (isSelectedQueue) null else liveRoute.postingId
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (filteredRoutes.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.LocationOn,
                        title = "No Matching Stands Found",
                        description = "No designated stand matches your railway line, station, exit side, and destination filter.",
                        actionLabel = "Reset Filters",
                        onAction = {
                            corridorQuery = ""
                            stationSearchQuery = ""
                            selectedLineName = RailwayLine.ALL.name
                            selectedStation = null
                            selectedSideName = null
                        }
                    )
                }
            } else {
                items(filteredRoutes, key = { it.id }) { route ->
                    StandRouteCard(
                        route = route,
                        isFavorite = favoriteKeys.contains(route.id),
                        onToggleFavorite = { favoriteRepo.toggleFavorite(route.id) },
                        onUseInOffer = { onSelectStandForOffer(route) },
                        onOpenMap = { ShareUtils.openStandOnMap(context, route) },
                        onShare = { ShareUtils.shareStandDetails(context, route) }
                    )
                }
            }
        }
    }
}

@Composable
fun LiveAutoRouteCard(
    route: LiveAutoRoute,
    currentAuthUid: String?,
    isLocking: Boolean,
    isReleasing: Boolean,
    isQueueExpanded: Boolean,
    queueLocks: List<RouteSeatLock>,
    allowSeatLock: Boolean = true,
    onLockSeats: (Int) -> Unit,
    onReleaseSeat: () -> Unit,
    onToggleQueueView: () -> Unit
) {
    val context = LocalContext.current
    var selectedSeats by rememberSaveable(route.postingId, route.availableSeats) {
        mutableIntStateOf(1.coerceAtMost(route.availableSeats.coerceAtLeast(1)))
    }
    val userHoldsActiveLock = currentAuthUid != null &&
        queueLocks.any { it.passengerUid == currentAuthUid && it.status == RouteSeatLock.STATUS_LOCKED }
    val isOwnDriverRoute = currentAuthUid != null && route.driverUid == currentAuthUid
    val formattedFare = if (route.totalBookingFare % 1.0 == 0.0) {
        String.format(Locale.US, "₹%.0f", route.totalBookingFare)
    } else {
        String.format(Locale.US, "₹%.2f", route.totalBookingFare)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusPill(
                        text = "${route.pickupStation} • ${route.pickupSide.label}",
                        tone = BadgeTone.NAVY
                    )
                    StatusPill(
                        text = if (route.availableSeats > 0) {
                            "${route.availableSeats}/${route.totalCapacitySeats} Seats Left"
                        } else {
                            "Full (0 Left)"
                        },
                        tone = if (route.availableSeats > 0) BadgeTone.EMERALD else BadgeTone.RED
                    )
                }
                StatusPill(
                    text = "Queue: ${route.activeQueueCount}",
                    tone = BadgeTone.AMBER
                )
            }

            Text(
                text = "${route.designatedStand} → ${route.destination}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (route.viaPoints.isNotEmpty()) {
                Text(
                    text = "Via: ${route.viaPoints.joinToString(" • ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$formattedFare Total Booking Fare",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!route.driverAutoRegNumber.isNullOrBlank()) {
                    StatusPill(
                        text = AuthDomainValidator.formatAutoRegForDisplay(route.driverAutoRegNumber),
                        tone = BadgeTone.NEUTRAL
                    )
                }
            }

            Text(
                text = "Flat booking fare covers your whole party (never multiplied per seat).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isOwnDriverRoute) {
                OutlinedButton(
                    onClick = { ShareUtils.openDriverPickupInMaps(context, route) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Driver Pickup in Maps")
                }
                Text(
                    text = "Navigate to the driver's designated pickup stand. This is not live GPS tracking.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isOwnDriverRoute && route.availableSeats > 0 && !userHoldsActiveLock) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Seats:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    (1..route.availableSeats.coerceAtMost(LiveAutoRoute.MAX_SEATS)).forEach { seatCount ->
                        FilterChip(
                            selected = selectedSeats == seatCount,
                            onClick = { selectedSeats = seatCount },
                            label = { Text("$seatCount") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (userHoldsActiveLock) {
                    OutlinedButton(
                        onClick = onReleaseSeat,
                        enabled = !isReleasing,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        if (isReleasing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Release My Seat Lock")
                    }
                } else if (!allowSeatLock) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Negotiate total fare first")
                    }
                } else {
                    Button(
                        onClick = { onLockSeats(selectedSeats) },
                        enabled = !isLocking && !isOwnDriverRoute && route.availableSeats > 0,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        if (isLocking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Locking...")
                        } else if (isOwnDriverRoute) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Your Route")
                        } else if (route.availableSeats <= 0) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("No Seats Left")
                        } else {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lock $selectedSeats Seat${if (selectedSeats > 1) "s" else ""}")
                        }
                    }
                }

                OutlinedButton(
                    onClick = onToggleQueueView,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text(if (isQueueExpanded) "Hide Queue" else "Live Queue (${route.activeQueueCount})")
                }
            }

            if (isQueueExpanded) {
                HorizontalDivider()
                Text(
                    text = "Real-Time Queue Updates",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (queueLocks.isEmpty()) {
                    Text(
                        text = "No passengers locked in queue yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    queueLocks.forEach { lock ->
                        val isMe = currentAuthUid != null && lock.passengerUid == currentAuthUid
                        Text(
                            text = "• Queue #${lock.queuePosition}: ${lock.seatsLocked} seat(s) ${lock.status}${if (isMe) " (Your Lock)" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isMe) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen 7: Station Directory
 * Searchable & filterable reference index across Western, Central, and Harbour railway lines.
 */
@Composable
fun StationDirectoryScreen(
    favoriteRepo: FavoriteRouteRepository,
    onSelectStandForOffer: (StationStandRoute) -> Unit
) {
    val context = LocalContext.current
    val favoriteKeys by favoriteRepo.favoriteKeys.collectAsState()
    var selectedLineName by rememberSaveable { mutableStateOf(RailwayLine.ALL.name) }
    var selectedSideName by rememberSaveable { mutableStateOf<String?>(null) }
    var stationSearch by rememberSaveable { mutableStateOf("") }
    var destinationSearch by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }

    val selectedLine = RailwayLine.entries.firstOrNull { it.name == selectedLineName } ?: RailwayLine.ALL
    val selectedSide = StationSide.entries.firstOrNull { it.name == selectedSideName }

    val routes = remember(selectedLine, selectedSide, stationSearch, destinationSearch, favoritesOnly, favoriteKeys) {
        MumbaiStationDirectory.filterDirectory(
            stationQuery = stationSearch,
            destinationQuery = destinationSearch,
            line = selectedLine,
            side = selectedSide,
            favoritesOnly = favoritesOnly,
            favoriteIds = favoriteKeys
        )
    }
    val stations = remember(routes) {
        routes.map { it.stationName }.distinct()
    }

    fun resetFilters() {
        selectedLineName = RailwayLine.ALL.name
        selectedSideName = null
        stationSearch = ""
        destinationSearch = ""
        favoritesOnly = false
    }

    ResponsiveContentColumn(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader(
                    title = "Mumbai Station Stand Directory",
                    subtitle = "${routes.size} designated stands across ${stations.size} suburban stations"
                )
            }

            // Railway Line Filter
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RailwayLine.entries) { line ->
                        val count = MumbaiStationDirectory.getRoutesByLine(line).size
                        FilterChip(
                            selected = selectedLine == line,
                            onClick = { selectedLineName = line.name },
                            label = { Text("${line.displayName} ($count)") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }
            }

            // Visually obvious East / West + Favorites Filter Row
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedSide == null,
                            onClick = { selectedSideName = null },
                            label = { Text("East & West") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                    items(StationSide.entries) { side ->
                        FilterChip(
                            selected = selectedSide == side,
                            onClick = {
                                selectedSideName = if (selectedSide == side) null else side.name
                            },
                            label = { Text(side.shortBadge) },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                    item {
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = { favoritesOnly = !favoritesOnly },
                            label = { Text("Saved Stands (${favoriteKeys.size})") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }
            }

            // Search by Station Name
            item {
                OutlinedTextField(
                    value = stationSearch,
                    onValueChange = { stationSearch = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Directory search by station name" },
                    label = { Text("Search by station name (e.g. Bandra, Andheri, Dadar, Thane)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (stationSearch.isNotEmpty()) {
                            IconButton(
                                onClick = { stationSearch = "" },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear directory station search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Search by Destination
            item {
                OutlinedTextField(
                    value = destinationSearch,
                    onValueChange = { destinationSearch = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Directory search by destination corridor" },
                    label = { Text("Search by destination, landmark, or via point") },
                    placeholder = { Text("e.g. BKC, SEEPZ, Lokhandwala, Wagle Estate") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (destinationSearch.isNotEmpty()) {
                            IconButton(
                                onClick = { destinationSearch = "" },
                                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear directory destination search")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (selectedLine != RailwayLine.ALL || selectedSide != null || stationSearch.isNotBlank() || destinationSearch.isNotBlank() || favoritesOnly) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(text = "${routes.size} Matching Stand(s)", tone = BadgeTone.NAVY)
                        OutlinedButton(
                            onClick = { resetFilters() },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Reset Directory Filters")
                        }
                    }
                }
            }

            item {
                UnavailableServiceBanner(
                    title = "Unverified Reference Directory",
                    message = "Coordinates, distances, journey times, and landmarks are unverified orientation data until independently confirmed. Actual booking fares are posted per complete booking by drivers."
                )
            }

            if (routes.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.LocationOn,
                        title = "No Matching Station Stands",
                        description = "No designated stand matches your railway line, East/West filter, station, or destination search.",
                        actionLabel = "Reset Filters",
                        onAction = { resetFilters() }
                    )
                }
            } else {
                stations.forEach { stationName ->
                    val stationRoutes = routes.filter { it.stationName == stationName }
                    val eastCount = stationRoutes.count { it.side == StationSide.EAST }
                    val westCount = stationRoutes.count { it.side == StationSide.WEST }
                    item(key = "header_$stationName") {
                        SectionHeader(
                            title = "$stationName Station",
                            subtitle = "${stationRoutes.size} stand corridor(s) • East: $eastCount • West: $westCount",
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(stationRoutes, key = { "dir_${it.id}" }) { route ->
                        StandRouteCard(
                            route = route,
                            isFavorite = favoriteKeys.contains(route.id),
                            onToggleFavorite = { favoriteRepo.toggleFavorite(route.id) },
                            onUseInOffer = { onSelectStandForOffer(route) },
                            onOpenMap = { ShareUtils.openStandOnMap(context, route) },
                            onShare = { ShareUtils.shareStandDetails(context, route) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen 12: Favorites
 * Real device-local saved stands managed via FavoriteRouteRepository with destructive confirmation dialog.
 */
@Composable
fun FavoritesScreen(
    favoriteRepo: FavoriteRouteRepository,
    onSelectStandForOffer: (StationStandRoute) -> Unit,
    onExploreDirectory: () -> Unit
) {
    val context = LocalContext.current
    val favoriteKeys by favoriteRepo.favoriteKeys.collectAsState()
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    val savedRoutes = remember(favoriteKeys) {
        MumbaiStationDirectory.allStationRoutes.filter { favoriteKeys.contains(it.id) }
    }

    if (showClearDialog) {
        DestructiveConfirmDialog(
            title = "Clear All Favorite Stands?",
            message = "This removes all ${savedRoutes.size} saved station stands from your device. You can bookmark them again anytime.",
            confirmLabel = "Clear All",
            onConfirm = {
                favoriteRepo.clearAllFavorites()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }

    ResponsiveContentColumn(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader(
                        title = "Saved Favorite Routes",
                        subtitle = "${savedRoutes.size} stand(s) saved locally on device",
                        modifier = Modifier.weight(1f)
                    )
                    if (savedRoutes.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            if (savedRoutes.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.BookmarkBorder,
                        title = "No Favorite Stands Yet",
                        description = "Bookmark frequent Mumbai railway station stands from Find Shared Auto or Station Directory for one-tap access.",
                        actionLabel = "Browse Station Directory",
                        onAction = onExploreDirectory
                    )
                }
            } else {
                items(savedRoutes, key = { "fav_${it.id}" }) { route ->
                    StandRouteCard(
                        route = route,
                        isFavorite = true,
                        onToggleFavorite = { favoriteRepo.removeFavorite(route.id) },
                        onUseInOffer = { onSelectStandForOffer(route) },
                        onOpenMap = { ShareUtils.openStandOnMap(context, route) },
                        onShare = { ShareUtils.shareStandDetails(context, route) }
                    )
                }
            }
        }
    }
}
