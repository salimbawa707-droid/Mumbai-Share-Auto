package com.mumbaishareauto.app

import com.mumbaishareauto.app.model.RailwayLine
import com.mumbaishareauto.app.model.StationSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Phase 2 unit test suite covering the Passenger Experience workflow,
 * searchable station & East/West stand filtering, party-size & route validations,
 * flat total-booking fare invariance, honest offline empty state, and strict
 * driver-accepted booking confirmation transitions.
 */
class PassengerWorkflowTest {

    private val verifiedTestCapacityConfig = VehicleCapacityConfig(
        maxPermittedPassengers = 3,
        configurationLabel = "Explicit Test Fixture Capacity",
        isLegallyVerified = true,
        isTestFixture = true
    )

    private val unverifiedProductionConfig = VehicleCapacityConfig(
        maxPermittedPassengers = 3,
        configurationLabel = "Evaluation Limit (Subject to Official RTO Verification)",
        isLegallyVerified = false,
        isTestFixture = false
    )

    @Test
    fun searchStations_filtersByLineAndCaseInsensitiveQuery() {
        val westernAll = PassengerWorkflowCoordinator.searchStations(RailwayLine.WESTERN, "")
        assertTrue(westernAll.contains("Bandra"))
        assertTrue(westernAll.contains("Andheri"))
        assertFalse(westernAll.contains("Thane"))

        val bandraQuery = PassengerWorkflowCoordinator.searchStations(RailwayLine.WESTERN, "ban")
        assertEquals(listOf("Bandra"), bandraQuery)

        val centralQuery = PassengerWorkflowCoordinator.searchStations(RailwayLine.CENTRAL, "thane")
        assertEquals(listOf("Thane"), centralQuery)

        val noMatch = PassengerWorkflowCoordinator.searchStations(RailwayLine.ALL, "NonExistentHub99")
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun filterStands_filtersByStationEastWestSideAndDestinationQuery() {
        val andheriEast = PassengerWorkflowCoordinator.filterStands(
            line = RailwayLine.WESTERN,
            stationName = "Andheri",
            side = StationSide.EAST
        )
        assertTrue(andheriEast.isNotEmpty())
        assertTrue(andheriEast.all { it.stationName == "Andheri" && it.side == StationSide.EAST })

        val andheriWest = PassengerWorkflowCoordinator.filterStands(
            line = RailwayLine.WESTERN,
            stationName = "Andheri",
            side = StationSide.WEST
        )
        assertTrue(andheriWest.isNotEmpty())
        assertTrue(andheriWest.all { it.stationName == "Andheri" && it.side == StationSide.WEST })

        val bkcFromBandra = PassengerWorkflowCoordinator.filterStands(
            line = RailwayLine.WESTERN,
            stationName = "Bandra",
            side = StationSide.EAST,
            query = "BKC"
        )
        assertTrue(bkcFromBandra.isNotEmpty())
        assertTrue(bkcFromBandra.all { it.destination.contains("BKC", ignoreCase = true) })
    }

    @Test
    fun validatePassengerInputs_rejectsBlankShortAndIdenticalRoutes() {
        val blankPickup = PassengerWorkflowCoordinator.validatePassengerInputs(
            offerId = "OFF_BLANK_PICKUP",
            partyId = "PARTY_1",
            pickupStand = "   ",
            destination = "BKC Diamond Bourse",
            partySizeInput = "2",
            capacityConfig = verifiedTestCapacityConfig
        )
        assertFalse(blankPickup.isValid)
        assertNull(blankPickup.validatedOffer)
        assertTrue(blankPickup.errors.any { it.contains("Pickup point cannot be blank") })

        val shortDest = PassengerWorkflowCoordinator.validatePassengerInputs(
            offerId = "OFF_SHORT_DEST",
            partyId = "PARTY_1",
            pickupStand = "Bandra East Stand",
            destination = "BK",
            partySizeInput = "2",
            capacityConfig = verifiedTestCapacityConfig
        )
        assertFalse(shortDest.isValid)
        assertTrue(shortDest.errors.any { it.contains("at least 3 characters") })

        val identicalRoute = PassengerWorkflowCoordinator.validatePassengerInputs(
            offerId = "OFF_SAME_ROUTE",
            partyId = "PARTY_1",
            pickupStand = "  Andheri West Stand  ",
            destination = "andheri west stand",
            partySizeInput = "2",
            capacityConfig = verifiedTestCapacityConfig
        )
        assertFalse(identicalRoute.isValid)
        assertTrue(identicalRoute.errors.any { it.contains("cannot be identical") })
    }

    @Test
    fun validatePassengerInputs_rejectsBlankNonIntegerZeroNegativeAndOverCapacityPartySize() {
        val invalidInputs = listOf("", "   ", "two", "1.5", "0", "-2")
        invalidInputs.forEach { rawCount ->
            val outcome = PassengerWorkflowCoordinator.validatePassengerInputs(
                offerId = "OFF_PAX_$rawCount",
                partyId = "PARTY_BAD",
                pickupStand = "Bandra West Station Stand",
                destination = "Carter Road",
                partySizeInput = rawCount,
                capacityConfig = verifiedTestCapacityConfig
            )
            assertFalse("Input '$rawCount' must be rejected", outcome.isValid)
            assertNull(outcome.validatedOffer)
        }

        val overCapacity = PassengerWorkflowCoordinator.validatePassengerInputs(
            offerId = "OFF_OVER_CAP",
            partyId = "PARTY_OVER",
            pickupStand = "Bandra West Station Stand",
            destination = "Carter Road",
            partySizeInput = "4",
            capacityConfig = verifiedTestCapacityConfig
        )
        assertFalse(overCapacity.isValid)
        assertTrue(overCapacity.errors.any { it.contains("exceeds configured vehicle capacity limit") })
    }

    @Test
    fun validatePassengerInputs_requiresVerifiedCapacityAndRejectsNullOrUnverifiedConfig() {
        val nullConfigOutcome = PassengerWorkflowCoordinator.validatePassengerInputs(
            offerId = "OFF_NULL_CFG",
            partyId = "PARTY_1",
            pickupStand = "Dadar East Stand",
            destination = "Wadala IMAX",
            partySizeInput = "2",
            capacityConfig = null
        )
        assertFalse(nullConfigOutcome.isValid)
        assertTrue(nullConfigOutcome.errors.any { it.contains("capacity configuration is required") })

        val unverifiedOutcome = PassengerWorkflowCoordinator.validatePassengerInputs(
            offerId = "OFF_UNVERIFIED_CFG",
            partyId = "PARTY_1",
            pickupStand = "Dadar East Stand",
            destination = "Wadala IMAX",
            partySizeInput = "2",
            capacityConfig = unverifiedProductionConfig
        )
        assertFalse("Unverified config must block production booking", unverifiedOutcome.isValid)
        assertTrue(unverifiedOutcome.errors.any { it.contains("unverified") })

        val validOutcome = PassengerWorkflowCoordinator.validatePassengerInputs(
            offerId = "OFF_VALID_CFG",
            partyId = "PARTY_1",
            pickupStand = "Dadar East Stand",
            destination = "Wadala IMAX",
            partySizeInput = " 3 ",
            capacityConfig = verifiedTestCapacityConfig
        )
        assertTrue("Valid route and party with verified fixture must pass", validOutcome.isValid)
        assertNotNull(validOutcome.validatedOffer)
        assertEquals(3, validOutcome.validatedOffer!!.party.passengerCount)
    }

    @Test
    fun formatTotalBookingFareDisplay_showsFlatPartyFareWithoutPerSeatMultiplication() {
        val onePax = PassengerWorkflowCoordinator.formatTotalBookingFareDisplay(90.0, 1)
        val threePax = PassengerWorkflowCoordinator.formatTotalBookingFareDisplay(90.0, 3)

        assertNotNull(onePax)
        assertNotNull(threePax)
        assertEquals("₹90.00", onePax!!.formattedTotalFare)
        assertEquals("₹90.00", threePax!!.formattedTotalFare)
        assertNotEquals("₹270.00", threePax.formattedTotalFare)
        assertTrue(threePax.billingScopeNote.contains("NOT per seat"))
        assertTrue(threePax.privateSplitNotice.contains("never tracked"))

        assertNull(PassengerWorkflowCoordinator.formatTotalBookingFareDisplay(0.0, 2))
        assertNull(PassengerWorkflowCoordinator.formatTotalBookingFareDisplay(-45.0, 2))
        assertNull(PassengerWorkflowCoordinator.formatTotalBookingFareDisplay(Double.NaN, 2))
        assertNull(PassengerWorkflowCoordinator.formatTotalBookingFareDisplay(Double.POSITIVE_INFINITY, 2))
        assertNull(PassengerWorkflowCoordinator.formatTotalBookingFareDisplay(90.0, 0))
    }

    @Test
    fun offlineDriverOfferDataSource_neverInventsDriversAndReturnsExactEmptyState() {
        val result = OfflineDriverOfferDataSource.fetchOffers(
            pickupStandQuery = "Bandra Station East",
            destinationQuery = "BKC"
        )
        assertTrue(result is LiveOfferFeedResult.BackendUnavailable)
        val unavailable = result as LiveOfferFeedResult.BackendUnavailable
        assertEquals(
            "No live driver offers are currently available. Station reference information is available.",
            unavailable.message
        )
    }

    @Test
    fun buildBookingSummary_preservesPickupAndPartyIsolationRules() {
        val posting = DriverRoutePosting(
            postingId = "POST_1",
            driverId = "DRV_1",
            designatedPickupStand = "Kurla West Auto Stand",
            destinationStand = "BKC Citibank",
            totalBookingFare = 120.0,
            availableSeats = 3,
            capacityConfig = verifiedTestCapacityConfig
        )

        val summary = PassengerWorkflowCoordinator.buildBookingSummary(
            pickupStand = "  Kurla West Auto Stand  ",
            destination = "  BKC Citibank  ",
            partySize = 3,
            driverPosting = posting,
            agreementState = BookingLifecycleState.DRIVER_ACCEPTED,
            isLiveBackendConnected = false
        )

        assertEquals("Kurla West Auto Stand", summary.pickupStand)
        assertEquals("BKC Citibank", summary.destination)
        assertEquals(3, summary.partySize)
        assertEquals("₹120.00", summary.fareDisplay?.formattedTotalFare)
        assertTrue(summary.driverPickupRuleNote.contains("Driver waits at the designated pickup stand"))
        assertTrue(summary.partyIsolationNote.contains("never merged"))
        assertEquals(BookingLifecycleState.DRIVER_ACCEPTED, summary.lifecycleState)
    }

    @Test
    fun confirmBookingAfterDriverAcceptance_blocksUnacceptedStatesAndLocksAfterDriverAccept() {
        val baseAgreement = BookingAgreement(
            bookingId = "BOOK_PHASE2_01",
            postingId = "POST_PHASE2_01",
            passengerParty = PassengerParty(partyId = "PARTY_A", passengerCount = 2),
            agreedTotalFare = 90.0,
            designatedStationStand = "Bandra East BKC Auto Bay",
            destination = "BKC Diamond Bourse",
            state = BookingLifecycleState.DRAFT
        )

        val blockedStates = listOf(
            BookingLifecycleState.DRAFT,
            BookingLifecycleState.OFFERED,
            BookingLifecycleState.PASSENGER_CONFIRMED,
            BookingLifecycleState.LOCKED,
            BookingLifecycleState.DECLINED,
            BookingLifecycleState.CANCELLED
        )

        blockedStates.forEach { state ->
            val candidate = baseAgreement.copy(state = state)
            val (unchanged, result) = PassengerWorkflowCoordinator.confirmBookingAfterDriverAcceptance(candidate)
            assertTrue("State $state must not confirm without DRIVER_ACCEPTED", result is ValidationResult.Invalid)
            assertEquals(state, unchanged.state)
        }

        val acceptedAgreement = baseAgreement.copy(state = BookingLifecycleState.DRIVER_ACCEPTED)
        val (lockedAgreement, result) = PassengerWorkflowCoordinator.confirmBookingAfterDriverAcceptance(acceptedAgreement)
        assertTrue("DRIVER_ACCEPTED must transition cleanly to LOCKED", result is ValidationResult.Valid)
        assertEquals(BookingLifecycleState.LOCKED, lockedAgreement.state)
        assertEquals(90.0, lockedAgreement.calculateEffectiveFare(), 0.001)

        // Separate passenger parties must never automatically merge
        val otherParty = PassengerParty(partyId = "PARTY_B", passengerCount = 1)
        assertFalse(BookingDomainCoordinator.canAutomaticallyMergeParties(baseAgreement.passengerParty, otherParty))
    }
}
