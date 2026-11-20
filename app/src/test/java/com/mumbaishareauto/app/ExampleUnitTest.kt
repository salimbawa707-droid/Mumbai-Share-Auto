package com.mumbaishareauto.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying production models in BookingDomain.kt.
 */
class ExampleUnitTest {

    private val testCapacityConfig = VehicleCapacityConfig(
        maxPermittedPassengers = 3,
        configurationLabel = "Unit Test Fixture Capacity",
        isLegallyVerified = true,
        isTestFixture = true
    )

    @Test
    fun productionModel_fareIsForTotalBookingNotPerSeat() {
        val posting = DriverRoutePosting(
            postingId = "POST_001",
            driverId = "DRV_BANDRA_01",
            designatedPickupStand = "Bandra Station West Share Stand",
            destinationStand = "BKC Diamond Bourse",
            totalBookingFare = 90.0,
            availableSeats = 3,
            capacityConfig = testCapacityConfig
        )

        val party = PassengerParty(
            partyId = "PARTY_01",
            passengerCount = 2
        )

        val (agreement, result) = BookingDomainCoordinator.createBookingOffer(
            bookingId = "BOOK_001",
            posting = posting,
            party = party
        )

        assertTrue("Offer creation must succeed for valid parameters", result is ValidationResult.Valid)
        assertEquals(90.0, agreement!!.calculateEffectiveFare(), 0.001)
    }

    @Test
    fun productionModel_separatePartiesAreNotMerged() {
        val partyA = PassengerParty(partyId = "PARTY_A", passengerCount = 1)
        val partyB = PassengerParty(partyId = "PARTY_B", passengerCount = 2)

        val canMerge = BookingDomainCoordinator.canAutomaticallyMergeParties(partyA, partyB)
        assertFalse("Automatic merging must be prohibited in Version 1", canMerge)
    }
}
