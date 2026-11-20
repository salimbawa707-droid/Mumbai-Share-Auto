package com.mumbaishareauto.app

import org.junit.Assert.*
import org.junit.Test

/**
 * Forensic unit test suite for Mumbai Share Auto Phase 1 Domain Logic.
 *
 * Verifies locked business requirements, actual production input validations,
 * capacity safety, and state transition guards in BookingDomain.kt.
 */
class BookingDomainTest {

    // Explicit test fixture capacity configuration — must NOT be reused as production config
    private val testFixtureConfig = VehicleCapacityConfig(
        maxPermittedPassengers = 3,
        configurationLabel = "Explicit Test Fixture Capacity",
        isLegallyVerified = true,
        isTestFixture = true
    )

    private fun createValidPosting(
        totalFare: Double = 90.0,
        availableSeats: Int = 3,
        pickupStand: String = "Bandra Station West Share Stand",
        destination: String = "BKC ICICI Bank Tower",
        config: VehicleCapacityConfig? = testFixtureConfig
    ): DriverRoutePosting {
        return DriverRoutePosting(
            postingId = "POST_BANDRA_001",
            driverId = "DRV_MH02_9876",
            designatedPickupStand = pickupStand,
            destinationStand = destination,
            totalBookingFare = totalFare,
            availableSeats = availableSeats,
            capacityConfig = config
        )
    }

    @Test
    fun totalBookingFare_isChargedPerBookingNotPerSeat() {
        val posting = createValidPosting(totalFare = 90.0, availableSeats = 3)
        val partyOfThree = PassengerParty(partyId = "PARTY_03", passengerCount = 3)

        val (agreement, result) = BookingDomainCoordinator.createBookingOffer(
            bookingId = "BOOKING_001",
            posting = posting,
            party = partyOfThree
        )

        assertTrue(result is ValidationResult.Valid)
        assertNotNull(agreement)

        // Locked Rule 1 & 2: Fare charged to party is total agreed fare (90.0), NOT 90.0 * 3 (270.0)
        assertEquals(90.0, agreement!!.calculateEffectiveFare(), 0.001)
        assertNotEquals(90.0 * 3, agreement.calculateEffectiveFare(), 0.001)
    }

    @Test
    fun totalFareInvariance_partySizeDoesNotAlterTotalFare() {
        val posting = createValidPosting(totalFare = 90.0, availableSeats = 3)
        
        for (paxCount in 1..3) {
            val party = PassengerParty(partyId = "PARTY_$paxCount", passengerCount = paxCount)
            val (agreement, result) = BookingDomainCoordinator.createBookingOffer(
                bookingId = "BOOKING_INVARIANCE_$paxCount",
                posting = posting,
                party = party
            )
            assertTrue("Party of $paxCount should be valid", result is ValidationResult.Valid)
            assertEquals("Total booking fare must be exactly 90.0 for $paxCount passenger(s)", 90.0, agreement!!.calculateEffectiveFare(), 0.001)
        }
    }

    @Test
    fun invalidOrNegativeFares_areRejected() {
        val zeroFarePosting = createValidPosting(totalFare = 0.0)
        val negativeFarePosting = createValidPosting(totalFare = -50.0)
        val nanFarePosting = createValidPosting(totalFare = Double.NaN)
        val infFarePosting = createValidPosting(totalFare = Double.POSITIVE_INFINITY)

        assertTrue("Zero fare should be rejected", zeroFarePosting.validate() is ValidationResult.Invalid)
        assertTrue("Negative fare should be rejected", negativeFarePosting.validate() is ValidationResult.Invalid)
        assertTrue("NaN fare should be rejected", nanFarePosting.validate() is ValidationResult.Invalid)
        assertTrue("Infinite fare should be rejected", infFarePosting.validate() is ValidationResult.Invalid)
    }

    @Test
    fun blankAndIdenticalRouteInputs_areRejectedInDriverPosting() {
        val blankPickup = createValidPosting(pickupStand = "   ")
        val blankDest = createValidPosting(destination = "")
        val identicalRoute = createValidPosting(
            pickupStand = "  Bandra West Station Stand  ",
            destination = "bandra west station stand"
        )

        val pickupVal = blankPickup.validate()
        assertTrue("Blank pickup should be rejected", pickupVal is ValidationResult.Invalid)
        assertTrue((pickupVal as ValidationResult.Invalid).summary.contains("pickup stand cannot be blank"))

        val destVal = blankDest.validate()
        assertTrue("Blank destination should be rejected", destVal is ValidationResult.Invalid)
        assertTrue((destVal as ValidationResult.Invalid).summary.contains("Destination cannot be blank"))

        val identicalVal = identicalRoute.validate()
        assertTrue("Identical pickup and destination must be rejected", identicalVal is ValidationResult.Invalid)
        assertTrue((identicalVal as ValidationResult.Invalid).summary.contains("cannot be identical"))
    }

    @Test
    fun blankAndIdenticalRouteInputs_areRejectedInPassengerOffer() {
        val party = PassengerParty(partyId = "PARTY_OFFER", passengerCount = 2)

        val blankPickupOffer = PassengerRouteOffer(
            offerId = "OFF_1",
            party = party,
            pickupPoint = "   ",
            destinationPoint = "BKC",
            capacityConfig = testFixtureConfig
        )
        val blankPickupVal = blankPickupOffer.validate()
        assertTrue(blankPickupVal is ValidationResult.Invalid)
        assertTrue((blankPickupVal as ValidationResult.Invalid).summary.contains("Pickup point cannot be blank"))

        val blankDestOffer = PassengerRouteOffer(
            offerId = "OFF_2",
            party = party,
            pickupPoint = "Bandra Station",
            destinationPoint = " ",
            capacityConfig = testFixtureConfig
        )
        val blankDestVal = blankDestOffer.validate()
        assertTrue(blankDestVal is ValidationResult.Invalid)
        assertTrue((blankDestVal as ValidationResult.Invalid).summary.contains("Destination point cannot be blank"))

        val identicalOffer = PassengerRouteOffer(
            offerId = "OFF_3",
            party = party,
            pickupPoint = "  Andheri East Stand  ",
            destinationPoint = "andheri east stand",
            capacityConfig = testFixtureConfig
        )
        val identicalVal = identicalOffer.validate()
        assertTrue(identicalVal is ValidationResult.Invalid)
        assertTrue((identicalVal as ValidationResult.Invalid).summary.contains("cannot be identical"))
    }

    @Test
    fun missingCapacityConfiguration_isRejectedWithoutAssumingDefaultLegalCapacity() {
        // Driver posting with null capacity configuration
        val unconfiguredPosting = createValidPosting(config = null)
        val postingVal = unconfiguredPosting.validate()
        assertTrue("Posting without explicit capacity configuration must be rejected", postingVal is ValidationResult.Invalid)
        assertTrue((postingVal as ValidationResult.Invalid).summary.contains("capacity configuration is required"))

        // Passenger party validation with null capacity configuration
        val party = PassengerParty(partyId = "PARTY_NOCONFIG", passengerCount = 2)
        val partyVal = party.validate(null)
        assertTrue("Party without explicit capacity configuration must be rejected", partyVal is ValidationResult.Invalid)
        assertTrue((partyVal as ValidationResult.Invalid).summary.contains("capacity configuration is required"))
    }

    @Test
    fun zeroOrNegativeCapacityConfiguration_isRejected() {
        val zeroCapacityConfig = VehicleCapacityConfig(
            maxPermittedPassengers = 0,
            configurationLabel = "Zero Capacity Test",
            isLegallyVerified = true,
            isTestFixture = true
        )
        val negativeCapacityConfig = VehicleCapacityConfig(
            maxPermittedPassengers = -3,
            configurationLabel = "Negative Capacity Test",
            isLegallyVerified = true,
            isTestFixture = true
        )

        val postingWithZero = createValidPosting(config = zeroCapacityConfig)
        val postingWithNeg = createValidPosting(config = negativeCapacityConfig)

        val zeroVal = postingWithZero.validate()
        assertTrue("Zero capacity limit must be rejected", zeroVal is ValidationResult.Invalid)
        assertTrue((zeroVal as ValidationResult.Invalid).summary.contains("must be positive"))

        val negVal = postingWithNeg.validate()
        assertTrue("Negative capacity limit must be rejected", negVal is ValidationResult.Invalid)
        assertTrue((negVal as ValidationResult.Invalid).summary.contains("must be positive"))
    }

    @Test
    fun unverifiedCapacityConfiguration_blocksProductionOperationsWithHonestExplanation() {
        val unverifiedConfig = VehicleCapacityConfig(
            maxPermittedPassengers = 3,
            configurationLabel = "Unverified Local Limit",
            isLegallyVerified = false,
            isTestFixture = false
        )

        val posting = createValidPosting(config = unverifiedConfig)
        val result = posting.validate()

        assertTrue("Unverified capacity must block production route operations", result is ValidationResult.Invalid)
        assertTrue(
            "Validation result must explain unverified capacity honestly",
            (result as ValidationResult.Invalid).summary.contains("unverified")
        )

        val party = PassengerParty(partyId = "PARTY_UNVERIFIED", passengerCount = 2)
        val partyResult = party.validate(unverifiedConfig)
        assertTrue("Unverified capacity must block production passenger validation", partyResult is ValidationResult.Invalid)
        assertTrue((partyResult as ValidationResult.Invalid).summary.contains("unverified"))
    }

    @Test
    fun unverifiedCapacityConfiguration_stillEnforcesConfiguredUpperSeatBounds() {
        val unverifiedConfig = VehicleCapacityConfig(
            maxPermittedPassengers = 3,
            configurationLabel = "Evaluation Limit (Subject to Official RTO Verification)",
            isLegallyVerified = false,
            isTestFixture = false
        )

        val overSeatPosting = createValidPosting(availableSeats = 5, config = unverifiedConfig)
        val postingVal = overSeatPosting.validate() as ValidationResult.Invalid
        assertTrue(postingVal.summary.contains("unverified"))
        assertTrue(postingVal.summary.contains("exceeds configured capacity limit"))

        val overParty = PassengerParty(partyId = "PARTY_OVER", passengerCount = 5)
        val partyVal = overParty.validate(unverifiedConfig) as ValidationResult.Invalid
        assertTrue(partyVal.summary.contains("unverified"))
        assertTrue(partyVal.summary.contains("exceeds configured vehicle capacity limit"))
    }

    @Test
    fun invalidSeatCounts_and_CapacityExceedance_areRejected() {
        val zeroSeatsPosting = createValidPosting(availableSeats = 0)
        val negSeatsPosting = createValidPosting(availableSeats = -2)
        val overCapacityPosting = createValidPosting(availableSeats = 4, config = testFixtureConfig)

        assertTrue(zeroSeatsPosting.validate() is ValidationResult.Invalid)
        assertTrue(negSeatsPosting.validate() is ValidationResult.Invalid)

        val overVal = overCapacityPosting.validate()
        assertTrue(overVal is ValidationResult.Invalid)
        assertTrue((overVal as ValidationResult.Invalid).summary.contains("exceeds configured capacity limit"))

        // Passenger party exceeding available seats
        val postingWithTwoSeats = createValidPosting(availableSeats = 2)
        val partyOfThree = PassengerParty(partyId = "PARTY_3", passengerCount = 3)
        val (agreement, result) = BookingDomainCoordinator.createBookingOffer(
            bookingId = "BOOK_EXCEED",
            posting = postingWithTwoSeats,
            party = partyOfThree
        )
        assertNull(agreement)
        assertTrue(result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).summary.contains("exceeds available seats"))
    }

    @Test
    fun fareLockingRules_cannotBeBypassedByInvalidStateTransitions() {
        val posting = createValidPosting()
        val party = PassengerParty(partyId = "PARTY_VALID", passengerCount = 2)
        val (agreement, _) = BookingDomainCoordinator.createBookingOffer(
            bookingId = "BOOKING_TRANSITION",
            posting = posting,
            party = party
        )
        assertNotNull(agreement)
        assertEquals(BookingLifecycleState.OFFERED, agreement!!.state)

        // Attempting to bypass driver acceptance and directly lock fare
        val (bypassedAgreement, bypassResult) = agreement.transitionTo(BookingLifecycleState.LOCKED)
        assertTrue("Direct transition from OFFERED to LOCKED must be rejected", bypassResult is ValidationResult.Invalid)
        assertEquals("State must remain OFFERED on invalid transition", BookingLifecycleState.OFFERED, bypassedAgreement.state)

        // Valid sequential progression
        val (acceptedAgreement, acceptedResult) = agreement.transitionTo(BookingLifecycleState.DRIVER_ACCEPTED)
        assertTrue(acceptedResult is ValidationResult.Valid)
        assertEquals(BookingLifecycleState.DRIVER_ACCEPTED, acceptedAgreement.state)

        val (confirmedAgreement, confirmedResult) = acceptedAgreement.transitionTo(BookingLifecycleState.PASSENGER_CONFIRMED)
        assertTrue(confirmedResult is ValidationResult.Valid)
        assertEquals(BookingLifecycleState.PASSENGER_CONFIRMED, confirmedAgreement.state)

        val (lockedAgreement, lockedResult) = confirmedAgreement.transitionTo(BookingLifecycleState.LOCKED)
        assertTrue(lockedResult is ValidationResult.Valid)
        assertEquals(BookingLifecycleState.LOCKED, lockedAgreement.state)
    }

    @Test
    fun independentBookingParties_areNotAutomaticallyMerged() {
        val partyA = PassengerParty(partyId = "PARTY_A", passengerCount = 1)
        val partyB = PassengerParty(partyId = "PARTY_B", passengerCount = 1)

        val canMerge = BookingDomainCoordinator.canAutomaticallyMergeParties(partyA, partyB)

        // Locked Rule 5: Version 1 prohibits automatic merging of distinct passenger parties
        assertFalse("Automatic party merging must evaluate to false in Version 1", canMerge)
    }

    @Test
    fun invalidInput_producesValidationResultWithoutCrashing() {
        val zeroPassengerParty = PassengerParty(partyId = "P0", passengerCount = 0)
        val negativeSeatsPosting = createValidPosting(availableSeats = -1)

        val partyResult = zeroPassengerParty.validate(testFixtureConfig)
        val postingResult = negativeSeatsPosting.validate()

        assertFalse(partyResult.isValid)
        assertFalse(postingResult.isValid)
    }
}
