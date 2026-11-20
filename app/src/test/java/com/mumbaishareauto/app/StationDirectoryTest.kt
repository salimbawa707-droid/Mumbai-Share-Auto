package com.mumbaishareauto.app

import com.mumbaishareauto.app.data.MumbaiStationDirectory
import com.mumbaishareauto.app.model.RailwayLine
import com.mumbaishareauto.app.model.ReferenceVerificationStatus
import com.mumbaishareauto.app.model.StationSide
import com.mumbaishareauto.app.model.StationStandRoute
import com.mumbaishareauto.app.ui.share.ShareUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive Phase 5 unit tests verifying MumbaiStationDirectory, StationModels accuracy
 * safeguards, railway line & East/West filtering, unavailable location handling, and share formatting.
 */
class StationDirectoryTest {

    private val testConfig = VehicleCapacityConfig(
        maxPermittedPassengers = 3,
        configurationLabel = "Directory Test Fixture Capacity",
        isLegallyVerified = true,
        isTestFixture = true
    )

    @Test
    fun directory_containsRoutesAcrossAllThreeMumbaiRailwayLines() {
        val allRoutes = MumbaiStationDirectory.allStationRoutes
        assertTrue("Directory should contain substantial station stand routes", allRoutes.size >= 15)

        val westernRoutes = MumbaiStationDirectory.getRoutesByLine(RailwayLine.WESTERN)
        val centralRoutes = MumbaiStationDirectory.getRoutesByLine(RailwayLine.CENTRAL)
        val harbourRoutes = MumbaiStationDirectory.getRoutesByLine(RailwayLine.HARBOUR)

        assertTrue("Western line stands must exist", westernRoutes.isNotEmpty())
        assertTrue("Central line stands must exist", centralRoutes.isNotEmpty())
        assertTrue("Harbour line stands must exist", harbourRoutes.isNotEmpty())
    }

    @Test
    fun stationSearch_destinationSearch_and_eastWestFilters_workAccurately() {
        val bandraEast = MumbaiStationDirectory.searchByStationName(
            stationQuery = "Bandra",
            line = RailwayLine.WESTERN,
            side = StationSide.EAST
        )
        assertTrue(bandraEast.isNotEmpty())
        assertTrue(bandraEast.all { it.stationName == "Bandra" && it.side == StationSide.EAST })

        val lokhandwalaWest = MumbaiStationDirectory.searchByDestination(
            destinationQuery = "Lokhandwala",
            line = RailwayLine.WESTERN,
            side = StationSide.WEST
        )
        assertTrue(lokhandwalaWest.isNotEmpty())
        assertTrue(lokhandwalaWest.all { it.side == StationSide.WEST && it.destination.contains("Lokhandwala") })

        val favoritesFiltered = MumbaiStationDirectory.filterDirectory(
            stationQuery = "Thane",
            destinationQuery = "Wagle",
            line = RailwayLine.CENTRAL,
            side = StationSide.WEST,
            favoritesOnly = true,
            favoriteIds = setOf("c_tna_w_wagle")
        )
        assertEquals(1, favoritesFiltered.size)
        assertEquals("c_tna_w_wagle", favoritesFiltered.first().id)

        val bkcRoutes = MumbaiStationDirectory.searchRoutes("BKC")
        assertTrue(bkcRoutes.isNotEmpty())
        assertTrue(bkcRoutes.all { it.destination.contains("BKC") || it.viaPoints.any { p -> p.contains("BKC") } })
    }

    @Test
    fun accuracyCompliance_allEntriesAreMarkedUnverifiedAndWithinValidBoundsIfPresent() {
        val allRoutes = MumbaiStationDirectory.allStationRoutes
        for (route in allRoutes) {
            assertEquals(
                "Entries must not claim verified status without independent evidence",
                ReferenceVerificationStatus.UNVERIFIED_REFERENCE,
                route.verificationStatus
            )
            assertFalse(
                "Badge label must explicitly state Unverified",
                route.effectiveVerificationStatus.badgeLabel.equals("Verified", ignoreCase = true)
            )
            if (route.hasAvailableCoordinates) {
                val lat = route.standLatitude!!
                val lng = route.standLongitude!!
                assertTrue("Latitude for ${route.stationName} ($lat) must be in Mumbai MMR (~18.9 to 19.4)", lat in 18.9..19.4)
                assertTrue("Longitude for ${route.stationName} ($lng) must be in Mumbai MMR (~72.7 to 73.2)", lng in 72.7..73.2)
            }
            assertTrue("Stand name must be defined", route.standName.isNotBlank())
            assertTrue("Destination must be defined", route.destination.isNotBlank())
            assertTrue("Landmark must be defined", route.standLandmark.isNotBlank())
            assertTrue(route.historicalFareDisclosure.contains("not per seat", ignoreCase = true))
        }
    }

    @Test
    fun unavailableLocationAndMetrics_reportHonestUnavailableMessagesWithoutFabrication() {
        val unavailableRoute = StationStandRoute(
            id = "test_unavail_loc",
            stationName = "Panvel",
            stationCode = "PNVL",
            line = RailwayLine.HARBOUR,
            side = StationSide.EAST,
            standName = "Panvel East Pending Stand",
            standLandmark = "Landmark verification pending",
            destination = "Kalamboli",
            viaPoints = emptyList(),
            distanceKm = null,
            approxMinutes = null,
            standLatitude = null,
            standLongitude = null
        )

        assertFalse(unavailableRoute.hasAvailableCoordinates)
        assertEquals(ReferenceVerificationStatus.LOCATION_UNAVAILABLE, unavailableRoute.effectiveVerificationStatus)
        assertEquals("Distance & travel time unavailable", unavailableRoute.formattedDistanceAndTimeOrUnavailable)
        assertEquals("Location data unavailable", unavailableRoute.formattedLocationAvailability)

        val sharedText = ShareUtils.formatShareStandMessage(unavailableRoute)
        assertTrue(sharedText.contains("Unverified Reference"))
        assertTrue(sharedText.contains("Currently unavailable"))
        assertFalse(sharedText.contains("https://maps.google.com"))
    }

    @Test
    fun referenceFares_areNotTreatedAsPerSeatMultipliersInBookingAgreement() {
        val bandraStand = MumbaiStationDirectory.allStationRoutes.first { it.id == "w_bnd_e_bkc" }

        val driverPosting = DriverRoutePosting(
            postingId = "POST_BANDRA_DIRECT",
            driverId = "DRV_BANDRA_01",
            designatedPickupStand = bandraStand.standName,
            destinationStand = bandraStand.destination,
            totalBookingFare = 90.0,
            availableSeats = 3,
            capacityConfig = testConfig
        )

        val partyOfThree = PassengerParty(partyId = "PARTY_3PAX", passengerCount = 3)
        val (agreement, result) = BookingDomainCoordinator.createBookingOffer(
            bookingId = "BOOK_PARTY_3",
            posting = driverPosting,
            party = partyOfThree
        )

        assertTrue(result is ValidationResult.Valid)
        assertNotNull(agreement)
        assertEquals(90.0, agreement!!.calculateEffectiveFare(), 0.001)
    }
}
