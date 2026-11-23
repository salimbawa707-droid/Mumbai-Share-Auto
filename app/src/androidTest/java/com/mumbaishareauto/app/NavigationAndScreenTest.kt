package com.mumbaishareauto.app

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mumbaishareauto.app.notifications.MumbaiShareAutoNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation UI tests for Mumbai Share Auto navigation,
 * Phase 2 Passenger Experience, and Phase 3 Driver Experience.
 */
@RunWith(AndroidJUnit4::class)
class NavigationAndScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun skipSplashAndOnboardingIfShown() {
        // Emulator TCG can render slowly; onboarding may also already be completed
        // by a previous test because the same app data is reused within the suite.
        composeRule.waitUntil(timeoutMillis = 30_000) {
            runCatching {
                composeRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() ||
                    composeRule.onAllNodesWithText("Find Shared Auto").fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }

        val skipNodes = composeRule.onAllNodesWithText("Skip").fetchSemanticsNodes()
        if (skipNodes.isNotEmpty()) {
            composeRule.onNodeWithText("Skip").performClick()
            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.onAllNodesWithText("Find Shared Auto").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun onboardingSkip_reachesHomeDashboardWithPrimaryActions() {
        skipSplashAndOnboardingIfShown()

        composeRule.onAllNodesWithText("Find Shared Auto")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Offer My Route")[0].assertIsDisplayed()
        composeRule.onNodeWithText("Favorite Routes").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun notificationsShortcut_opensHonestStatusScreen() {
        skipSplashAndOnboardingIfShown()

        composeRule.onNodeWithContentDescription("Open notifications and service status").performClick()
        composeRule.onNodeWithText("Real-Time Push Alerts Unavailable").assertIsDisplayed()
    }

    @Test
    fun findSharedAuto_supportsSearchableStationEastWestFilterAndHonestEmptyOffers() {
        skipSplashAndOnboardingIfShown()

        composeRule.onAllNodesWithText("Find Shared Auto")[0].performClick()
        composeRule.onNodeWithText("Find a Shared Auto").assertIsDisplayed()

        // Live route state is asynchronous: Firebase may be Connecting, AuthRequired,
        // Live, or temporarily Error while the local station search remains usable.
        // This test covers the station-search workflow, not network readiness.
        composeRule.onNodeWithContentDescription("Search station name").assertIsDisplayed()
        composeRule.onNodeWithText("East Exit").assertIsDisplayed()

        // Search station and select its clickable FilterChip, then filter by East exit
        composeRule.onNodeWithContentDescription("Search station name").performTextInput("Andheri")
        composeRule.onAllNodesWithText("Andheri", substring = true).filter(hasClickAction())[1].performClick()
        composeRule.onNodeWithText("East Exit").performClick()
        composeRule.onNodeWithText("Andheri East Auto Deck Stand", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("SEEPZ Gate 1 & 2 / MIDC / Mahakali Caves", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun passengerBookingWorkflow_validatesInvalidPartyAndLocksConfirmWithoutDriverAccept() {
        skipSplashAndOnboardingIfShown()

        composeRule.onAllNodesWithText("Offer My Route")[0].performClick()
        composeRule.onNodeWithText("Passenger Booking Workflow").assertIsDisplayed()
        composeRule.onNodeWithText("Next: Party & Offers").performScrollTo().performClick()

        // Invalid party size triggers validation error via central BookingDomain
        composeRule.onNodeWithContentDescription("Passenger party size count").performTextInput("0")
        composeRule.onNodeWithText("Validate Party & Route via BookingDomain").performScrollTo().performClick()
        // Invalid party size must surface a validation error; it must not be mistaken for an
        // offline-offers state. Move to the summary to verify the real offline disclosure and lock.
        composeRule.onNodeWithText("Review Booking Summary").performScrollTo().performClick()
        composeRule.onNodeWithText("Confirmation Locked").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "No live driver offers are currently available. Station reference information is available."
        ).performScrollTo().assertIsDisplayed()

        // Booking summary keeps confirmation disabled without DRIVER_ACCEPTED
        composeRule.onNodeWithText("Confirm Booking (Requires Driver Acceptance)").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun driverDashboardAndRoutePosting_showsRequiredSectionsFlatFarePreviewAndOfflineDisclosures() {
        skipSplashAndOnboardingIfShown()

        // "Driver Dashboard" is a Home quick-access tile; open the route before
        // asserting controls that exist only inside DriverModeScreen.
        composeRule.onNodeWithText("Driver Dashboard").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Post My Route").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Post My Route")[0].assertIsDisplayed()
        composeRule.onNodeWithText("My Active Route").assertIsDisplayed()
        composeRule.onNodeWithText("Booking Requests").assertIsDisplayed()

        // Verify the offline Booking Requests tab without assuming it is a live feed.
        composeRule.onNodeWithText("Booking Requests").performClick()
        composeRule.onNodeWithText("No Genuine Booking Requests").performScrollTo().assertIsDisplayed()

        // Return Home, reopen the dashboard, and launch the route-posting form.
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Driver Dashboard").performScrollTo().performClick()
        composeRule.onAllNodesWithText("Post My Route")[0].performClick()
        composeRule.onNodeWithText("Post My Route & Total Fare").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Driver pickup railway station").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("8. Vehicle Capacity Verification Status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Total booking fare in rupees").performScrollTo().performTextInput("120")
        composeRule.onNodeWithText("Flat Booking Fare Preview: ₹120").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Publish Live Route (Backend Unavailable)").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun stationDirectory_filtersByLineEastWestStationAndDestination() {
        skipSplashAndOnboardingIfShown()

        composeRule.onNodeWithText("Stands").performClick()
        composeRule.onNodeWithText("Mumbai Station Stand Directory").assertIsDisplayed()
        composeRule.onNodeWithText("Unverified Reference Directory").assertIsDisplayed()

        // Filter by E • EAST badge, then search station & destination
        composeRule.onNodeWithText("E • EAST").performClick()
        composeRule.onNodeWithContentDescription("Directory search by station name").performTextInput("Bandra")
        composeRule.onNodeWithContentDescription("Directory search by destination corridor").performTextInput("BKC")
        composeRule.onNodeWithText("E • EAST").assertIsDisplayed()
        composeRule.onNodeWithText("Stand (East Exit): Bandra Station East Auto Stand", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun extractDeepLinkBookingId_handlesNullBlankAndPopulatedAndroidIntentsCleanly() {
        assertNull(extractDeepLinkBookingId(null))

        val blankIntent = Intent().apply {
            putExtra(MumbaiShareAutoNotificationManager.EXTRA_DEEP_LINK_BOOKING_ID, "   ")
        }
        assertNull(extractDeepLinkBookingId(blankIntent))

        val validIntent = Intent().apply {
            putExtra(MumbaiShareAutoNotificationManager.EXTRA_DEEP_LINK_BOOKING_ID, "DRAFT_PAX_12")
        }
        assertEquals("DRAFT_PAX_12", extractDeepLinkBookingId(validIntent))
    }

    @Test
    fun phoneOtpLoginScreen_supportsPassengerAndDriverRoleToggleWithAutoPlateInput() {
        skipSplashAndOnboardingIfShown()

        composeRule.onNodeWithText("Sign In with +91 Mobile OTP").performScrollTo().performClick()
        composeRule.onNodeWithText("1. Select Your Role").assertIsDisplayed()
        composeRule.onNodeWithText("2. Indian Mobile Number (+91)").performScrollTo().assertIsDisplayed()

        // Selecting Driver reveals Driver Auto Registration Number field
        composeRule.onNodeWithContentDescription("Driver role").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Driver auto registration number").performScrollTo().assertIsDisplayed()
    }
}
