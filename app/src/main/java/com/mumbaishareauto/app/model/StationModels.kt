package com.mumbaishareauto.app.model

/**
 * Mumbai Suburban Railway Lines serving share auto hubs.
 */
enum class RailwayLine(val displayName: String, val lineCode: String) {
    ALL("All Lines", "ALL"),
    WESTERN("Western Line", "WR"),
    CENTRAL("Central Line", "CR"),
    HARBOUR("Harbour Line", "HR")
}

/**
 * Station exit side (East vs West) where designated share auto stands are located.
 */
enum class StationSide(val label: String, val shortBadge: String) {
    EAST("East", "E • EAST"),
    WEST("West", "W • WEST")
}

/**
 * Explicit verification status for physical stand orientation data.
 * Entries must NEVER be labeled verified or authoritative without independent field evidence.
 */
enum class ReferenceVerificationStatus(val badgeLabel: String, val disclosure: String) {
    UNVERIFIED_REFERENCE(
        badgeLabel = "Unverified Reference",
        disclosure = "Coordinates, distances, travel times, and landmarks are unverified reference data until independently confirmed."
    ),
    LOCATION_UNAVAILABLE(
        badgeLabel = "Location Unavailable",
        disclosure = "Precise location coordinates or corridor metrics are currently unavailable for this entry."
    )
}

/**
 * Reference directory model for an unverified Mumbai station share-auto stand.
 *
 * ACCURACY COMPLIANCE:
 * 1. Coordinates, distances, journey times, and landmarks are unverified reference data.
 * 2. Unavailable coordinates, distances, or times are represented honestly as null (never fabricated).
 * 3. Historical indicative corridor notes are NEVER actual booking fares; in Mumbai Share Auto,
 *    drivers quote ONE total fare for the entire booking party, never per seat.
 */
data class StationStandRoute(
    val id: String,
    val stationName: String,
    val stationCode: String,
    val line: RailwayLine,
    val side: StationSide,
    val standName: String,
    val standLandmark: String,
    val destination: String,
    val viaPoints: List<String> = emptyList(),
    val distanceKm: Double? = null,
    val approxMinutes: Int? = null,
    val standLatitude: Double? = null,
    val standLongitude: Double? = null,
    val referenceIndicativeFare: Double? = null,
    val verificationStatus: ReferenceVerificationStatus = ReferenceVerificationStatus.UNVERIFIED_REFERENCE
) {
    val displayTitle: String get() = "$stationName (${side.label}) → $destination"

    val fullStandLocation: String get() = "$standName ($standLandmark)"

    val hasAvailableCoordinates: Boolean
        get() {
            val lat = standLatitude ?: return false
            val lng = standLongitude ?: return false
            return lat.isFinite() && lng.isFinite() &&
                lat in -90.0..90.0 && lng in -180.0..180.0 &&
                !(lat == 0.0 && lng == 0.0)
        }

    val effectiveVerificationStatus: ReferenceVerificationStatus
        get() = if (hasAvailableCoordinates) verificationStatus else ReferenceVerificationStatus.LOCATION_UNAVAILABLE

    val formattedDistanceAndTimeOrUnavailable: String
        get() {
            val dist = distanceKm
            val mins = approxMinutes
            return if (dist != null && dist.isFinite() && dist > 0.0 && mins != null && mins > 0) {
                "~$dist km • ~$mins mins (Unverified est.)"
            } else {
                "Distance & travel time unavailable"
            }
        }

    val formattedLocationAvailability: String
        get() = if (hasAvailableCoordinates) {
            "Unverified ref. pin (${standLatitude}, ${standLongitude})"
        } else {
            "Location data unavailable"
        }

    val historicalFareDisclosure: String
        get() = "Actual fares are quoted per booking by drivers (not per seat)"
}
