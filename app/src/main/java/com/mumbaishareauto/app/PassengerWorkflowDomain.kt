package com.mumbaishareauto.app

import com.mumbaishareauto.app.data.MumbaiStationDirectory
import com.mumbaishareauto.app.model.RailwayLine
import com.mumbaishareauto.app.model.StationSide
import com.mumbaishareauto.app.model.StationStandRoute
import java.util.Locale

/**
 * Pure domain workflow & validation coordinator for the Phase 2 Passenger Experience.
 *
 * Preserves all locked Version 1 business rules in BookingDomain.kt:
 * - One booking = one passenger party (never merged).
 * - Total booking fare displayed flat for the whole party (never multiplied per seat).
 * - Passengers arrange private fare splits offline (app never tracks contributions).
 * - Driver waits at designated pickup stand; passengers travel there.
 * - Confirmation allowed ONLY after genuine BookingLifecycleState.DRIVER_ACCEPTED.
 * - Never fabricates live drivers or fake legal RTO verification.
 */
enum class PassengerWorkflowStage(val stepNumber: Int, val label: String) {
    ROUTE_SELECTION(1, "Stand & Route"),
    PARTY_AND_OFFERS(2, "Party & Offers"),
    BOOKING_SUMMARY(3, "Summary & Confirm"),
    STATUS_TRACKING(4, "Status Tracker")
}

sealed class LiveOfferFeedResult {
    data class BackendUnavailable(
        val message: String = EMPTY_LIVE_OFFERS_MESSAGE
    ) : LiveOfferFeedResult()

    data class AvailableOffers(
        val offers: List<DriverRoutePosting>
    ) : LiveOfferFeedResult()

    companion object {
        const val EMPTY_LIVE_OFFERS_MESSAGE =
            "No live driver offers are currently available. Station reference information is available."
    }
}

/**
 * Contract for fetching genuine live driver offers from a backend service.
 */
interface LiveDriverOfferDataSource {
    fun fetchOffers(
        pickupStandQuery: String,
        destinationQuery: String
    ): LiveOfferFeedResult
}

/**
 * Default offline implementation used while no production backend is connected.
 * Never returns simulated or fabricated drivers.
 */
object OfflineDriverOfferDataSource : LiveDriverOfferDataSource {
    override fun fetchOffers(
        pickupStandQuery: String,
        destinationQuery: String
    ): LiveOfferFeedResult {
        return LiveOfferFeedResult.BackendUnavailable(
            LiveOfferFeedResult.EMPTY_LIVE_OFFERS_MESSAGE
        )
    }
}

data class PassengerFareDisplay(
    val formattedTotalFare: String,
    val partySize: Int,
    val billingScopeNote: String,
    val privateSplitNotice: String
)

data class PassengerBookingSummary(
    val pickupStand: String,
    val destination: String,
    val partySize: Int,
    val driverPickupRuleNote: String,
    val partyIsolationNote: String,
    val fareDisplay: PassengerFareDisplay?,
    val lifecycleState: BookingLifecycleState,
    val isLiveBackendConnected: Boolean
)

data class PassengerValidationOutcome(
    val isValid: Boolean,
    val errors: List<String>,
    val validatedOffer: PassengerRouteOffer? = null
) {
    val summary: String get() = errors.joinToString("\n• ", prefix = if (errors.isNotEmpty()) "• " else "")
}

object PassengerWorkflowCoordinator {

    /**
     * Filters distinct station names for the chosen railway line using a searchable station query.
     */
    fun searchStations(
        line: RailwayLine,
        stationSearchQuery: String
    ): List<String> {
        val allForLine = MumbaiStationDirectory.getRoutesByLine(line)
            .map { it.stationName }
            .distinct()
        val q = stationSearchQuery.trim()
        if (q.isEmpty()) return allForLine
        return allForLine.filter { it.contains(q, ignoreCase = true) }
    }

    /**
     * Filters designated station stands by railway line, station name, East/West side, and text search.
     */
    fun filterStands(
        line: RailwayLine,
        stationName: String? = null,
        side: StationSide? = null,
        query: String = ""
    ): List<StationStandRoute> {
        return MumbaiStationDirectory.searchRoutes(query = query, line = line).filter { stand ->
            val matchesStation = stationName.isNullOrBlank() ||
                stand.stationName.equals(stationName, ignoreCase = true)
            val matchesSide = side == null || stand.side == side
            matchesStation && matchesSide
        }
    }

    /**
     * Validates passenger inputs centrally via BookingDomain.PassengerRouteOffer.validate().
     * Explicitly validates positive integer parsing before delegating to domain rules.
     */
    fun validatePassengerInputs(
        offerId: String,
        partyId: String,
        pickupStand: String,
        destination: String,
        partySizeInput: String,
        capacityConfig: VehicleCapacityConfig?
    ): PassengerValidationOutcome {
        val trimmedPartyInput = partySizeInput.trim()
        val parsedPartyCount = trimmedPartyInput.toIntOrNull()

        if (trimmedPartyInput.isEmpty() || parsedPartyCount == null) {
            val parseError = if (trimmedPartyInput.isEmpty()) {
                "Passenger party size cannot be blank; enter a positive whole number"
            } else {
                "Passenger party size \"$trimmedPartyInput\" must be a valid positive whole number"
            }
            // Still collect route and capacity domain errors so no rule is hidden
            val dummyParty = PassengerParty(partyId = partyId, passengerCount = 0)
            val offer = PassengerRouteOffer(
                offerId = offerId,
                party = dummyParty,
                pickupPoint = pickupStand,
                destinationPoint = destination,
                capacityConfig = capacityConfig
            )
            val domainErrors = when (val res = offer.validate()) {
                is ValidationResult.Valid -> emptyList()
                is ValidationResult.Invalid -> res.errors.filterNot {
                    it == "Passenger count must be at least 1"
                }
            }
            return PassengerValidationOutcome(
                isValid = false,
                errors = listOf(parseError) + domainErrors,
                validatedOffer = null
            )
        }

        val party = PassengerParty(
            partyId = partyId,
            passengerCount = parsedPartyCount
        )
        val offer = PassengerRouteOffer(
            offerId = offerId,
            party = party,
            pickupPoint = pickupStand,
            destinationPoint = destination,
            capacityConfig = capacityConfig
        )

        return when (val result = offer.validate()) {
            is ValidationResult.Valid -> PassengerValidationOutcome(
                isValid = true,
                errors = emptyList(),
                validatedOffer = offer
            )
            is ValidationResult.Invalid -> PassengerValidationOutcome(
                isValid = false,
                errors = result.errors,
                validatedOffer = null
            )
        }
    }

    /**
     * Formats the driver's total booking fare identically regardless of party size.
     * Never multiplies totalBookingFare by partySize.
     */
    fun formatTotalBookingFareDisplay(
        totalBookingFare: Double,
        partySize: Int
    ): PassengerFareDisplay? {
        if (!totalBookingFare.isFinite() || totalBookingFare <= 0.0 || partySize <= 0) {
            return null
        }
        val formatted = String.format(Locale.US, "₹%.2f", totalBookingFare)
        return PassengerFareDisplay(
            formattedTotalFare = formatted,
            partySize = partySize,
            billingScopeNote = "Complete booking fare for your single party ($partySize passenger(s)) — NOT per seat.",
            privateSplitNotice = "Passengers arrange any private split offline; individual contributions are never tracked by the app."
        )
    }

    /**
     * Builds an immutable PassengerBookingSummary respecting all V1 locked rules.
     */
    fun buildBookingSummary(
        pickupStand: String,
        destination: String,
        partySize: Int,
        driverPosting: DriverRoutePosting?,
        agreementState: BookingLifecycleState,
        isLiveBackendConnected: Boolean
    ): PassengerBookingSummary {
        val fareDisplay = driverPosting?.let {
            formatTotalBookingFareDisplay(
                totalBookingFare = it.totalBookingFare,
                partySize = partySize
            )
        }
        return PassengerBookingSummary(
            pickupStand = pickupStand.trim(),
            destination = destination.trim(),
            partySize = partySize,
            driverPickupRuleNote = "Driver waits at the designated pickup stand; passengers travel to the stand.",
            partyIsolationNote = "One booking = one passenger party. Separate parties are never merged.",
            fareDisplay = fareDisplay,
            lifecycleState = agreementState,
            isLiveBackendConnected = isLiveBackendConnected
        )
    }

    /**
     * Confirms and locks a booking ONLY if the driver has genuinely accepted first
     * (BookingLifecycleState.DRIVER_ACCEPTED). Uses BookingAgreement.transitionTo()
     * so illegal transitions cannot be bypassed.
     */
    fun confirmBookingAfterDriverAcceptance(
        agreement: BookingAgreement
    ): Pair<BookingAgreement, ValidationResult> {
        if (agreement.state != BookingLifecycleState.DRIVER_ACCEPTED) {
            return Pair(
                agreement,
                ValidationResult.Invalid(
                    listOf(
                        "Cannot confirm booking in state ${agreement.state}. Passenger confirmation requires genuine driver acceptance (DRIVER_ACCEPTED) first."
                    )
                )
            )
        }

        val (confirmedAgreement, confirmResult) = agreement.transitionTo(BookingLifecycleState.PASSENGER_CONFIRMED)
        if (confirmResult is ValidationResult.Invalid) {
            return Pair(agreement, confirmResult)
        }

        val (lockedAgreement, lockResult) = confirmedAgreement.transitionTo(BookingLifecycleState.LOCKED)
        if (lockResult is ValidationResult.Invalid) {
            return Pair(confirmedAgreement, lockResult)
        }

        return Pair(lockedAgreement, ValidationResult.Valid)
    }
}
