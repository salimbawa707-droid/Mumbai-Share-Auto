package com.mumbaishareauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FareNegotiationDomainTest {
    private fun offer(passengers: Int = 3, fare: Double = 150.0): FareNegotiationSession {
        val (session, result) = FareNegotiationSession.createPassengerOffer(
            bookingId = "BOOK_1",
            routeId = "ROUTE_1",
            passengerUid = "PASSENGER_1",
            driverUid = "DRIVER_1",
            passengerCount = passengers,
            pickupStand = "Bandra East Stand",
            destination = "BKC",
            proposedTotalFareInr = fare,
            nowMs = 100L
        )
        assertTrue(result is ValidationResult.Valid)
        return requireNotNull(session)
    }

    @Test
    fun passengerFareIsWholeGroupTotalAndBothSidesSeeSameAmount() {
        val session = offer(passengers = 3, fare = 150.0)
        assertEquals(150.0, session.passengerVisibleTotalFareInr!!, 0.0)
        assertEquals(150.0, session.driverVisibleTotalFareInr!!, 0.0)
        assertTrue(session.faresMatchForBothSides)
        assertEquals(3, session.passengerCount)
        assertEquals(1, FareNegotiationSession.DRIVER_SEATS)
    }

    @Test
    fun driverCanAcceptPassengerTotalFareAndAgreementLocksThatExactAmount() {
        val (agreed, result) = offer().driverAcceptPassengerOffer(nowMs = 200L)
        assertTrue(result is ValidationResult.Valid)
        assertEquals(FareNegotiationStatus.AGREED, agreed.status)
        assertEquals(150.0, agreed.agreedTotalFareInr!!, 0.0)
        assertEquals(2, agreed.events.size)
        assertEquals(2, agreed.events.last().sequence)
    }

    @Test
    fun passengerMustAcceptDriverCounterOfferBeforeAgreement() {
        val (countered, counterResult) = offer().driverCounterOffer(180.0, nowMs = 200L)
        assertTrue(counterResult is ValidationResult.Valid)
        assertEquals(FareNegotiationStatus.COUNTERED, countered.status)
        assertEquals(180.0, countered.passengerVisibleTotalFareInr!!, 0.0)
        assertEquals(180.0, countered.driverVisibleTotalFareInr!!, 0.0)
        assertTrue(countered.faresMatchForBothSides)

        val (agreed, acceptResult) = countered.passengerAcceptDriverCounter(nowMs = 300L)
        assertTrue(acceptResult is ValidationResult.Valid)
        assertEquals(FareNegotiationStatus.AGREED, agreed.status)
        assertEquals(180.0, agreed.agreedTotalFareInr!!, 0.0)
        assertEquals(
            listOf(FareNegotiationEventType.PASSENGER_PROPOSED, FareNegotiationEventType.DRIVER_COUNTERED, FareNegotiationEventType.PASSENGER_ACCEPTED_COUNTER),
            agreed.events.map { it.type }
        )
    }

    @Test
    fun passengerGroupCannotExceedThreeAndDriverIsSeparateFourthSeat() {
        for (count in listOf(0, 4, 5)) {
            val (session, result) = FareNegotiationSession.createPassengerOffer(
                "BOOK_$count", "ROUTE_1", "PAX", "DRIVER", count,
                "Bandra East Stand", "BKC", 100.0, 1L
            )
            assertNull(session)
            assertTrue(result is ValidationResult.Invalid)
        }
        assertEquals("3 passengers + 1 driver", FareNegotiationSession.VEHICLE_CONFIGURATION)
        assertNotNull(offer(passengers = 3))
    }

    @Test
    fun invalidFaresAndSelfBookingAreRejected() {
        for (fare in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val (session, result) = FareNegotiationSession.createPassengerOffer(
                "BOOK_BAD", "ROUTE_1", "PAX", "DRIVER", 2,
                "Bandra East Stand", "BKC", fare, 1L
            )
            assertNull(session)
            assertTrue(result is ValidationResult.Invalid)
        }
        val (selfSession, selfResult) = FareNegotiationSession.createPassengerOffer(
            "BOOK_SELF", "ROUTE_1", "SAME_UID", "SAME_UID", 1,
            "Bandra East Stand", "BKC", 100.0, 1L
        )
        assertNull(selfSession)
        assertTrue(selfResult is ValidationResult.Invalid)
    }

    @Test
    fun counterOfferCannotBeAcceptedWithoutPassengerApprovalAndHistoryIsAppendOnly() {
        val (countered, result) = offer().driverCounterOffer(175.0, 200L)
        assertTrue(result is ValidationResult.Valid)
        val (unchanged, acceptResult) = countered.driverAcceptPassengerOffer(300L)
        assertTrue(acceptResult is ValidationResult.Invalid)
        assertEquals(countered, unchanged)
        assertEquals(2, countered.events.size)
        assertFalse(countered.status == FareNegotiationStatus.AGREED)
    }

    @Test
    fun decliningCounterOfferClosesNegotiationWithoutLockingFare() {
        val (countered, _) = offer().driverCounterOffer(175.0, 200L)
        val (declined, result) = countered.passengerDeclineDriverCounter(300L)
        assertTrue(result is ValidationResult.Valid)
        assertEquals(FareNegotiationStatus.DECLINED, declined.status)
        assertNull(declined.agreedTotalFareInr)
        assertEquals(3, declined.events.size)
    }
}
