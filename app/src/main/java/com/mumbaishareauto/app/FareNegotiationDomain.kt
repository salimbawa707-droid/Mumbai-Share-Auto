package com.mumbaishareauto.app

import java.util.UUID

/**
 * Passenger-first total-fare negotiation for one passenger group and one driver.
 *
 * Vehicle convention: one driver + up to three passengers (3+1). The fare is
 * the amount for the complete passenger group, never a per-seat amount.
 * This domain object is pure Kotlin; persistence and authorization belong to
 * the Firestore repository and security rules.
 */
enum class FareNegotiationStatus {
    OPEN,
    COUNTERED,
    AGREED,
    DECLINED,
    CANCELLED
}

enum class FareNegotiationActor {
    PASSENGER,
    DRIVER
}

enum class FareNegotiationEventType {
    PASSENGER_PROPOSED,
    DRIVER_COUNTERED,
    DRIVER_ACCEPTED,
    PASSENGER_ACCEPTED_COUNTER,
    DRIVER_DECLINED,
    PASSENGER_DECLINED_COUNTER,
    CANCELLED
}

/** Append-only audit event. Existing events are never edited or removed. */
data class FareNegotiationEvent(
    val eventId: String,
    val sequence: Int,
    val actor: FareNegotiationActor,
    val type: FareNegotiationEventType,
    val totalFareInr: Double?,
    val createdAtEpochMs: Long
)

data class FareNegotiationSession(
    val bookingId: String,
    val routeId: String,
    val passengerUid: String,
    val driverUid: String,
    val passengerCount: Int,
    val pickupStand: String,
    val destination: String,
    val events: List<FareNegotiationEvent>,
    val status: FareNegotiationStatus,
    val agreedTotalFareInr: Double? = null
) {
    val currentProposedTotalFareInr: Double?
        get() = events.lastOrNull {
            it.type == FareNegotiationEventType.PASSENGER_PROPOSED ||
                it.type == FareNegotiationEventType.DRIVER_COUNTERED
        }?.totalFareInr

    val passengerVisibleTotalFareInr: Double?
        get() = agreedTotalFareInr ?: currentProposedTotalFareInr

    val driverVisibleTotalFareInr: Double?
        get() = agreedTotalFareInr ?: currentProposedTotalFareInr

    val faresMatchForBothSides: Boolean
        get() = passengerVisibleTotalFareInr == driverVisibleTotalFareInr

    fun driverAcceptPassengerOffer(nowMs: Long): Pair<FareNegotiationSession, ValidationResult> {
        if (status != FareNegotiationStatus.OPEN) {
            return rejected("Driver can accept only an open passenger offer.")
        }
        val fare = currentProposedTotalFareInr
            ?: return rejected("No passenger fare offer is available to accept.")
        return accepted(
            event = event(FareNegotiationActor.DRIVER, FareNegotiationEventType.DRIVER_ACCEPTED, fare, nowMs),
            agreedFare = fare
        )
    }

    fun driverCounterOffer(counterTotalFareInr: Double, nowMs: Long): Pair<FareNegotiationSession, ValidationResult> {
        if (status != FareNegotiationStatus.OPEN) {
            return rejected("A driver counter-offer is allowed only while the passenger offer is open.")
        }
        if (!counterTotalFareInr.isValidFare()) {
            return rejected("Driver counter-offer must be a positive finite total fare for the whole passenger group.")
        }
        return Pair(
            copy(
                events = events + event(
                    FareNegotiationActor.DRIVER,
                    FareNegotiationEventType.DRIVER_COUNTERED,
                    counterTotalFareInr,
                    nowMs
                ),
                status = FareNegotiationStatus.COUNTERED
            ),
            ValidationResult.Valid
        )
    }

    fun passengerAcceptDriverCounter(nowMs: Long): Pair<FareNegotiationSession, ValidationResult> {
        if (status != FareNegotiationStatus.COUNTERED) {
            return rejected("Passenger can accept only a current driver counter-offer.")
        }
        val fare = currentProposedTotalFareInr
            ?: return rejected("No driver counter-offer is available to accept.")
        return accepted(
            event = event(
                FareNegotiationActor.PASSENGER,
                FareNegotiationEventType.PASSENGER_ACCEPTED_COUNTER,
                fare,
                nowMs
            ),
            agreedFare = fare
        )
    }

    fun driverDeclinePassengerOffer(nowMs: Long): Pair<FareNegotiationSession, ValidationResult> {
        if (status != FareNegotiationStatus.OPEN) {
            return rejected("Driver can decline only an open passenger offer.")
        }
        return terminal(
            FareNegotiationStatus.DECLINED,
            event(FareNegotiationActor.DRIVER, FareNegotiationEventType.DRIVER_DECLINED, null, nowMs)
        )
    }

    fun passengerDeclineDriverCounter(nowMs: Long): Pair<FareNegotiationSession, ValidationResult> {
        if (status != FareNegotiationStatus.COUNTERED) {
            return rejected("Passenger can decline only a current driver counter-offer.")
        }
        return terminal(
            FareNegotiationStatus.DECLINED,
            event(FareNegotiationActor.PASSENGER, FareNegotiationEventType.PASSENGER_DECLINED_COUNTER, null, nowMs)
        )
    }

    fun cancel(actor: FareNegotiationActor, nowMs: Long): Pair<FareNegotiationSession, ValidationResult> {
        if (status != FareNegotiationStatus.OPEN && status != FareNegotiationStatus.COUNTERED) {
            return rejected("Only an active negotiation can be cancelled.")
        }
        return terminal(
            FareNegotiationStatus.CANCELLED,
            event(actor, FareNegotiationEventType.CANCELLED, null, nowMs)
        )
    }

    private fun accepted(
        event: FareNegotiationEvent,
        agreedFare: Double
    ): Pair<FareNegotiationSession, ValidationResult> =
        Pair(
            copy(
                events = events + event,
                status = FareNegotiationStatus.AGREED,
                agreedTotalFareInr = agreedFare
            ),
            ValidationResult.Valid
        )

    private fun terminal(
        nextStatus: FareNegotiationStatus,
        event: FareNegotiationEvent
    ): Pair<FareNegotiationSession, ValidationResult> =
        Pair(copy(events = events + event, status = nextStatus), ValidationResult.Valid)

    private fun rejected(message: String): Pair<FareNegotiationSession, ValidationResult> =
        Pair(this, ValidationResult.Invalid(listOf(message)))

    private fun event(
        actor: FareNegotiationActor,
        type: FareNegotiationEventType,
        fare: Double?,
        nowMs: Long
    ) = FareNegotiationEvent(
        eventId = UUID.randomUUID().toString(),
        sequence = events.size + 1,
        actor = actor,
        type = type,
        totalFareInr = fare,
        createdAtEpochMs = nowMs
    )

    companion object {
        const val MAX_PASSENGERS = 3
        const val DRIVER_SEATS = 1
        const val VEHICLE_CONFIGURATION = "3 passengers + 1 driver"

        fun createPassengerOffer(
            bookingId: String,
            routeId: String,
            passengerUid: String,
            driverUid: String,
            passengerCount: Int,
            pickupStand: String,
            destination: String,
            proposedTotalFareInr: Double,
            nowMs: Long
        ): Pair<FareNegotiationSession?, ValidationResult> {
            val errors = mutableListOf<String>()
            if (bookingId.isBlank()) errors.add("Booking ID is required.")
            if (routeId.isBlank()) errors.add("Driver route ID is required.")
            if (passengerUid.isBlank()) errors.add("Passenger account is required.")
            if (driverUid.isBlank()) errors.add("Driver account is required.")
            if (passengerUid.isNotBlank() && passengerUid == driverUid) errors.add("A driver cannot create a passenger offer on their own route.")
            if (passengerCount !in 1..MAX_PASSENGERS) errors.add("Passenger group must contain 1 to 3 people; the driver occupies the fourth seat.")
            if (pickupStand.trim().length < 3) errors.add("Driver pickup location must be at least 3 characters.")
            if (destination.trim().length < 3) errors.add("Destination must be at least 3 characters.")
            if (pickupStand.trim().equals(destination.trim(), ignoreCase = true)) errors.add("Pickup and destination cannot be identical.")
            if (!proposedTotalFareInr.isValidFare()) errors.add("Passenger's proposed total fare must be a positive finite amount.")
            if (errors.isNotEmpty()) return Pair(null, ValidationResult.Invalid(errors))

            val firstEvent = FareNegotiationEvent(
                eventId = UUID.randomUUID().toString(),
                sequence = 1,
                actor = FareNegotiationActor.PASSENGER,
                type = FareNegotiationEventType.PASSENGER_PROPOSED,
                totalFareInr = proposedTotalFareInr,
                createdAtEpochMs = nowMs
            )
            return Pair(
                FareNegotiationSession(
                    bookingId = bookingId,
                    routeId = routeId,
                    passengerUid = passengerUid,
                    driverUid = driverUid,
                    passengerCount = passengerCount,
                    pickupStand = pickupStand.trim(),
                    destination = destination.trim(),
                    events = listOf(firstEvent),
                    status = FareNegotiationStatus.OPEN
                ),
                ValidationResult.Valid
            )
        }
    }
}

private fun Double.isValidFare(): Boolean = isFinite() && this > 0.0
