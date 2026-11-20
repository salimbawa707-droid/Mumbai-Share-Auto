package com.mumbaishareauto.app

import com.mumbaishareauto.app.model.RailwayLine
import com.mumbaishareauto.app.model.StationSide
import com.mumbaishareauto.app.model.StationStandRoute
import java.util.Locale

/**
 * Pure domain workflow, validation, and fare-display coordinator for the
 * Phase 3 Driver Experience.
 *
 * Enforces locked Version 1 rules:
 * - Driver posts ONE total booking fare for the complete booking (never per seat).
 * - Rejects empty routes, identical pickup & destination, blank/zero/negative/NaN/infinite fares,
 *   invalid passenger capacities, and unverified legal capacities in production operations.
 * - A local button click must NEVER pretend to send, accept, or reject a real booking request.
 * - Offline backend unavailability is always disclosed honestly.
 */
enum class DriverDashboardTab(val label: String) {
    MY_ACTIVE_ROUTE("My Active Route"),
    BOOKING_REQUESTS("Booking Requests"),
    ACCEPTED_BOOKINGS("Accepted Bookings"),
    COMPLETED_BOOKINGS("Completed Bookings"),
    CANCELLED_BOOKINGS("Cancelled Bookings")
}

sealed class DriverRequestsFeedResult {
    data class BackendUnavailable(
        val message: String = DRIVER_BACKEND_UNAVAILABLE_MESSAGE
    ) : DriverRequestsFeedResult()

    data class AvailableRequests(
        val pendingRequests: List<BookingAgreement>,
        val acceptedBookings: List<BookingAgreement>,
        val completedBookings: List<BookingAgreement>,
        val cancelledBookings: List<BookingAgreement>
    ) : DriverRequestsFeedResult()

    companion object {
        const val DRIVER_BACKEND_UNAVAILABLE_MESSAGE =
            "Backend integration is unavailable. Live route publishing and real-time booking requests cannot be sent or received."
    }
}

/**
 * Contract for genuine backend-backed driver booking requests and route feed.
 */
interface DriverBookingDataSource {
    fun fetchDriverBookings(): DriverRequestsFeedResult
}

/**
 * Default offline data source used when no live backend integration exists.
 * Never fabricates incoming booking requests or active trips.
 */
object OfflineDriverBookingDataSource : DriverBookingDataSource {
    override fun fetchDriverBookings(): DriverRequestsFeedResult {
        return DriverRequestsFeedResult.BackendUnavailable(
            DriverRequestsFeedResult.DRIVER_BACKEND_UNAVAILABLE_MESSAGE
        )
    }
}

data class DriverFarePreview(
    val totalFareInr: Double,
    val formattedTotalFare: String,
    val capacitySeats: Int,
    val farePolicyNote: String
)

data class DriverRouteValidationOutcome(
    val isValid: Boolean,
    val errors: List<String>,
    val validatedPosting: DriverRoutePosting? = null,
    val pickupStation: String = "",
    val pickupSide: StationSide? = null,
    val viaPoints: List<String> = emptyList(),
    val farePreview: DriverFarePreview? = null,
    val verificationStatusLabel: String = ""
) {
    val summary: String
        get() = errors.joinToString("\n• ", prefix = if (errors.isNotEmpty()) "• " else "")
}

object DriverWorkflowCoordinator {

    /**
     * Filters distinct stations for a railway line by search query for the driver route form.
     */
    fun searchStations(
        line: RailwayLine,
        query: String
    ): List<String> = PassengerWorkflowCoordinator.searchStations(line, query)

    /**
     * Filters reference stands for a chosen station & East/West side.
     */
    fun filterStandsForDriver(
        line: RailwayLine,
        stationName: String?,
        side: StationSide?
    ): List<StationStandRoute> = PassengerWorkflowCoordinator.filterStands(
        line = line,
        stationName = stationName,
        side = side,
        query = ""
    )

    /**
     * Parses optional comma/semicolon-separated via points cleanly.
     */
    fun parseViaPoints(rawViaPoints: String): List<String> {
        return rawViaPoints
            .split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    /**
     * Formats ONE complete booking fare for driver display.
     * Example: ₹120 remains ₹120 for 2 or 3 passengers — NEVER multiplied or divided per seat.
     */
    fun formatDriverTotalFarePreview(
        totalBookingFare: Double,
        availableCapacity: Int
    ): DriverFarePreview? {
        if (!totalBookingFare.isFinite() || totalBookingFare <= 0.0 || availableCapacity <= 0) {
            return null
        }
        val formatted = if (totalBookingFare % 1.0 == 0.0) {
            String.format(Locale.US, "₹%.0f", totalBookingFare)
        } else {
            String.format(Locale.US, "₹%.2f", totalBookingFare)
        }
        return DriverFarePreview(
            totalFareInr = totalBookingFare,
            formattedTotalFare = formatted,
            capacitySeats = availableCapacity,
            farePolicyNote = "Total Booking Fare: $formatted for the complete booking. Remains $formatted even for 2 or $availableCapacity passengers — never per-seat."
        )
    }

    /**
     * Describes the current legal verification status of a vehicle capacity config honestly.
     */
    fun getVehicleCapacityVerificationStatus(capacityConfig: VehicleCapacityConfig?): String {
        return when {
            capacityConfig == null ->
                "Missing — Explicit vehicle capacity configuration is required"
            capacityConfig.isLegallyVerified ->
                "Verified Legal Capacity (${capacityConfig.maxPermittedPassengers} pax) — ${capacityConfig.configurationLabel}"
            capacityConfig.isTestFixture ->
                "Test Fixture Only (${capacityConfig.maxPermittedPassengers} pax) — Not legally verified for production"
            else ->
                "Unverified Legal Capacity — Blocked for production operations (${capacityConfig.configurationLabel})"
        }
    }

    /**
     * Centralized validation for all 8 Driver Route Posting Form fields.
     * Delegates core fare, route, and capacity safety checks directly to BookingDomain.DriverRoutePosting.
     */
    fun validateDriverRoutePostingInput(
        postingId: String,
        driverId: String,
        pickupStation: String,
        pickupSide: StationSide?,
        designatedStand: String,
        destination: String,
        viaPointsInput: String,
        totalFareInput: String,
        availableCapacityInput: String,
        capacityConfig: VehicleCapacityConfig?
    ): DriverRouteValidationOutcome {
        val errors = mutableListOf<String>()
        val trimmedStation = pickupStation.trim()
        val trimmedStand = designatedStand.trim()
        val trimmedDest = destination.trim()
        val trimmedFare = totalFareInput.trim()
        val trimmedCapacity = availableCapacityInput.trim()
        val parsedViaPoints = parseViaPoints(viaPointsInput)
        val verificationStatus = getVehicleCapacityVerificationStatus(capacityConfig)

        if (trimmedStation.isEmpty()) {
            errors.add("Pickup railway station is required")
        }
        if (pickupSide == null) {
            errors.add("Pickup station side (East or West) must be selected")
        }

        val parsedFare: Double = when {
            trimmedFare.isEmpty() -> {
                errors.add("Total booking fare cannot be blank")
                Double.NaN
            }
            trimmedFare.equals("NaN", ignoreCase = true) -> {
                errors.add("Total booking fare cannot be NaN")
                Double.NaN
            }
            trimmedFare.equals("Infinity", ignoreCase = true) ||
                trimmedFare.equals("-Infinity", ignoreCase = true) ||
                trimmedFare.equals("+Infinity", ignoreCase = true) -> {
                errors.add("Total booking fare must be a finite number")
                Double.POSITIVE_INFINITY
            }
            else -> {
                val value = trimmedFare.toDoubleOrNull()
                if (value == null) {
                    errors.add("Total booking fare \"$trimmedFare\" must be a valid positive number")
                    Double.NaN
                } else {
                    value
                }
            }
        }

        val parsedCapacity: Int = when {
            trimmedCapacity.isEmpty() -> {
                errors.add("Available passenger capacity cannot be blank")
                0
            }
            else -> {
                val value = trimmedCapacity.toIntOrNull()
                if (value == null) {
                    errors.add("Available passenger capacity \"$trimmedCapacity\" must be a valid positive whole number")
                    0
                } else {
                    value
                }
            }
        }

        val candidatePosting = DriverRoutePosting(
            postingId = postingId,
            driverId = driverId,
            designatedPickupStand = trimmedStand,
            destinationStand = trimmedDest,
            totalBookingFare = parsedFare,
            availableSeats = parsedCapacity,
            capacityConfig = capacityConfig
        )

        val domainResult = candidatePosting.validate()
        if (domainResult is ValidationResult.Invalid) {
            domainResult.errors.forEach { domainErr ->
                val isDuplicateParseMessage =
                    (trimmedFare.isEmpty() && domainErr.contains("Total booking fare must be a strictly positive")) ||
                        (trimmedFare.toDoubleOrNull() == null && domainErr.contains("Total booking fare must be a strictly positive")) ||
                        (trimmedCapacity.toIntOrNull() == null && domainErr == "Available seats must be at least 1")
                if (!isDuplicateParseMessage && !errors.contains(domainErr)) {
                    errors.add(domainErr)
                }
            }
        }

        // Prevent destination from matching the station-prefixed stand label as well
        if (trimmedStation.isNotEmpty() && pickupSide != null && trimmedStand.isNotEmpty() && trimmedDest.isNotEmpty()) {
            val fullStationStand = "$trimmedStation (${pickupSide.label}) $trimmedStand"
            if (trimmedDest.equals(fullStationStand, ignoreCase = true)) {
                val msg = "Designated pickup stand and destination cannot be identical"
                if (!errors.contains(msg)) errors.add(msg)
            }
        }

        val preview = if (parsedFare.isFinite() && parsedFare > 0.0) {
            formatDriverTotalFarePreview(
                totalBookingFare = parsedFare,
                availableCapacity = parsedCapacity.coerceAtLeast(1)
            )
        } else {
            null
        }

        return if (errors.isEmpty() && domainResult is ValidationResult.Valid) {
            DriverRouteValidationOutcome(
                isValid = true,
                errors = emptyList(),
                validatedPosting = candidatePosting,
                pickupStation = trimmedStation,
                pickupSide = pickupSide,
                viaPoints = parsedViaPoints,
                farePreview = preview,
                verificationStatusLabel = verificationStatus
            )
        } else {
            DriverRouteValidationOutcome(
                isValid = false,
                errors = errors,
                validatedPosting = null,
                pickupStation = trimmedStation,
                pickupSide = pickupSide,
                viaPoints = parsedViaPoints,
                farePreview = preview,
                verificationStatusLabel = verificationStatus
            )
        }
    }

    /**
     * Evaluates a driver's accept or reject decision on an incoming passenger booking request.
     *
     * Rules:
     * - A local button click must NEVER pretend to accept/reject a booking request when backend
     *   integration is unavailable.
     * - Only genuine requests in BookingLifecycleState.OFFERED can transition to DRIVER_ACCEPTED
     *   or DECLINED via BookingAgreement.transitionTo().
     */
    fun respondToGenuineBookingRequest(
        agreement: BookingAgreement,
        accept: Boolean,
        isBackendConnected: Boolean
    ): Pair<BookingAgreement, ValidationResult> {
        if (!isBackendConnected) {
            return Pair(
                agreement,
                ValidationResult.Invalid(
                    listOf(
                        "Cannot ${if (accept) "accept" else "reject"} booking request offline: backend integration is unavailable and a local action must not simulate real bookings."
                    )
                )
            )
        }

        if (agreement.state != BookingLifecycleState.OFFERED) {
            return Pair(
                agreement,
                ValidationResult.Invalid(
                    listOf(
                        "Cannot respond to booking ${agreement.bookingId} in state ${agreement.state}; driver decision applies only to OFFERED requests."
                    )
                )
            )
        }

        val targetState = if (accept) {
            BookingLifecycleState.DRIVER_ACCEPTED
        } else {
            BookingLifecycleState.DECLINED
        }

        return agreement.transitionTo(targetState)
    }
}
