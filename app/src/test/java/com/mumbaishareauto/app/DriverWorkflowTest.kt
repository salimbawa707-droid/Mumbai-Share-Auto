package com.mumbaishareauto.app

import com.mumbaishareauto.app.model.StationSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Phase 3 unit tests for the Driver Experience:
 * - Complete 8-field route posting validation
 * - Single total booking fare display (e.g. ₹120 invariant for 2 or 3 passengers, never per seat)
 * - Rejection of empty routes, same pickup/dest, blank/0/negative/NaN/infinite fares, invalid capacity, unverified legal capacity
 * - Honest offline unavailability for DriverBookingDataSource
 * - Genuine request accept/reject transition gating (never fakeable offline)
 */
class DriverWorkflowTest {

    private val verifiedFixtureConfig = VehicleCapacityConfig(
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
    fun formatDriverTotalFarePreview_keepsCompleteFareFixedAt120ForMultiplePassengers() {
        val previewTwo = DriverWorkflowCoordinator.formatDriverTotalFarePreview(120.0, 2)
        val previewThree = DriverWorkflowCoordinator.formatDriverTotalFarePreview(120.0, 3)

        assertNotNull(previewTwo)
        assertNotNull(previewThree)
        assertEquals("₹120", previewTwo!!.formattedTotalFare)
        assertEquals("₹120", previewThree!!.formattedTotalFare)
        assertTrue(previewThree.farePolicyNote.contains("Remains ₹120"))
        assertTrue(previewThree.farePolicyNote.contains("never per-seat"))

        assertNull(DriverWorkflowCoordinator.formatDriverTotalFarePreview(0.0, 3))
        assertNull(DriverWorkflowCoordinator.formatDriverTotalFarePreview(-120.0, 3))
        assertNull(DriverWorkflowCoordinator.formatDriverTotalFarePreview(Double.NaN, 3))
        assertNull(DriverWorkflowCoordinator.formatDriverTotalFarePreview(Double.POSITIVE_INFINITY, 3))
        assertNull(DriverWorkflowCoordinator.formatDriverTotalFarePreview(120.0, 0))
    }

    @Test
    fun parseViaPoints_handlesCommaSemicolonAndBlankInputs() {
        assertEquals(
            listOf("Kalanagar", "Family Court", "BKC Junction"),
            DriverWorkflowCoordinator.parseViaPoints("Kalanagar, Family Court ; BKC Junction, Kalanagar")
        )
        assertTrue(DriverWorkflowCoordinator.parseViaPoints("   ").isEmpty())
    }

    @Test
    fun validateDriverRoutePostingInput_acceptsCompleteValid8FieldSubmissionWithVerifiedFixture() {
        val outcome = DriverWorkflowCoordinator.validateDriverRoutePostingInput(
            postingId = "POST_BND_120",
            driverId = "DRV_MH02_1234",
            pickupStation = "Bandra",
            pickupSide = StationSide.EAST,
            designatedStand = "Bandra East BKC Auto Bay",
            destination = "BKC Diamond Bourse",
            viaPointsInput = "Kalanagar, Family Court",
            totalFareInput = "120",
            availableCapacityInput = "3",
            capacityConfig = verifiedFixtureConfig
        )

        assertTrue(outcome.isValid)
        assertTrue(outcome.errors.isEmpty())
        assertNotNull(outcome.validatedPosting)
        assertEquals(120.0, outcome.validatedPosting!!.totalBookingFare, 0.001)
        assertEquals(3, outcome.validatedPosting.availableSeats)
        assertEquals(listOf("Kalanagar", "Family Court"), outcome.viaPoints)
        assertEquals("₹120", outcome.farePreview?.formattedTotalFare)
        assertTrue(outcome.verificationStatusLabel.contains("Verified Legal Capacity"))
    }

    @Test
    fun validateDriverRoutePostingInput_rejectsEmptyRoutesMissingSideAndIdenticalPickupDestination() {
        val missingStationAndSide = DriverWorkflowCoordinator.validateDriverRoutePostingInput(
            postingId = "POST_ERR_1",
            driverId = "DRV_1",
            pickupStation = " ",
            pickupSide = null,
            designatedStand = "",
            destination = "BK",
            viaPointsInput = "",
            totalFareInput = "120",
            availableCapacityInput = "3",
            capacityConfig = verifiedFixtureConfig
        )
        assertFalse(missingStationAndSide.isValid)
        assertTrue(missingStationAndSide.errors.any { it.contains("Pickup railway station is required") })
        assertTrue(missingStationAndSide.errors.any { it.contains("East or West") })
        assertTrue(missingStationAndSide.errors.any { it.contains("Designated pickup stand cannot be blank") })
        assertTrue(missingStationAndSide.errors.any { it.contains("Destination must be at least 3 characters") })

        val identicalRoute = DriverWorkflowCoordinator.validateDriverRoutePostingInput(
            postingId = "POST_ERR_2",
            driverId = "DRV_1",
            pickupStation = "Andheri",
            pickupSide = StationSide.EAST,
            designatedStand = "  Andheri East Auto Deck Stand ",
            destination = "andheri east auto deck stand",
            viaPointsInput = "",
            totalFareInput = "120",
            availableCapacityInput = "3",
            capacityConfig = verifiedFixtureConfig
        )
        assertFalse(identicalRoute.isValid)
        assertTrue(identicalRoute.errors.any { it.contains("cannot be identical") })
    }

    @Test
    fun validateDriverRoutePostingInput_rejectsBlankZeroNegativeNanAndInfiniteFares() {
        val badFares = listOf("", "  ", "abc", "0", "0.0", "-120", "NaN", "Infinity", "-Infinity")
        badFares.forEach { badFare ->
            val outcome = DriverWorkflowCoordinator.validateDriverRoutePostingInput(
                postingId = "POST_FARE_$badFare",
                driverId = "DRV_1",
                pickupStation = "Andheri",
                pickupSide = StationSide.EAST,
                designatedStand = "Andheri East Auto Deck Stand",
                destination = "SEEPZ Gate 1",
                viaPointsInput = "",
                totalFareInput = badFare,
                availableCapacityInput = "3",
                capacityConfig = verifiedFixtureConfig
            )
            assertFalse("Fare '$badFare' must be rejected", outcome.isValid)
            assertNull(outcome.validatedPosting)
        }
    }

    @Test
    fun validateDriverRoutePostingInput_rejectsInvalidPassengerCapacityAndUnverifiedLegalConfig() {
        val badCapacities = listOf("", "   ", "three", "2.5", "0", "-1", "4")
        badCapacities.forEach { badCap ->
            val outcome = DriverWorkflowCoordinator.validateDriverRoutePostingInput(
                postingId = "POST_CAP_$badCap",
                driverId = "DRV_1",
                pickupStation = "Andheri",
                pickupSide = StationSide.WEST,
                designatedStand = "Andheri West Station Stand",
                destination = "Lokhandwala Complex",
                viaPointsInput = "",
                totalFareInput = "120",
                availableCapacityInput = badCap,
                capacityConfig = verifiedFixtureConfig
            )
            assertFalse("Capacity '$badCap' must be rejected", outcome.isValid)
        }

        val unverifiedOutcome = DriverWorkflowCoordinator.validateDriverRoutePostingInput(
            postingId = "POST_UNVERIFIED",
            driverId = "DRV_1",
            pickupStation = "Andheri",
            pickupSide = StationSide.WEST,
            designatedStand = "Andheri West Station Stand",
            destination = "Lokhandwala Complex",
            viaPointsInput = "",
            totalFareInput = "120",
            availableCapacityInput = "3",
            capacityConfig = unverifiedProductionConfig
        )
        assertFalse("Unverified legal capacity must block production route posting", unverifiedOutcome.isValid)
        assertTrue(unverifiedOutcome.errors.any { it.contains("unverified") })
        assertTrue(unverifiedOutcome.verificationStatusLabel.contains("Unverified Legal Capacity"))

        val nullConfigOutcome = DriverWorkflowCoordinator.validateDriverRoutePostingInput(
            postingId = "POST_NULL_CFG",
            driverId = "DRV_1",
            pickupStation = "Andheri",
            pickupSide = StationSide.WEST,
            designatedStand = "Andheri West Station Stand",
            destination = "Lokhandwala Complex",
            viaPointsInput = "",
            totalFareInput = "120",
            availableCapacityInput = "3",
            capacityConfig = null
        )
        assertFalse(nullConfigOutcome.isValid)
        assertTrue(nullConfigOutcome.verificationStatusLabel.contains("Missing"))
    }

    @Test
    fun offlineDriverBookingDataSource_honestlyReportsBackendUnavailable() {
        val feed = OfflineDriverBookingDataSource.fetchDriverBookings()
        assertTrue(feed is DriverRequestsFeedResult.BackendUnavailable)
        val message = (feed as DriverRequestsFeedResult.BackendUnavailable).message
        assertTrue(message.contains("unavailable", ignoreCase = true))
    }

    @Test
    fun respondToGenuineBookingRequest_blocksOfflineLocalClicksAndAcceptsOrRejectsGenuineOfferedOnly() {
        val offeredAgreement = BookingAgreement(
            bookingId = "BOOK_DRV_01",
            postingId = "POST_BND_120",
            passengerParty = PassengerParty(partyId = "PARTY_2", passengerCount = 2),
            agreedTotalFare = 120.0,
            designatedStationStand = "Bandra East BKC Auto Bay",
            destination = "BKC Diamond Bourse",
            state = BookingLifecycleState.OFFERED
        )

        // Local click while offline must NEVER accept or reject
        val (offlineUnchangedAccept, offlineAcceptRes) = DriverWorkflowCoordinator.respondToGenuineBookingRequest(
            agreement = offeredAgreement,
            accept = true,
            isBackendConnected = false
        )
        assertTrue(offlineAcceptRes is ValidationResult.Invalid)
        assertEquals(BookingLifecycleState.OFFERED, offlineUnchangedAccept.state)

        val (offlineUnchangedReject, offlineRejectRes) = DriverWorkflowCoordinator.respondToGenuineBookingRequest(
            agreement = offeredAgreement,
            accept = false,
            isBackendConnected = false
        )
        assertTrue(offlineRejectRes is ValidationResult.Invalid)
        assertEquals(BookingLifecycleState.OFFERED, offlineUnchangedReject.state)

        // Genuine backend OFFERED request -> DRIVER_ACCEPTED or DECLINED
        val (acceptedAgreement, acceptResult) = DriverWorkflowCoordinator.respondToGenuineBookingRequest(
            agreement = offeredAgreement,
            accept = true,
            isBackendConnected = true
        )
        assertTrue(acceptResult is ValidationResult.Valid)
        assertEquals(BookingLifecycleState.DRIVER_ACCEPTED, acceptedAgreement.state)
        assertEquals(120.0, acceptedAgreement.calculateEffectiveFare(), 0.001)

        val (declinedAgreement, declineResult) = DriverWorkflowCoordinator.respondToGenuineBookingRequest(
            agreement = offeredAgreement,
            accept = false,
            isBackendConnected = true
        )
        assertTrue(declineResult is ValidationResult.Valid)
        assertEquals(BookingLifecycleState.DECLINED, declinedAgreement.state)

        // Cannot re-respond to an already accepted/cancelled agreement
        val (invalidRepeat, repeatResult) = DriverWorkflowCoordinator.respondToGenuineBookingRequest(
            agreement = acceptedAgreement,
            accept = false,
            isBackendConnected = true
        )
        assertTrue(repeatResult is ValidationResult.Invalid)
        assertEquals(BookingLifecycleState.DRIVER_ACCEPTED, invalidRepeat.state)
    }

    @Test
    fun pickupDirectionsUrl_usesDriverDesignatedStandAndEncodesAddress() {
        val url = PickupDirectionsUrl.build(
            pickupStand = "Bandra East Auto Stand",
            pickupStation = "Bandra East"
        )

        assertNotNull(url)
        assertTrue(url!!.startsWith("https://www.google.com/maps/dir/?api=1&destination="))
        assertTrue(url.contains("Bandra+East+Auto+Stand"))
        assertTrue(url.contains("Mumbai%2C+Maharashtra%2C+India"))
        assertFalse(url.contains("latitude"))
    }

    @Test
    fun pickupDirectionsUrl_rejectsMissingPickupAddress() {
        assertNull(PickupDirectionsUrl.build(" ", "Bandra"))
        assertNull(PickupDirectionsUrl.build("Bandra East Stand", " "))
    }

}
