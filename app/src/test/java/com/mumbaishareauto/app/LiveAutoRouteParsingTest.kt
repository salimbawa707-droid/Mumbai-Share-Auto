package com.mumbaishareauto.app

import com.mumbaishareauto.app.data.remote.LiveAutoRoute
import com.mumbaishareauto.app.data.remote.LiveRouteTransactionEngine
import com.mumbaishareauto.app.data.remote.FirestoreActionResult
import com.mumbaishareauto.app.model.StationSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class LiveAutoRouteParsingTest {
    private fun payload(capacity: Int, available: Int): Map<String, Any?> = mapOf(
        LiveAutoRoute.FIELD_POSTING_ID to "route_1",
        LiveAutoRoute.FIELD_DRIVER_UID to "driver_1",
        LiveAutoRoute.FIELD_PICKUP_STATION to "Bandra",
        LiveAutoRoute.FIELD_PICKUP_SIDE to StationSide.EAST.name,
        LiveAutoRoute.FIELD_DESIGNATED_STAND to "Bandra East Auto Stand",
        LiveAutoRoute.FIELD_DESTINATION to "BKC",
        LiveAutoRoute.FIELD_TOTAL_BOOKING_FARE to 120.0,
        LiveAutoRoute.FIELD_TOTAL_CAPACITY_SEATS to capacity,
        LiveAutoRoute.FIELD_AVAILABLE_SEATS to available,
        LiveAutoRoute.FIELD_IS_ACTIVE to true
    )

    @Test
    fun validThreePassengerAutoInventoryIsPreserved() {
        val route = LiveAutoRoute.fromFirestoreFields("route_1", payload(3, 2))
        assertNotNull(route)
        assertEquals(3, route!!.totalCapacitySeats)
        assertEquals(2, route.availableSeats)
    }

    @Test
    fun invalidInventoryIsRejectedInsteadOfSilentlyClamped() {
        assertNull(LiveAutoRoute.fromFirestoreFields("route_1", payload(4, 4)))
        assertNull(LiveAutoRoute.fromFirestoreFields("route_1", payload(2, 3)))
        assertNull(LiveAutoRoute.fromFirestoreFields("route_1", payload(0, 0)))
    }

    @Test
    fun missingActiveStateIsNotAssumedToBeAnActiveDriverRoute() {
        val missingState = payload(3, 3).toMutableMap().apply {
            remove(LiveAutoRoute.FIELD_IS_ACTIVE)
        }
        assertNull(LiveAutoRoute.fromFirestoreFields("route_1", missingState))
    }

    @Test
    fun seatLockUsesNegotiatedWholeGroupFareInsteadOfDriversListedFare() {
        val route = LiveAutoRoute.fromFirestoreFields("route_1", payload(3, 3))!!
        val negotiatedRoute = route.copy(totalBookingFare = 77.0)

        val result = LiveRouteTransactionEngine.computeSeatLockTransition(
            currentRoute = negotiatedRoute,
            existingLock = null,
            passengerUid = "passenger_1",
            seatsRequested = 2
        )

        assert(result is FirestoreActionResult.Success)
        val lock = (result as FirestoreActionResult.Success).data.createdOrUpdatedLock
        assertEquals(77.0, lock.agreedTotalFare, 0.0)
        assertEquals(2, lock.seatsLocked)
    }

    @Test
    fun groupCannotReserveMoreThanThreePassengerSeats() {
        val route = LiveAutoRoute.fromFirestoreFields("route_1", payload(3, 3))!!
        val result = LiveRouteTransactionEngine.computeSeatLockTransition(
            currentRoute = route,
            existingLock = null,
            passengerUid = "passenger_2",
            seatsRequested = 4
        )
        assert(result is FirestoreActionResult.Failure)
    }
}
