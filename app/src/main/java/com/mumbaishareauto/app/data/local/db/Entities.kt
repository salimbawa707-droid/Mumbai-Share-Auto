package com.mumbaishareauto.app.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mumbaishareauto.app.BookingAgreement
import com.mumbaishareauto.app.BookingLifecycleState
import com.mumbaishareauto.app.DriverRoutePosting
import com.mumbaishareauto.app.PassengerParty
import com.mumbaishareauto.app.UserMode
import com.mumbaishareauto.app.VehicleCapacityConfig
import com.mumbaishareauto.app.ui.theme.ThemePreference

/**
 * Persisted favorite station stand route bookmark.
 * Migrated safely from legacy SharedPreferences (`favorite_route_keys`) in v1 -> v2.
 */
@Entity(tableName = "favorite_routes")
data class FavoriteRouteEntity(
    @PrimaryKey
    val routeKey: String,
    val savedAtMs: Long,
    val sourceScope: String = SOURCE_DEVICE_LOCAL
) {
    companion object {
        const val SOURCE_DEVICE_LOCAL = "DEVICE_LOCAL"
        const val SOURCE_MIGRATED_PREFS = "MIGRATED_SHARED_PREFS"
    }
}

/**
 * Persisted BookingAgreement record.
 *
 * Security & Privacy Rules:
 * - Stores local lifecycle agreements ONLY (`dataOrigin = LOCAL_AGREEMENT`).
 * - Never stores sensitive payment credentials (no PAN, UPI PIN, card numbers, or CVV).
 * - Preserves `agreedTotalFare` == `lockedFareInr` invariant across restarts.
 */
@Entity(
    tableName = "bookings",
    indices = [
        Index(value = ["state"]),
        Index(value = ["postingId"])
    ]
)
data class BookingEntity(
    @PrimaryKey
    val bookingId: String,
    val postingId: String,
    val partyId: String,
    val passengerCount: Int,
    val contactRef: String,
    val agreedTotalFare: Double,
    val designatedStationStand: String,
    val destination: String,
    val state: String,
    val lockedFareInr: Double?,
    val cancellationReason: String?,
    val dataOrigin: String = ORIGIN_DEVICE_LOCAL,
    val updatedAtMs: Long
) {
    fun toDomain(): BookingAgreement? {
        val lifecycle = BookingLifecycleState.entries.firstOrNull { it.name == state } ?: return null
        return BookingAgreement(
            bookingId = bookingId,
            postingId = postingId,
            passengerParty = PassengerParty(
                partyId = partyId,
                passengerCount = passengerCount,
                contactRef = contactRef
            ),
            agreedTotalFare = agreedTotalFare,
            designatedStationStand = designatedStationStand,
            destination = destination,
            state = lifecycle,
            lockedFareInr = lockedFareInr,
            cancellationReason = cancellationReason
        )
    }

    companion object {
        const val ORIGIN_DEVICE_LOCAL = "DEVICE_LOCAL_AGREEMENT"
        const val ORIGIN_MIGRATED_PREFS = "MIGRATED_SHARED_PREFS"

        fun fromDomain(
            agreement: BookingAgreement,
            updatedAtMs: Long,
            dataOrigin: String = ORIGIN_DEVICE_LOCAL
        ): BookingEntity = BookingEntity(
            bookingId = agreement.bookingId,
            postingId = agreement.postingId,
            partyId = agreement.passengerParty.partyId,
            passengerCount = agreement.passengerParty.passengerCount,
            contactRef = agreement.passengerParty.contactRef,
            agreedTotalFare = agreement.agreedTotalFare,
            designatedStationStand = agreement.designatedStationStand,
            destination = agreement.destination,
            state = agreement.state.name,
            lockedFareInr = agreement.lockedFareInr,
            cancellationReason = agreement.cancellationReason,
            dataOrigin = dataOrigin,
            updatedAtMs = updatedAtMs
        )
    }
}

/**
 * Persisted local Driver Route Evaluation / Posting record.
 *
 * Truthfulness Guard:
 * - `isLiveServerBroadcast` MUST remain `false` for all local persistence.
 * - Repository rejects any write claiming live server/online driver broadcast in offline mode.
 */
@Entity(
    tableName = "driver_routes",
    indices = [
        Index(value = ["status"]),
        Index(value = ["isLiveServerBroadcast"])
    ]
)
data class DriverRouteEntity(
    @PrimaryKey
    val postingId: String,
    val driverId: String,
    val designatedPickupStand: String,
    val destinationStand: String,
    val viaPointsSummary: String,
    val totalBookingFare: Double,
    val availableSeats: Int,
    val maxPermittedPassengers: Int,
    val isLegallyVerified: Boolean,
    val status: String = STATUS_LOCAL_DRAFT,
    val isLiveServerBroadcast: Boolean = false,
    val dataOrigin: String = ORIGIN_DEVICE_LOCAL,
    val createdAtMs: Long
) {
    fun toDriverRoutePosting(isTestFixture: Boolean = false): DriverRoutePosting =
        DriverRoutePosting(
            postingId = postingId,
            driverId = driverId,
            designatedPickupStand = designatedPickupStand,
            destinationStand = destinationStand,
            totalBookingFare = totalBookingFare,
            availableSeats = availableSeats,
            capacityConfig = VehicleCapacityConfig(
                maxPermittedPassengers = maxPermittedPassengers,
                configurationLabel = if (isLegallyVerified) {
                    "Verified Capacity Config"
                } else {
                    "Unverified Local Evaluation Config"
                },
                isLegallyVerified = isLegallyVerified,
                isTestFixture = isTestFixture
            )
        )

    companion object {
        const val STATUS_LOCAL_DRAFT = "LOCAL_EVALUATION_ONLY"
        const val STATUS_ARCHIVED = "ARCHIVED"
        const val ORIGIN_DEVICE_LOCAL = "DEVICE_LOCAL_DRIVER_ROUTE"
    }
}

/**
 * Singleton row (id = 1) holding user preferences and one-time legacy migration flags.
 */
@Entity(tableName = "user_preferences")
data class UserPreferencesEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val onboardingCompleted: Boolean = false,
    val preferredMode: String = UserMode.PASSENGER.name,
    val themePreference: String = ThemePreference.SYSTEM.name,
    val favoritesMigratedFromPrefs: Boolean = false,
    val agreementsMigratedFromPrefs: Boolean = false,
    val updatedAtMs: Long = 0L
) {
    fun resolvedUserMode(): UserMode =
        UserMode.entries.firstOrNull { it.name == preferredMode } ?: UserMode.PASSENGER

    fun resolvedThemePreference(): ThemePreference =
        ThemePreference.entries.firstOrNull { it.name == themePreference } ?: ThemePreference.SYSTEM

    companion object {
        const val SINGLETON_ID = 1
    }
}

/**
 * Device-local system/compliance/storage notice.
 * Strictly marks `isLocalNotice = true` so local notices are never disguised as live push alerts.
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["isRead"]),
        Index(value = ["createdAtMs"])
    ]
)
data class NotificationEntity(
    @PrimaryKey
    val id: String,
    val category: String,
    val title: String,
    val message: String,
    val isRead: Boolean = false,
    val isLocalNotice: Boolean = true,
    val createdAtMs: Long
) {
    companion object {
        const val CATEGORY_COMPLIANCE = "COMPLIANCE"
        const val CATEGORY_STORAGE = "LOCAL_STORAGE"
        const val CATEGORY_FARE_RULE = "FARE_RULE"
    }
}
