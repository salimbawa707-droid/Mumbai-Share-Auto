package com.mumbaishareauto.app

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Mumbai Share Auto — Version 1 Domain Models, Lifecycle State Machine, and Fare Security Logic.
 *
 * Enforces mandatory locked product rules:
 * 1. Total fare per booking (never multiplied by passenger count).
 * 2. Passenger and driver see the exact same canonical booking fare before acceptance/confirmation.
 * 3. Fare is locked ONLY after explicit sequence: DRAFT -> OFFERED -> DRIVER_ACCEPTED -> PASSENGER_CONFIRMED -> LOCKED.
 * 4. Fare changes after locking (or silent fare mutations) are strictly rejected.
 * 5. One booking represents one passenger party in V1.
 * 6. Independent passenger parties must never be automatically merged into one booking.
 * 7. Passengers arrange private splits; app never tracks individual contributions.
 * 8. Driver waits at designated station pickup point; passengers travel there.
 * 9. Configurable vehicle capacity with validation against local transport rules.
 * 10. Explicit validation results (Valid vs Invalid) and consistent state integrity across restarts.
 */

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult() {
        val summary: String get() = errors.joinToString("; ")
    }

    val isValid: Boolean get() = this is Valid
}

enum class BookingLifecycleState {
    DRAFT,
    OFFERED,
    DRIVER_ACCEPTED,
    PASSENGER_CONFIRMED,
    LOCKED,
    DECLINED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == LOCKED || this == DECLINED || this == CANCELLED
}

/**
 * Explicit vehicle capacity configuration.
 *
 * IMPORTANT: Applicable Maharashtra/Mumbai vehicle capacity rules require independent
 * verification with local transport authorities before commercial deployment.
 * No universal default legal capacity is hardcoded or assumed.
 */
data class VehicleCapacityConfig(
    val maxPermittedPassengers: Int,
    val configurationLabel: String = "Configured Evaluation Capacity",
    val isLegallyVerified: Boolean = false,
    val isTestFixture: Boolean = false,
    val regulatoryNotice: String = "Applicable local transport regulations, vehicle seating capacity limits, and driver permits must be independently verified before production deployment."
)

data class DriverRoutePosting(
    val postingId: String,
    val driverId: String,
    val designatedPickupStand: String,
    val destinationStand: String,
    val totalBookingFare: Double,
    val availableSeats: Int,
    val capacityConfig: VehicleCapacityConfig?
) {
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        if (postingId.isBlank()) errors.add("Posting ID cannot be blank")
        if (driverId.isBlank()) errors.add("Driver ID cannot be blank")

        val trimmedPickup = designatedPickupStand.trim()
        val trimmedDest = destinationStand.trim()

        if (trimmedPickup.isEmpty()) {
            errors.add("Designated pickup stand cannot be blank")
        } else if (trimmedPickup.length < 3) {
            errors.add("Designated pickup stand must be at least 3 characters")
        }

        if (trimmedDest.isEmpty()) {
            errors.add("Destination cannot be blank")
        } else if (trimmedDest.length < 3) {
            errors.add("Destination must be at least 3 characters")
        }

        if (trimmedPickup.isNotEmpty() && trimmedDest.isNotEmpty() &&
            trimmedPickup.equals(trimmedDest, ignoreCase = true)
        ) {
            errors.add("Designated pickup stand and destination cannot be identical")
        }

        if (totalBookingFare <= 0.0 || totalBookingFare.isNaN() || totalBookingFare.isInfinite()) {
            errors.add("Total booking fare must be a strictly positive finite amount")
        }

        if (availableSeats <= 0) {
            errors.add("Available seats must be at least 1")
        }

        if (capacityConfig == null) {
            errors.add("Vehicle capacity configuration is required. Applicable capacity limits must be explicitly configured and verified before route validation.")
        } else {
            if (capacityConfig.maxPermittedPassengers <= 0) {
                errors.add("Configured vehicle capacity limit must be positive (found ${capacityConfig.maxPermittedPassengers})")
            }
            if (!capacityConfig.isLegallyVerified && !capacityConfig.isTestFixture) {
                errors.add("Vehicle capacity configuration is unverified (${capacityConfig.configurationLabel}). Production capacity-dependent operations require an independently verified legal capacity configuration from local transport authorities.")
            }
            if (capacityConfig.maxPermittedPassengers > 0 && availableSeats > capacityConfig.maxPermittedPassengers) {
                errors.add("Available seats ($availableSeats) exceeds configured capacity limit (${capacityConfig.maxPermittedPassengers}) under ${capacityConfig.configurationLabel}")
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

data class PassengerParty(
    val partyId: String,
    val passengerCount: Int,
    val contactRef: String = ""
) {
    fun validate(capacityConfig: VehicleCapacityConfig?): ValidationResult {
        val errors = mutableListOf<String>()
        if (partyId.isBlank()) errors.add("Party ID cannot be blank")
        if (passengerCount <= 0) {
            errors.add("Passenger count must be at least 1")
        }

        if (capacityConfig == null) {
            errors.add("Vehicle capacity configuration is required to validate passenger party size against legal limits.")
        } else {
            if (capacityConfig.maxPermittedPassengers <= 0) {
                errors.add("Configured vehicle capacity limit must be positive (found ${capacityConfig.maxPermittedPassengers})")
            }
            if (!capacityConfig.isLegallyVerified && !capacityConfig.isTestFixture) {
                errors.add("Vehicle capacity configuration is unverified (${capacityConfig.configurationLabel}). Production capacity-dependent operations require an independently verified legal capacity configuration from local transport authorities.")
            }
            if (capacityConfig.maxPermittedPassengers > 0 && passengerCount > capacityConfig.maxPermittedPassengers) {
                errors.add("Party size ($passengerCount) exceeds configured vehicle capacity limit (${capacityConfig.maxPermittedPassengers}) under ${capacityConfig.configurationLabel}")
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

/**
 * Passenger Route Offer submitted by a passenger party.
 */
data class PassengerRouteOffer(
    val offerId: String,
    val party: PassengerParty,
    val pickupPoint: String,
    val destinationPoint: String,
    val capacityConfig: VehicleCapacityConfig?
) {
    fun validate(): ValidationResult {
        val errors = mutableListOf<String>()
        if (offerId.isBlank()) errors.add("Offer ID cannot be blank")

        val trimmedPickup = pickupPoint.trim()
        val trimmedDest = destinationPoint.trim()

        if (trimmedPickup.isEmpty()) {
            errors.add("Pickup point cannot be blank")
        } else if (trimmedPickup.length < 3) {
            errors.add("Pickup point must be at least 3 characters")
        }

        if (trimmedDest.isEmpty()) {
            errors.add("Destination point cannot be blank")
        } else if (trimmedDest.length < 3) {
            errors.add("Destination point must be at least 3 characters")
        }

        if (trimmedPickup.isNotEmpty() && trimmedDest.isNotEmpty() &&
            trimmedPickup.equals(trimmedDest, ignoreCase = true)
        ) {
            errors.add("Pickup point and destination cannot be identical")
        }

        if (capacityConfig == null) {
            errors.add("Explicit vehicle capacity configuration is required for passenger route offer validation")
        } else {
            val partyResult = party.validate(capacityConfig)
            if (partyResult is ValidationResult.Invalid) {
                errors.addAll(partyResult.errors)
            }
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }
}

/**
 * Canonical dual-sided view proving passenger and driver observe the exact same total booking fare.
 */
data class BookingFareSnapshot(
    val bookingId: String,
    val passengerVisibleTotalFare: Double,
    val driverVisibleTotalFare: Double,
    val passengerCount: Int,
    val isFareLocked: Boolean,
    val lockedFareInr: Double?
) {
    val faresMatchExactly: Boolean
        get() = passengerVisibleTotalFare == driverVisibleTotalFare &&
            (!isFareLocked || lockedFareInr == passengerVisibleTotalFare)
}

data class BookingAgreement(
    val bookingId: String,
    val postingId: String,
    val passengerParty: PassengerParty,
    val agreedTotalFare: Double,
    val designatedStationStand: String,
    val destination: String,
    val state: BookingLifecycleState = BookingLifecycleState.DRAFT,
    val lockedFareInr: Double? = null,
    val cancellationReason: String? = null
) {
    /**
     * Invariant: Total booking fare is charged once for the party, never multiplied per seat.
     * Both passenger and driver always read this same canonical amount.
     */
    fun calculateEffectiveFare(): Double = lockedFareInr ?: agreedTotalFare

    /**
     * Returns canonical fare view for both passenger and driver.
     */
    fun fareSnapshot(): BookingFareSnapshot {
        val canonical = calculateEffectiveFare()
        return BookingFareSnapshot(
            bookingId = bookingId,
            passengerVisibleTotalFare = canonical,
            driverVisibleTotalFare = canonical,
            passengerCount = passengerParty.passengerCount,
            isFareLocked = state == BookingLifecycleState.LOCKED,
            lockedFareInr = lockedFareInr
        )
    }

    /**
     * Validates structural, fare, and lock-state consistency of the BookingAgreement.
     */
    fun validateIntegrity(): ValidationResult {
        val errors = mutableListOf<String>()
        if (bookingId.isBlank()) errors.add("Booking ID cannot be blank")
        if (postingId.isBlank()) errors.add("Posting ID cannot be blank")
        if (passengerParty.partyId.isBlank()) errors.add("Passenger party ID cannot be blank")
        if (passengerParty.passengerCount <= 0) errors.add("Passenger party count must be at least 1")

        val trimmedStand = designatedStationStand.trim()
        val trimmedDest = destination.trim()
        if (trimmedStand.length < 3) errors.add("Designated pickup stand must be at least 3 characters")
        if (trimmedDest.length < 3) errors.add("Destination must be at least 3 characters")
        if (trimmedStand.isNotEmpty() && trimmedStand.equals(trimmedDest, ignoreCase = true)) {
            errors.add("Designated pickup stand and destination cannot be identical")
        }

        if (agreedTotalFare <= 0.0 || agreedTotalFare.isNaN() || agreedTotalFare.isInfinite()) {
            errors.add("Agreed total booking fare must be a strictly positive finite amount")
        }

        if (state == BookingLifecycleState.LOCKED) {
            if (lockedFareInr == null) {
                errors.add("Inconsistent LOCKED state: lockedFareInr must be recorded upon fare lock")
            } else if (lockedFareInr != agreedTotalFare) {
                errors.add("Fare integrity violation: agreedTotalFare ($agreedTotalFare) differs from lockedFareInr ($lockedFareInr)")
            }
        } else if (lockedFareInr != null) {
            errors.add("Inconsistent state $state: lockedFareInr must be null prior to LOCKED state")
        }

        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    /**
     * Attempts to update the agreed total fare cleanly.
     * Fare changes after driver acceptance, passenger confirmation, or locking MUST NOT happen silently.
     */
    fun withProposedFareUpdate(newTotalFare: Double): Pair<BookingAgreement, ValidationResult> {
        if (newTotalFare <= 0.0 || newTotalFare.isNaN() || newTotalFare.isInfinite()) {
            return Pair(
                this,
                ValidationResult.Invalid(listOf("Proposed total booking fare must be a strictly positive finite amount"))
            )
        }
        if (state == BookingLifecycleState.LOCKED) {
            return Pair(
                this,
                ValidationResult.Invalid(listOf("Fare change rejected: booking $bookingId is LOCKED at ₹${calculateEffectiveFare()} and cannot be modified."))
            )
        }
        if (state == BookingLifecycleState.DRIVER_ACCEPTED || state == BookingLifecycleState.PASSENGER_CONFIRMED) {
            return Pair(
                this,
                ValidationResult.Invalid(listOf("Silent fare change rejected in state $state: renegotiation requires a new offer before acceptance."))
            )
        }
        if (state == BookingLifecycleState.DECLINED || state == BookingLifecycleState.CANCELLED) {
            return Pair(
                this,
                ValidationResult.Invalid(listOf("Cannot update fare in terminal state $state."))
            )
        }
        return Pair(this.copy(agreedTotalFare = newTotalFare), ValidationResult.Valid)
    }

    /**
     * Strict lifecycle transitions to prevent premature fare locking, unauthorized jumps,
     * duplicate confirmation, and corrupted state progression.
     */
    fun transitionTo(
        nextState: BookingLifecycleState,
        reason: String? = null
    ): Pair<BookingAgreement, ValidationResult> {
        if (nextState == BookingLifecycleState.PASSENGER_CONFIRMED &&
            (state == BookingLifecycleState.PASSENGER_CONFIRMED || state == BookingLifecycleState.LOCKED)
        ) {
            return Pair(
                this,
                ValidationResult.Invalid(
                    listOf("Duplicate confirmation rejected: booking $bookingId is already in state $state.")
                )
            )
        }

        if (state == BookingLifecycleState.LOCKED) {
            return Pair(
                this,
                ValidationResult.Invalid(
                    listOf("Illegal state transition from LOCKED to $nextState. Locked bookings and fares cannot transition further or be cancelled silently.")
                )
            )
        }

        if (state == BookingLifecycleState.DECLINED || state == BookingLifecycleState.CANCELLED) {
            return Pair(
                this,
                ValidationResult.Invalid(
                    listOf("Illegal state transition from terminal state $state to $nextState.")
                )
            )
        }

        val isAllowed = when (state) {
            BookingLifecycleState.DRAFT -> nextState in listOf(
                BookingLifecycleState.OFFERED,
                BookingLifecycleState.CANCELLED
            )
            BookingLifecycleState.OFFERED -> nextState in listOf(
                BookingLifecycleState.DRIVER_ACCEPTED,
                BookingLifecycleState.DECLINED,
                BookingLifecycleState.CANCELLED
            )
            BookingLifecycleState.DRIVER_ACCEPTED -> nextState in listOf(
                BookingLifecycleState.PASSENGER_CONFIRMED,
                BookingLifecycleState.CANCELLED
            )
            BookingLifecycleState.PASSENGER_CONFIRMED -> nextState in listOf(
                BookingLifecycleState.LOCKED,
                BookingLifecycleState.CANCELLED
            )
            BookingLifecycleState.LOCKED,
            BookingLifecycleState.DECLINED,
            BookingLifecycleState.CANCELLED -> false
        }

        if (!isAllowed) {
            return Pair(
                this,
                ValidationResult.Invalid(
                    listOf("Illegal state transition from $state to $nextState. Fare-locking requires driver acceptance followed by passenger confirmation.")
                )
            )
        }

        // Enforce complete structural + fare integrity before advancing along active booking lifecycle
        if (nextState != BookingLifecycleState.CANCELLED && nextState != BookingLifecycleState.DECLINED) {
            val integrity = validateIntegrity()
            if (integrity is ValidationResult.Invalid) {
                return Pair(this, integrity)
            }
        }

        val updated = when (nextState) {
            BookingLifecycleState.LOCKED -> copy(
                state = BookingLifecycleState.LOCKED,
                lockedFareInr = agreedTotalFare
            )
            BookingLifecycleState.CANCELLED -> copy(
                state = BookingLifecycleState.CANCELLED,
                cancellationReason = reason?.trim()?.takeIf { it.isNotEmpty() } ?: cancellationReason
            )
            else -> copy(state = nextState)
        }

        return Pair(updated, ValidationResult.Valid)
    }
}

object BookingDomainCoordinator {

    private val sequenceCounter = AtomicLong(0L)

    /**
     * Generates a globally unique booking ID safe across tight loops and restarts.
     */
    fun generateUniqueBookingId(prefix: String = "MSA_BK"): String {
        val cleanPrefix = prefix.trim().ifEmpty { "MSA_BK" }
        val seq = sequenceCounter.incrementAndGet()
        val entropy = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        return "${cleanPrefix}_${seq}_$entropy"
    }

    /**
     * Locked rule: Separate passenger parties must not be automatically merged into one booking.
     */
    fun canAutomaticallyMergeParties(partyA: PassengerParty, partyB: PassengerParty): Boolean {
        return false
    }

    /**
     * Evaluates a proposed booking between a passenger party and a driver posting.
     */
    fun createBookingOffer(
        bookingId: String,
        posting: DriverRoutePosting,
        party: PassengerParty
    ): Pair<BookingAgreement?, ValidationResult> {
        if (bookingId.isBlank()) {
            return Pair(null, ValidationResult.Invalid(listOf("Booking ID cannot be blank")))
        }

        val postingValidation = posting.validate()
        if (postingValidation is ValidationResult.Invalid) {
            return Pair(null, postingValidation)
        }

        val partyValidation = party.validate(posting.capacityConfig)
        if (partyValidation is ValidationResult.Invalid) {
            return Pair(null, partyValidation)
        }

        if (party.passengerCount > posting.availableSeats) {
            return Pair(
                null,
                ValidationResult.Invalid(
                    listOf("Party size (${party.passengerCount}) exceeds available seats (${posting.availableSeats}) on this route")
                )
            )
        }

        val agreement = BookingAgreement(
            bookingId = bookingId,
            postingId = posting.postingId,
            passengerParty = party,
            agreedTotalFare = posting.totalBookingFare,
            designatedStationStand = posting.designatedPickupStand,
            destination = posting.destinationStand,
            state = BookingLifecycleState.OFFERED
        )

        return Pair(agreement, ValidationResult.Valid)
    }

    fun submitDraftOffer(agreement: BookingAgreement): Pair<BookingAgreement, ValidationResult> =
        agreement.transitionTo(BookingLifecycleState.OFFERED)

    fun driverAcceptBooking(agreement: BookingAgreement): Pair<BookingAgreement, ValidationResult> =
        agreement.transitionTo(BookingLifecycleState.DRIVER_ACCEPTED)

    fun driverDeclineBooking(agreement: BookingAgreement): Pair<BookingAgreement, ValidationResult> =
        agreement.transitionTo(BookingLifecycleState.DECLINED)

    fun passengerConfirmBooking(agreement: BookingAgreement): Pair<BookingAgreement, ValidationResult> =
        agreement.transitionTo(BookingLifecycleState.PASSENGER_CONFIRMED)

    fun lockFareAfterConfirmation(agreement: BookingAgreement): Pair<BookingAgreement, ValidationResult> =
        agreement.transitionTo(BookingLifecycleState.LOCKED)

    fun cancelBooking(
        agreement: BookingAgreement,
        reason: String = "Cancelled by participant"
    ): Pair<BookingAgreement, ValidationResult> =
        agreement.transitionTo(BookingLifecycleState.CANCELLED, reason = reason)
}
