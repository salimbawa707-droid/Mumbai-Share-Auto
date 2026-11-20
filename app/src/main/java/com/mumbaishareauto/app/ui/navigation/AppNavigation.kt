package com.mumbaishareauto.app.ui.navigation

/**
 * Complete screen destinations for Phase 1 Mumbai Share Auto.
 * Maps all 15 redesigned screens explicitly.
 */
enum class AppRoute(
    val title: String,
    val subtitle: String,
    val isTopLevelTab: Boolean = false,
    val showChrome: Boolean = true
) {
    SPLASH(
        title = "Mumbai Share Auto",
        subtitle = "Initializing",
        showChrome = false
    ),
    ONBOARDING(
        title = "Welcome",
        subtitle = "Version 1 Rules",
        showChrome = false
    ),
    HOME(
        title = "Mumbai Share Auto",
        subtitle = "Urban Auto Coordination",
        isTopLevelTab = true
    ),
    PASSENGER_MODE(
        title = "Passenger Mode",
        subtitle = "Commuter Hub"
    ),
    DRIVER_MODE(
        title = "Driver Mode",
        subtitle = "Driver Dashboard"
    ),
    FIND_SHARED_AUTO(
        title = "Find Shared Auto",
        subtitle = "Corridor & Stand Finder"
    ),
    STATION_DIRECTORY(
        title = "Station Directory",
        subtitle = "Mumbai Suburban Stands",
        isTopLevelTab = true
    ),
    PASSENGER_BOOKING(
        title = "Offer My Route",
        subtitle = "Passenger Party Request"
    ),
    DRIVER_ROUTE_POSTING(
        title = "Post Route & Fare",
        subtitle = "Driver Total-Booking Posting"
    ),
    BOOKING_DETAILS(
        title = "Booking Details",
        subtitle = "Agreement & Lifecycle Inspector"
    ),
    BOOKING_HISTORY(
        title = "My Bookings",
        subtitle = "Local Drafts & History",
        isTopLevelTab = true
    ),
    FAVORITES(
        title = "Favorite Routes",
        subtitle = "Saved Station Stands",
        isTopLevelTab = true
    ),
    NOTIFICATIONS(
        title = "Notifications",
        subtitle = "System & Service Status"
    ),
    PROFILE_SETTINGS(
        title = "Profile & Settings",
        subtitle = "Preferences & Compliance",
        isTopLevelTab = true
    ),
    PHONE_AUTH(
        title = "Mobile OTP Login",
        subtitle = "+91 Phone Verification & Role Setup"
    ),
    HELP_ABOUT(
        title = "Help & About",
        subtitle = "Locked Rules & Disclosures"
    )
}
