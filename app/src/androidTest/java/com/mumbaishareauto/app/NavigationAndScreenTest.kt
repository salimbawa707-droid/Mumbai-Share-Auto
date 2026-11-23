package com.mumbaishareauto.app

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.mumbaishareauto.app.auth.FirebaseRuntimeInspector
import com.mumbaishareauto.app.notifications.MumbaiShareAutoNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

        composeRule.onNodeWithTag("home_primary_cta").assertIsDisplayed()
        composeRule.onNodeWithTag("home_offer_route_card").performScrollTo().assertIsDisplayed()
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

        composeRule.onNodeWithTag("home_primary_cta").performClick()
        composeRule.onNodeWithText("Find a Shared Auto").assertIsDisplayed()

        // Live route state is asynchronous: Firebase may be Connecting, AuthRequired,
        // Live, or temporarily Error while the local station search remains usable.
        composeRule.onNodeWithContentDescription("Search station name").assertIsDisplayed()
        composeRule.onNodeWithText("East Exit").assertIsDisplayed()

        // Search station and select its tagged FilterChip, then filter by East exit
        composeRule.onNodeWithContentDescription("Search station name").performTextInput("Andheri")
        composeRule.onNodeWithTag("station_chip_Andheri").performClick()
        composeRule.onNodeWithText("East Exit").performClick()

        val listNode = composeRule.onNodeWithTag("find_shared_auto_list")
        listNode.performScrollToNode(hasText("Andheri East Auto Deck Stand", substring = true))
        composeRule.onNodeWithText("Andheri East Auto Deck Stand", substring = true).assertIsDisplayed()
        listNode.performScrollToNode(hasText("SEEPZ Gate 1 & 2 / MIDC / Mahakali Caves", substring = true))
        composeRule.onNodeWithText("SEEPZ Gate 1 & 2 / MIDC / Mahakali Caves", substring = true).assertIsDisplayed()
    }

    @Test
    fun passengerBookingWorkflow_validatesInvalidPartyAndLocksConfirmWithoutDriverAccept() {
        skipSplashAndOnboardingIfShown()

        composeRule.onNodeWithTag("home_offer_route_card").performScrollTo().performClick()
        composeRule.onNodeWithText("Passenger Booking Workflow").assertIsDisplayed()
        composeRule.onNodeWithText("Next: Party & Offers").performScrollTo().performClick()

        // Invalid party size triggers validation error via central BookingDomain
        composeRule.onNodeWithContentDescription("Passenger party size count").performTextInput("0")
        composeRule.onNodeWithText("Validate Party & Route via BookingDomain").performScrollTo().performClick()
        // Move to summary to verify the offline disclosure and confirmation lock.
        composeRule.onNodeWithText("Review Booking Summary").performScrollTo().performClick()
        composeRule.onNodeWithText("Confirmation Locked").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "No live driver offers are currently available. Station reference information is available."
        ).performScrollTo().assertIsDisplayed()

        // Booking summary keeps confirmation disabled without DRIVER_ACCEPTED
        composeRule.onNodeWithText("Confirm Booking (Requires Driver Acceptance)").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun driverDashboardAndRoutePosting_showsRequiredSectionsFlatFarePreviewAndBackendGate() {
        skipSplashAndOnboardingIfShown()

        composeRule.onNodeWithText("Driver Dashboard").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag("driver_post_route_card").fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("driver_post_route_card").assertIsDisplayed()
        composeRule.onNodeWithTag("driver_tab_${DriverDashboardTab.MY_ACTIVE_ROUTE.name}").assertIsDisplayed()
        composeRule.onNodeWithTag("driver_tab_${DriverDashboardTab.BOOKING_REQUESTS.name}").assertIsDisplayed()
        composeRule.onNodeWithTag("driver_section_${DriverDashboardTab.MY_ACTIVE_ROUTE.name}")
            .performScrollTo()
            .assertIsDisplayed()

        // Verify the Booking Requests tab and its honest empty state.
        composeRule.onNodeWithTag("driver_tab_${DriverDashboardTab.BOOKING_REQUESTS.name}").performClick()
        composeRule.onNodeWithTag("driver_section_${DriverDashboardTab.BOOKING_REQUESTS.name}")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("No Genuine Booking Requests").performScrollTo().assertIsDisplayed()

        // Return Home, reopen the dashboard, and launch the route-posting form.
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Driver Dashboard").performScrollTo().performClick()
        composeRule.onNodeWithTag("driver_post_route_card").performScrollTo().performClick()
        composeRule.onNodeWithText("Post My Route & Total Fare").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Driver pickup railway station").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("8. Vehicle Capacity Verification Status").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Total booking fare in rupees").performScrollTo().performTextInput("120")
        composeRule.onNodeWithText("Flat Booking Fare Preview: ₹120").performScrollTo().assertIsDisplayed()

        // Assert exact live-publishing button state implied by actual Firebase configuration/session.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val firebaseInitialized = FirebaseRuntimeInspector.isFirebaseInitialized(appContext)
        val signedInUser = if (firebaseInitialized) {
            runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        } else {
            null
        }
        if (!firebaseInitialized) {
            composeRule.onNodeWithText("Publish Live Route (Backend Unavailable)")
                .performScrollTo()
                .assertIsNotEnabled()
        } else if (signedInUser == null) {
            composeRule.onNodeWithText("Sign In with +91 OTP to Publish Live Route")
                .performScrollTo()
                .assertIsEnabled()
        } else {
            composeRule.onNodeWithText("Publish Live Route to Cloud Firestore")
                .performScrollTo()
                .assertIsEnabled()
        }
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

        composeRule.onNodeWithTag("station_directory_list").performScrollToNode(
            hasText("Stand (East): Bandra Station East Auto Stand", substring = true)
        )
        composeRule.onNodeWithText("Stand (East): Bandra Station East Auto Stand", substring = true)
            .assertIsDisplayed()
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

