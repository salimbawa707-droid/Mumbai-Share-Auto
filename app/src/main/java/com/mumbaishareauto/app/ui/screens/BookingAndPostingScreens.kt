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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mumbaishareauto.app.BookingAgreement
import com.mumbaishareauto.app.BookingDomainCoordinator
import com.mumbaishareauto.app.BookingLifecycleState
import com.mumbaishareauto.app.DriverRequestsFeedResult
import com.mumbaishareauto.app.DriverWorkflowCoordinator
import com.mumbaishareauto.app.LiveOfferFeedResult
import com.mumbaishareauto.app.OfflineDriverOfferDataSource
import com.mumbaishareauto.app.PassengerParty
import com.mumbaishareauto.app.PassengerWorkflowCoordinator
import com.mumbaishareauto.app.PassengerWorkflowStage
import com.mumbaishareauto.app.ValidationResult
import com.mumbaishareauto.app.VehicleCapacityConfig
import com.mumbaishareauto.app.data.local.AppPreferencesRepository
import com.mumbaishareauto.app.data.local.LocalRouteDraft
import com.mumbaishareauto.app.data.remote.LiveAutoRoute
import com.mumbaishareauto.app.data.remote.LiveRouteTransactionEngine
import com.mumbaishareauto.app.data.remote.LiveRoutesStreamState
import com.mumbaishareauto.app.data.remote.PublishRouteCommand
import com.mumbaishareauto.app.model.RailwayLine
import com.mumbaishareauto.app.model.StationSide
import com.mumbaishareauto.app.model.StationStandRoute
import com.mumbaishareauto.app.ui.LiveRouteActionState
import com.mumbaishareauto.app.ui.components.BadgeTone
import com.mumbaishareauto.app.ui.components.DestructiveConfirmDialog
import com.mumbaishareauto.app.ui.components.EmptyStateCard
import com.mumbaishareauto.app.ui.components.LoadingStateCard
import com.mumbaishareauto.app.ui.components.LockedRulesNoticeCard
import com.mumbaishareauto.app.ui.components.ResponsiveContentColumn
import com.mumbaishareauto.app.ui.components.SectionHeader
import com.mumbaishareauto.app.ui.components.StatusPill
import com.mumbaishareauto.app.ui.components.UnavailableServiceBanner
import com.mumbaishareauto.app.ui.components.ValidationFeedbackCard

/**
 * Shared explicit evaluation capacity configuration (never fakes RTO legal verification).
 */
fun defaultEvaluationCapacityConfig(): VehicleCapacityConfig = VehicleCapacityConfig(
    maxPermittedPassengers = 3,
    configurationLabel = "Evaluation Limit (Subject to Official RTO Verification)",
    isLegallyVerified = false,
    isTestFixture = false
)

/**
 * Screen 8: Passenger Booking (Complete 10-Step Guided Passenger Experience)
 *
 * Guided Stages:
 * 1. Route & Stand Selection (Line -> Searchable Station -> East/West -> Pickup Stand -> Destination)
 * 2. Party Size & Genuine Route Offers Check (Offline shows exact required reference message)
 * 3. Booking Summary & Driver-Accepted Confirmation Guard
 * 4. Booking Status Tracker (DRAFT -> OFFERED -> DRIVER_ACCEPTED -> PASSENGER_CONFIRMED -> LOCKED)
 */
@Composable
fun PassengerBookingScreen(
    initialStand: StationStandRoute?,
    prefsRepo: AppPreferencesRepository,
    onInspectDraft: (LocalRouteDraft) -> Unit,
    onBrowseStands: () -> Unit,
    liveRoutesState: LiveRoutesStreamState = LiveRoutesStreamState.NotConfigured(),
    liveRouteActionState: LiveRouteActionState = LiveRouteActionState(),
    currentAuthUid: String? = null,
    onLockSeat: (postingId: String, seatsRequested: Int) -> Unit = { _, _ -> },
    onReleaseSeat: (postingId: String) -> Unit = {},
    onSelectRouteForQueue: (postingId: String?) -> Unit = {},
    onOpenPhoneAuth: () -> Unit = {}
) {
    var stageIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedLineName by rememberSaveable(initialStand?.id) {
        mutableStateOf(initialStand?.line?.name ?: RailwayLine.ALL.name)
    }
    var stationSearch by rememberSaveable(initialStand?.id) {
        mutableStateOf("")
    }
    var selectedStation by rememberSaveable(initialStand?.id) {
        mutableStateOf(initialStand?.stationName)
    }
    var selectedSideName by rememberSaveable(initialStand?.id) {
        mutableStateOf(initialStand?.side?.name)
    }
    var pickupPoint by rememberSaveable(initialStand?.id) {
        mutableStateOf(initialStand?.fullStandLocation ?: "")
    }
    var destination by rememberSaveable(initialStand?.id) {
        mutableStateOf(initialStand?.destination ?: "")
    }
    var partySizeText by rememberSaveable { mutableStateOf("") }
    var validationMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var validationSuccess by rememberSaveable { mutableStateOf(false) }
    var latestDraft by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedLine = RailwayLine.entries.firstOrNull { it.name == selectedLineName } ?: RailwayLine.ALL
    val selectedSide = StationSide.entries.firstOrNull { it.name == selectedSideName }
    val currentStage = PassengerWorkflowStage.entries[stageIndex.coerceIn(0, PassengerWorkflowStage.entries.lastIndex)]
    val evaluationConfig = defaultEvaluationCapacityConfig()

    val matchingStations = remember(selectedLine, stationSearch) {
        PassengerWorkflowCoordinator.searchStations(selectedLine, stationSearch)
    }
    val matchingStands = remember(selectedLine, selectedStation, selectedSide) {
        PassengerWorkflowCoordinator.filterStands(
            line = selectedLine,
            stationName = selectedStation,
            side = selectedSide
        )
    }

    val liveFeed = remember(pickupPoint, destination) {
        OfflineDriverOfferDataSource.fetchOffers(pickupPoint, destination)
    }

    val matchingLiveRoutes = remember(liveRoutesState, selectedStation, selectedSide, pickupPoint, destination) {
        val routes = (liveRoutesState as? LiveRoutesStreamState.Live)?.routes.orEmpty()
        val stationFilter = selectedStation?.takeIf { it.isNotBlank() } ?: ""
        routes.filter { route ->
            route.matchesSearch(
                stationQuery = stationFilter,
                sideFilter = selectedSide,
                destinationOrCorridorQuery = destination
            )
        }
    }

    val parsedPartyInt = partySizeText.trim().toIntOrNull()
    val partySizeInvalid = partySizeText.isNotBlank() &&
        (parsedPartyInt == null || parsedPartyInt <= 0 || parsedPartyInt > evaluationConfig.maxPermittedPassengers)
    val identicalRouteError = pickupPoint.trim().isNotEmpty() &&
        destination.trim().isNotEmpty() &&
        pickupPoint.trim().equals(destination.trim(), ignoreCase = true)

    fun evaluateViaCentralDomain() {
        val outcome = PassengerWorkflowCoordinator.validatePassengerInputs(
            offerId = BookingDomainCoordinator.generateUniqueBookingId("OFFER"),
            partyId = "PARTY_LOCAL",
            pickupStand = pickupPoint,
            destination = destination,
            partySizeInput = partySizeText,
            capacityConfig = evaluationConfig
        )
        if (outcome.isValid) {
            validationMessage = "Valid locally via BookingDomain (ready for live matching once connected)."
            validationSuccess = true
        } else {
            validationMessage = outcome.summary
            validationSuccess = false
        }

        val count = partySizeText.trim().toIntOrNull() ?: 0
        val draft = LocalRouteDraft(
            id = BookingDomainCoordinator.generateUniqueBookingId("DRAFT_PAX"),
            roleLabel = "Passenger Offer Draft",
            pickupStand = pickupPoint.trim().ifBlank { "(Unfilled Stand)" },
            destination = destination.trim().ifBlank { "(Unfilled Destination)" },
            partyOrSeatCount = count.coerceAtLeast(0),
            totalFareInr = null,
            validationSummary = validationMessage ?: "",
            isStructurallyValid = outcome.isValid,
            isCommerciallyVerified = false,
            createdAtMs = System.currentTimeMillis()
        )
        latestDraft = draft.serialize()
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
                title = "Passenger Booking Workflow",
                subtitle = "Step ${currentStage.stepNumber} of ${PassengerWorkflowStage.entries.size}: ${currentStage.label}"
            )

            LinearProgressIndicator(
                progress = { currentStage.stepNumber.toFloat() / PassengerWorkflowStage.entries.size.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PassengerWorkflowStage.entries) { stage ->
                    FilterChip(
                        selected = currentStage == stage,
                        onClick = { stageIndex = stage.ordinal },
                        label = { Text("${stage.stepNumber}. ${stage.label}") },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }

            when (currentStage) {
                PassengerWorkflowStage.ROUTE_SELECTION -> {
                    SectionHeader(
                        title = "1. Line, Station & East/West Stand",
                        subtitle = "Driver waits at the designated pickup point; passengers travel there"
                    )

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

                    OutlinedTextField(
                        value = stationSearch,
                        onValueChange = { stationSearch = it },
                        label = { Text("Search Railway Station") },
                        placeholder = { Text("e.g. Bandra, Andheri, Dadar, Thane") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedStation == null,
                                onClick = { selectedStation = null },
                                label = { Text("All Stations") },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                        }
                        items(matchingStations) { station ->
                            FilterChip(
                                selected = selectedStation.equals(station, ignoreCase = true),
                                onClick = {
                                    selectedStation = if (selectedStation.equals(station, ignoreCase = true)) null else station
                                },
                                label = { Text(station) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                        }
                    }

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
                                onClick = { selectedSideName = if (selectedSide == side) null else side.name },
                                label = { Text(side.label) },
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            )
                        }
                    }

                    if (matchingStands.isNotEmpty()) {
                        Text(
                            text = "Tap a Designated Station Stand to Fill Route (${matchingStands.size}):",
                            style = MaterialTheme.typography.labelLarge
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(matchingStands.take(10), key = { it.id }) { stand ->
                                FilterChip(
                                    selected = pickupPoint == stand.fullStandLocation && destination == stand.destination,
                                    onClick = {
                                        selectedStation = stand.stationName
                                        selectedSideName = stand.side.name
                                        pickupPoint = stand.fullStandLocation
                                        destination = stand.destination
                                    },
                                    label = { Text("${stand.stationCode} (${stand.side.label}): ${stand.standName}") },
                                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = pickupPoint,
                        onValueChange = { pickupPoint = it },
                        label = { Text("Designated Pickup Stand (Driver waits here)") },
                        placeholder = { Text("Select above or enter station stand") },
                        singleLine = true,
                        isError = identicalRouteError || (validationMessage != null && pickupPoint.trim().length < 3),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Designated station pickup stand" }
                    )

                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it },
                        label = { Text("Intended Destination") },
                        placeholder = { Text("e.g. BKC Diamond Bourse") },
                        singleLine = true,
                        isError = identicalRouteError || (validationMessage != null && destination.trim().length < 3),
                        supportingText = {
                            if (identicalRouteError) {
                                Text("Destination cannot match the pickup stand")
                            } else {
                                Text("Passenger travels to the pickup stand above")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Intended destination point" }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBrowseStands,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("All Stands")
                        }
                        Button(
                            onClick = { stageIndex = PassengerWorkflowStage.PARTY_AND_OFFERS.ordinal },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Next: Party & Offers")
                        }
                    }
                }

                PassengerWorkflowStage.PARTY_AND_OFFERS -> {
                    SectionHeader(
                        title = "2. Passenger Party Size & Live Offers",
                        subtitle = "Single passenger party only — different parties never automatically merge"
                    )

                    OutlinedTextField(
                        value = partySizeText,
                        onValueChange = { partySizeText = it },
                        label = { Text("Passenger Party Size (Positive Integer, 1 to ${evaluationConfig.maxPermittedPassengers})") },
                        placeholder = { Text("e.g. 2") },
                        singleLine = true,
                        isError = partySizeInvalid,
                        supportingText = {
                            Text("Capacity verification required: official RTO approval pending (${evaluationConfig.configurationLabel})")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Passenger party size count" }
                    )

                    Button(
                        onClick = { evaluateViaCentralDomain() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                    ) {
                        Text("Validate Party & Route via BookingDomain")
                    }

                    if (validationMessage != null) {
                        ValidationFeedbackCard(
                            message = validationMessage ?: "",
                            isSuccess = validationSuccess
                        )
                    }

                    when (liveRoutesState) {
                        is LiveRoutesStreamState.NotConfigured -> {
                            UnavailableServiceBanner(
                                title = "Genuine Live Driver Offers",
                                message = (liveFeed as? LiveOfferFeedResult.BackendUnavailable)?.message
                                    ?: LiveOfferFeedResult.EMPTY_LIVE_OFFERS_MESSAGE
                            )
                        }

                        is LiveRoutesStreamState.AuthRequired -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Live Firestore Offers",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = liveRoutesState.message,
                                        style = MaterialTheme.typography.bodySmall
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

                        LiveRoutesStreamState.Connecting -> {
                            LoadingStateCard(label = "Listening for live driver routes on Cloud Firestore...")
                        }

                        is LiveRoutesStreamState.Error -> {
                            ValidationFeedbackCard(
                                message = liveRoutesState.message,
                                isSuccess = false
                            )
                        }

                        is LiveRoutesStreamState.Live -> {
                            if (!liveRouteActionState.lastActionMessage.isNullOrBlank()) {
                                ValidationFeedbackCard(
                                    message = liveRouteActionState.lastActionMessage,
                                    isSuccess = true
                                )
                            }
                            if (!liveRouteActionState.lastActionError.isNullOrBlank()) {
                                ValidationFeedbackCard(
                                    message = liveRouteActionState.lastActionError,
                                    isSuccess = false
                                )
                            }
                            if (matchingLiveRoutes.isEmpty()) {
                                UnavailableServiceBanner(
                                    title = "No Matching Live Autos",
                                    message = "No active driver route matches your current stand/destination right now."
                                )
                            } else {
                                matchingLiveRoutes.forEach { offer ->
                                    val isSelectedQueue = liveRouteActionState.selectedQueuePostingId == offer.postingId
                                    LiveAutoRouteCard(
                                        route = offer,
                                        currentAuthUid = currentAuthUid,
                                        isLocking = liveRouteActionState.lockingPostingId == offer.postingId,
                                        isReleasing = liveRouteActionState.releasingPostingId == offer.postingId,
                                        isQueueExpanded = isSelectedQueue,
                                        queueLocks = if (isSelectedQueue) liveRouteActionState.selectedQueueLocks else emptyList(),
                                        onLockSeats = { seats -> onLockSeat(offer.postingId, seats) },
                                        onReleaseSeat = { onReleaseSeat(offer.postingId) },
                                        onToggleQueueView = {
                                            onSelectRouteForQueue(
                                                if (isSelectedQueue) null else offer.postingId
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (matchingStands.isNotEmpty()) {
                        Text(
                            text = "Station Reference Information (${matchingStands.size} stand(s)):",
                            style = MaterialTheme.typography.labelLarge
                        )
                        matchingStands.take(3).forEach { ref ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("${ref.stationName} (${ref.side.label}) — ${ref.standName}", fontWeight = FontWeight.SemiBold)
                                    Text("→ ${ref.destination}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    Text("Landmark: ${ref.standLandmark}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            evaluateViaCentralDomain()
                            stageIndex = PassengerWorkflowStage.BOOKING_SUMMARY.ordinal
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("Review Booking Summary")
                    }
                }

                PassengerWorkflowStage.BOOKING_SUMMARY -> {
                    val count = (parsedPartyInt ?: 0).coerceAtLeast(0)
                    val summary = PassengerWorkflowCoordinator.buildBookingSummary(
                        pickupStand = pickupPoint.ifBlank { "Not Selected" },
                        destination = destination.ifBlank { "Not Selected" },
                        partySize = count,
                        driverPosting = null,
                        agreementState = BookingLifecycleState.DRAFT,
                        isLiveBackendConnected = false
                    )

                    SectionHeader(
                        title = "3. Booking Summary & Confirmation",
                        subtitle = "Confirmation unlocks ONLY after genuine driver acceptance"
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                StatusPill(text = "Single Party ($count pax)", tone = BadgeTone.NAVY)
                                StatusPill(text = summary.lifecycleState.name, tone = BadgeTone.AMBER)
                            }
                            Text("Pickup Stand: ${summary.pickupStand}", fontWeight = FontWeight.SemiBold)
                            Text("Destination: ${summary.destination}", fontWeight = FontWeight.SemiBold)
                            Text(summary.driverPickupRuleNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(summary.partyIsolationNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider()
                            Text(
                                text = "Total Booking Fare: Pending genuine driver posting (complete booking fare displayed flat for whole party, never multiplied per seat).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Private Split: Passengers arrange private fare contributions offline; individual contributions are never tracked by the app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Strict Confirmation Gate (always disabled without genuine DRIVER_ACCEPTED agreement)
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirm Booking (Requires Driver Acceptance)")
                    }

                    UnavailableServiceBanner(
                        title = "Confirmation Locked",
                        message = LiveOfferFeedResult.EMPTY_LIVE_OFFERS_MESSAGE
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val parsedLatest = latestDraft?.let { LocalRouteDraft.deserialize(it) }
                        if (parsedLatest != null) {
                            OutlinedButton(
                                onClick = {
                                    prefsRepo.saveLocalDraft(parsedLatest)
                                    onInspectDraft(parsedLatest)
                                },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                            ) {
                                Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save Draft")
                            }
                        }
                        Button(
                            onClick = { stageIndex = PassengerWorkflowStage.STATUS_TRACKING.ordinal },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Status Tracker")
                        }
                    }
                }

                PassengerWorkflowStage.STATUS_TRACKING -> {
                    SectionHeader(
                        title = "4. Booking Status Tracking",
                        subtitle = "Strict Version 1 lifecycle state machine"
                    )

                    val lifecycleSteps = listOf(
                        BookingLifecycleState.DRAFT to "Local party offer validated & prepared",
                        BookingLifecycleState.OFFERED to "Submitted to verified drivers (Requires Phase 2 backend)",
                        BookingLifecycleState.DRIVER_ACCEPTED to "Driver reviews party & accepts booking",
                        BookingLifecycleState.PASSENGER_CONFIRMED to "Passenger confirms after driver acceptance",
                        BookingLifecycleState.LOCKED to "Total booking fare locked for the party"
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            lifecycleSteps.forEachIndexed { idx, (state, desc) ->
                                val isCurrent = state == BookingLifecycleState.DRAFT
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isCurrent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "${idx + 1}. ${state.name}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    UnavailableServiceBanner(
                        title = "Live Tracking Offline",
                        message = LiveOfferFeedResult.EMPTY_LIVE_OFFERS_MESSAGE
                    )
                }
            }

            LockedRulesNoticeCard()
        }
    }
}

/**
 * Screen 9: Driver Route Posting (Phase 3 Route Posting Form)
 * Implements all 8 required fields and routes validation centrally through
 * DriverWorkflowCoordinator -> BookingDomain.DriverRoutePosting.validate().
 */
@Composable
fun DriverRoutePostingScreen(
    prefsRepo: AppPreferencesRepository,
    onInspectDraft: (LocalRouteDraft) -> Unit,
    isFirebaseConfigured: Boolean = false,
    signedInDriverUid: String? = null,
    driverAutoRegNumber: String? = null,
    liveRouteActionState: LiveRouteActionState = LiveRouteActionState(),
    onPublishLiveRoute: (PublishRouteCommand) -> Unit = {},
    onOpenPhoneAuth: () -> Unit = {}
) {
    var selectedLineName by rememberSaveable { mutableStateOf(RailwayLine.ALL.name) }
    var pickupStation by rememberSaveable { mutableStateOf("") }
    var selectedSideName by rememberSaveable { mutableStateOf<String?>(null) }
    var pickupPoint by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var viaPointsText by rememberSaveable { mutableStateOf("") }
    var totalFareText by rememberSaveable { mutableStateOf("") }
    var availableSeatsText by rememberSaveable { mutableStateOf("") }
    var validationSummary by rememberSaveable { mutableStateOf<String?>(null) }
    var isVerifiedValid by rememberSaveable { mutableStateOf(false) }
    var latestDraftSerialized by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedLine = RailwayLine.entries.firstOrNull { it.name == selectedLineName } ?: RailwayLine.ALL
    val selectedSide = StationSide.entries.firstOrNull { it.name == selectedSideName }
    val evaluationConfig = defaultEvaluationCapacityConfig()
    val verificationStatusText = DriverWorkflowCoordinator.getVehicleCapacityVerificationStatus(evaluationConfig)

    val matchingStations = remember(selectedLine, pickupStation) {
        DriverWorkflowCoordinator.searchStations(selectedLine, pickupStation)
    }
    val matchingStands = remember(selectedLine, pickupStation, selectedSide) {
        DriverWorkflowCoordinator.filterStandsForDriver(
            line = selectedLine,
            stationName = pickupStation.takeIf { it.isNotBlank() && matchingStations.any { s -> s.equals(it, ignoreCase = true) } },
            side = selectedSide
        )
    }

    val enteredFare = totalFareText.trim().toDoubleOrNull()
    val enteredCapacity = availableSeatsText.trim().toIntOrNull()
    val liveFarePreview = remember(enteredFare, enteredCapacity) {
        if (enteredFare != null) {
            DriverWorkflowCoordinator.formatDriverTotalFarePreview(
                totalBookingFare = enteredFare,
                availableCapacity = (enteredCapacity ?: 3).coerceAtLeast(1)
            )
        } else {
            null
        }
    }
    val identicalRouteError = pickupPoint.trim().isNotEmpty() &&
        destination.trim().isNotEmpty() &&
        pickupPoint.trim().equals(destination.trim(), ignoreCase = true)

    ResponsiveContentColumn(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = "Post My Route & Total Fare",
                subtitle = "Driver enters ONE total fare for the complete booking — never per seat"
            )

            // 1. Pickup Railway Station
            Text(
                text = "1. Pickup Railway Station",
                style = MaterialTheme.typography.labelLarge
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(RailwayLine.entries) { line ->
                    FilterChip(
                        selected = selectedLine == line,
                        onClick = { selectedLineName = line.name },
                        label = { Text(line.displayName) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }

            OutlinedTextField(
                value = pickupStation,
                onValueChange = { pickupStation = it },
                label = { Text("Pickup Railway Station") },
                placeholder = { Text("e.g. Bandra, Andheri, Dadar, Thane") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Driver pickup railway station" }
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(matchingStations.take(12)) { station ->
                    FilterChip(
                        selected = pickupStation.equals(station, ignoreCase = true),
                        onClick = { pickupStation = station },
                        label = { Text(station) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }

            // 2. Pickup East / West
            Text(
                text = "2. Pickup East / West Exit",
                style = MaterialTheme.typography.labelLarge
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(StationSide.entries) { side ->
                    FilterChip(
                        selected = selectedSide == side,
                        onClick = { selectedSideName = side.name },
                        label = { Text(side.label) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    )
                }
            }

            if (matchingStands.isNotEmpty()) {
                Text(
                    text = "Reference Stands (${matchingStands.size}) — Tap to Prefill:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(matchingStands.take(10), key = { it.id }) { stand ->
                        FilterChip(
                            selected = pickupPoint == stand.fullStandLocation && destination == stand.destination,
                            onClick = {
                                pickupStation = stand.stationName
                                selectedSideName = stand.side.name
                                pickupPoint = stand.fullStandLocation
                                destination = stand.destination
                                viaPointsText = stand.viaPoints.joinToString(", ")
                            },
                            label = { Text("${stand.stationCode} (${stand.side.label}): ${stand.standName}") },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        )
                    }
                }
            }

            // 3. Designated Stand
            OutlinedTextField(
                value = pickupPoint,
                onValueChange = { pickupPoint = it },
                label = { Text("3. Designated Stand (Driver Waits Here)") },
                placeholder = { Text("e.g. Andheri East Auto Deck Stand") },
                singleLine = true,
                isError = identicalRouteError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Driver designated stand" }
            )

            // 4. Destination
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("4. Destination") },
                placeholder = { Text("e.g. SEEPZ Gate 1 & 2 / MIDC") },
                singleLine = true,
                isError = identicalRouteError,
                supportingText = {
                    if (identicalRouteError) {
                        Text("Destination cannot match designated pickup stand")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Driver route destination" }
            )

            // 5. Via Points (where applicable)
            OutlinedTextField(
                value = viaPointsText,
                onValueChange = { viaPointsText = it },
                label = { Text("5. Via Points (Where Applicable, Comma-Separated)") },
                placeholder = { Text("e.g. Kalanagar, Family Court, BKC Junction") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Route via points" }
            )

            // 6. Total Booking Fare (ONE total fare for complete booking)
            OutlinedTextField(
                value = totalFareText,
                onValueChange = { totalFareText = it },
                label = { Text("6. Total Booking Fare (INR) — Complete Booking") },
                placeholder = { Text("e.g. 120") },
                singleLine = true,
                supportingText = {
                    Text("Example: ₹120 remains ₹120 even if passenger party has 2 or 3 people — NEVER per-seat")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Total booking fare in rupees" }
            )

            if (liveFarePreview != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Flat Booking Fare Preview: ${liveFarePreview.formattedTotalFare}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = liveFarePreview.farePolicyNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // 7. Available Passenger Capacity
            OutlinedTextField(
                value = availableSeatsText,
                onValueChange = { availableSeatsText = it },
                label = { Text("7. Available Passenger Capacity (1 to ${evaluationConfig.maxPermittedPassengers})") },
                placeholder = { Text("e.g. 3") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Available passenger capacity" }
            )

            // 8. Vehicle Capacity Verification Status
            Card(
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
                        Text(
                            text = "8. Vehicle Capacity Verification Status",
                            style = MaterialTheme.typography.labelLarge
                        )
                        StatusPill(text = "Unverified", tone = BadgeTone.AMBER)
                    }
                    Text(
                        text = verificationStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = evaluationConfig.regulatoryNotice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = {
                    val outcome = DriverWorkflowCoordinator.validateDriverRoutePostingInput(
                        postingId = BookingDomainCoordinator.generateUniqueBookingId("POST"),
                        driverId = "LOCAL_DRIVER_EVAL",
                        pickupStation = pickupStation,
                        pickupSide = selectedSide,
                        designatedStand = pickupPoint,
                        destination = destination,
                        viaPointsInput = viaPointsText,
                        totalFareInput = totalFareText,
                        availableCapacityInput = availableSeatsText,
                        capacityConfig = evaluationConfig
                    )
                    if (outcome.isValid) {
                        validationSummary = "Route posting validated via BookingDomain (${outcome.farePreview?.formattedTotalFare ?: ""})."
                        isVerifiedValid = true
                    } else {
                        validationSummary = outcome.summary
                        isVerifiedValid = false
                    }

                    val parsedCapacity = availableSeatsText.trim().toIntOrNull() ?: 0
                    val formattedPickupLabel = buildString {
                        if (pickupStation.isNotBlank()) {
                            append(pickupStation.trim())
                            if (selectedSide != null) append(" (${selectedSide.label})")
                            append(" — ")
                        }
                        append(pickupPoint.trim().ifBlank { "(Unfilled Stand)" })
                    }
                    val draft = LocalRouteDraft(
                        id = BookingDomainCoordinator.generateUniqueBookingId("DRAFT_DRV"),
                        roleLabel = "Driver Posting Draft",
                        pickupStand = formattedPickupLabel,
                        destination = destination.trim().ifBlank { "(Unfilled Destination)" },
                        partyOrSeatCount = parsedCapacity.coerceAtLeast(0),
                        totalFareInr = if (enteredFare != null && enteredFare.isFinite() && enteredFare > 0.0) enteredFare else null,
                        validationSummary = validationSummary ?: "",
                        isStructurallyValid = isVerifiedValid,
                        isCommerciallyVerified = false,
                        createdAtMs = System.currentTimeMillis(),
                        viaPointsSummary = outcome.viaPoints.joinToString(", ")
                    )
                    latestDraftSerialized = draft.serialize()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 52.dp)
            ) {
                Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Validate Posting via BookingDomain")
            }

            if (validationSummary != null) {
                ValidationFeedbackCard(
                    message = validationSummary ?: "",
                    isSuccess = isVerifiedValid
                )
            }

            if (!liveRouteActionState.lastActionMessage.isNullOrBlank()) {
                ValidationFeedbackCard(
                    message = liveRouteActionState.lastActionMessage,
                    isSuccess = true
                )
            }
            if (!liveRouteActionState.lastActionError.isNullOrBlank()) {
                ValidationFeedbackCard(
                    message = liveRouteActionState.lastActionError,
                    isSuccess = false
                )
            }

            if (!isFirebaseConfigured) {
                // Explicit disabled live broadcast button when Firebase is not configured in this build
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publish Live Route (Backend Unavailable)")
                }
            } else if (signedInDriverUid.isNullOrBlank()) {
                Button(
                    onClick = onOpenPhoneAuth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign In with +91 OTP to Publish Live Route")
                }
            } else {
                Button(
                    onClick = {
                        val parsedFare = totalFareText.trim().toDoubleOrNull() ?: 0.0
                        val parsedSeats = availableSeatsText.trim().toIntOrNull() ?: 0
                        val command = PublishRouteCommand(
                            driverUid = signedInDriverUid,
                            driverAutoRegNumber = driverAutoRegNumber,
                            pickupStation = pickupStation,
                            pickupSide = selectedSide,
                            designatedStand = pickupPoint,
                            destination = destination,
                            viaPoints = DriverWorkflowCoordinator.parseViaPoints(viaPointsText),
                            totalBookingFare = parsedFare,
                            availableSeats = parsedSeats
                        )
                        val preCheck = LiveRouteTransactionEngine.validatePublishCommand(command)
                        if (preCheck is com.mumbaishareauto.app.data.remote.FirestoreActionResult.Failure) {
                            validationSummary = preCheck.message
                            isVerifiedValid = false
                        } else {
                            onPublishLiveRoute(command)
                        }
                    },
                    enabled = !liveRouteActionState.isPublishingRoute,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 52.dp)
                ) {
                    if (liveRouteActionState.isPublishingRoute) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Publishing to Cloud Firestore...")
                    } else {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Publish Live Route to Cloud Firestore")
                    }
                }
            }

            val parsedDraft = latestDraftSerialized?.let { LocalRouteDraft.deserialize(it) }
            if (parsedDraft != null) {
                OutlinedButton(
                    onClick = {
                        prefsRepo.saveLocalDraft(parsedDraft)
                        onInspectDraft(parsedDraft)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Save Local Draft & View Lifecycle Details")
                }
            }

            if (!isFirebaseConfigured) {
                UnavailableServiceBanner(
                    title = "Live Publishing & RTO Verification Unavailable",
                    message = DriverRequestsFeedResult.DRIVER_BACKEND_UNAVAILABLE_MESSAGE
                )
            } else {
                UnavailableServiceBanner(
                    title = "RTO Permit Verification Disclosure",
                    message = "Live route publishing synced via Cloud Firestore (${LiveAutoRoute.MIN_SEATS}–${LiveAutoRoute.MAX_SEATS} seats). Official commercial RTO permit verification remains pending."
                )
            }

            LockedRulesNoticeCard(title = "Driver Rules & Total Fare Policy")
        }
    }
}

/**
 * Screen 10: Booking Details
 * Inspects a local draft and demonstrates genuine BookingAgreement lifecycle & fare invariance rules.
 */
@Composable
fun BookingDetailsScreen(
    draft: LocalRouteDraft?,
    prefsRepo: AppPreferencesRepository,
    onDraftDeleted: () -> Unit,
    onCreateOffer: () -> Unit
) {
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    if (showDeleteConfirm && draft != null) {
        DestructiveConfirmDialog(
            title = "Delete Local Draft?",
            message = "Remove draft \"${draft.pickupStand} → ${draft.destination}\" from local device storage?",
            confirmLabel = "Delete Draft",
            onConfirm = {
                prefsRepo.deleteLocalDraft(draft.id)
                showDeleteConfirm = false
                onDraftDeleted()
            },
            onDismiss = { showDeleteConfirm = false }
        )
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
                title = "Booking & Lifecycle Inspector",
                subtitle = "Total booking fare invariance & strict V1 state transitions"
            )

            if (draft == null) {
                EmptyStateCard(
                    icon = Icons.Default.Route,
                    title = "No Draft Selected",
                    description = "Save a local validation draft from Offer My Route or My Bookings to inspect its BookingDomain agreement rules.",
                    actionLabel = "Create Local Offer Draft",
                    onAction = onCreateOffer
                )
            } else {
                val sampleAgreement = BookingAgreement(
                    bookingId = draft.id,
                    postingId = "LOCAL_INSPECT",
                    passengerParty = PassengerParty(
                        partyId = "LOCAL_PARTY",
                        passengerCount = draft.partyOrSeatCount.coerceAtLeast(1)
                    ),
                    agreedTotalFare = draft.totalFareInr ?: 0.0,
                    designatedStationStand = draft.pickupStand,
                    destination = draft.destination,
                    state = BookingLifecycleState.DRAFT
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
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
                            StatusPill(text = draft.roleLabel, tone = BadgeTone.NAVY)
                            StatusPill(text = "Local Draft • Not Submitted", tone = BadgeTone.AMBER)
                        }

                        Text(
                            text = "${draft.pickupStand} → ${draft.destination}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (draft.viaPointsSummary.isNotBlank()) {
                            Text(
                                text = "Via Points: ${draft.viaPointsSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Seats / Party Count: ${draft.partyOrSeatCount}",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        val fareSnapshot = sampleAgreement.fareSnapshot()
                        val formattedFare = if (draft.totalFareInr != null) {
                            PassengerWorkflowCoordinator.formatTotalBookingFareDisplay(
                                totalBookingFare = fareSnapshot.passengerVisibleTotalFare,
                                partySize = draft.partyOrSeatCount.coerceAtLeast(1)
                            )
                        } else {
                            null
                        }
                        Text(
                            text = if (formattedFare != null) {
                                "Effective Total Party Fare: ${formattedFare.formattedTotalFare} (${formattedFare.billingScopeNote} Passenger & driver fare match: ${fareSnapshot.faresMatchExactly})"
                            } else {
                                "Total Fare: Pending driver total-booking quote"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        HorizontalDivider()

                        Text(
                            text = "Central BookingDomain Report:",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = draft.validationSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Lifecycle transition verification display (uses BookingAgreement.transitionTo)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Mandatory Fare-Lock Sequence",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        val steps = listOf(
                            BookingLifecycleState.DRAFT,
                            BookingLifecycleState.OFFERED,
                            BookingLifecycleState.DRIVER_ACCEPTED,
                            BookingLifecycleState.PASSENGER_CONFIRMED,
                            BookingLifecycleState.LOCKED
                        )
                        val (_, directLockAttempt) = sampleAgreement.transitionTo(BookingLifecycleState.LOCKED)
                        steps.forEachIndexed { idx, st ->
                            Text(
                                text = "${idx + 1}. ${st.name}${if (st == BookingLifecycleState.DRAFT) " (Current Local State)" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (st == BookingLifecycleState.DRAFT) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        if (directLockAttempt is ValidationResult.Invalid) {
                            Text(
                                text = "Domain Guard Active: ${directLockAttempt.summary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Text(
                            text = "Automatic Party Merge Check: ${BookingDomainCoordinator.canAutomaticallyMergeParties(sampleAgreement.passengerParty, sampleAgreement.passengerParty)} (Prohibited in V1)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Local Draft", color = MaterialTheme.colorScheme.error)
                }
            }

            UnavailableServiceBanner(
                title = "Live Agreement Sync & Payments Not Active",
                message = LiveOfferFeedResult.EMPTY_LIVE_OFFERS_MESSAGE
            )
        }
    }
}

/**
 * Screen 11: Booking History
 * Displays real persisted local drafts or honest empty state (zero fabricated past rides).
 */
@Composable
fun BookingHistoryScreen(
    prefsRepo: AppPreferencesRepository,
    onSelectDraft: (LocalRouteDraft) -> Unit,
    onStartOffer: () -> Unit
) {
    val drafts by prefsRepo.localDrafts.collectAsState()
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    if (showClearDialog) {
        DestructiveConfirmDialog(
            title = "Clear All Local Drafts?",
            message = "Delete all ${drafts.size} locally stored route drafts? This cannot be undone.",
            confirmLabel = "Clear All Drafts",
            onConfirm = {
                prefsRepo.clearAllDrafts()
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
                        title = "My Bookings & Local Drafts",
                        subtitle = "Live trip history requires Phase 2 backend",
                        modifier = Modifier.weight(1f)
                    )
                    if (drafts.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                UnavailableServiceBanner(
                    title = "No Live Bookings Active",
                    message = "Past trip receipts and live locked agreements connect when Phase 2 backend launches. Only drafts you explicitly save locally appear below."
                )
            }

            if (drafts.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.History,
                        title = "No Saved Local Drafts",
                        description = "Run a route validation on Offer My Route or Post Route & Fare and tap Save to store a local draft here.",
                        actionLabel = "Create Route Offer Draft",
                        onAction = onStartOffer
                    )
                }
            } else {
                items(drafts, key = { it.id }) { draft ->
                    Card(
                        onClick = { onSelectDraft(draft) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatusPill(text = draft.roleLabel, tone = BadgeTone.NAVY)
                                    StatusPill(text = "Local Only", tone = BadgeTone.AMBER)
                                }
                                Text(
                                    text = "${draft.pickupStand} → ${draft.destination}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = buildString {
                                        append("Count: ${draft.partyOrSeatCount}")
                                        if (draft.totalFareInr != null) {
                                            append(" • Total Fare: ₹${draft.totalFareInr}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Inspect draft",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
