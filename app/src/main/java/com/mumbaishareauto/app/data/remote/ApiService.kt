package com.mumbaishareauto.app.data.remote

import com.mumbaishareauto.app.BookingLifecycleState
import com.mumbaishareauto.app.UserMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Network Connectivity Awareness interface.
 */
interface NetworkConnectivityManager {
    val isConnected: StateFlow<Boolean>
    val connectionType: StateFlow<String>
    suspend fun checkConnectivityNow(): Boolean
}

/**
 * Result wrapper for remote API communications.
 */
sealed class NetworkResult<out T> {
    data class Success<T>(
        val data: T,
        val statusCode: Int = 200,
        val responseTimestampMs: Long = System.currentTimeMillis()
    ) : NetworkResult<T>()

    data class Error(
        val message: String,
        val statusCode: Int = -1,
        val cause: Throwable? = null
    ) : NetworkResult<Nothing>()

    data class NetworkUnavailable(
        val message: String = "No secure network connection available. Offline cache is displayed."
    ) : NetworkResult<Nothing>()
}

// ==========================================
// REQUEST & RESPONSE DTOs (HTTPS / REST)
// ==========================================

data class RegisterRequestDto(
    val email: String,
    val passwordHash: String, // Client-side hashed before transmission; server verifies & salts
    val fullName: String,
    val role: String // PASSENGER or DRIVER
)

data class AuthRequestDto(
    val email: String,
    val passwordHash: String
)

data class AuthResponseDto(
    val tokenType: String = "Bearer",
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val userId: String,
    val email: String,
    val role: String,
    val driverVerified: Boolean
)

data class DriverProfileDto(
    val driverId: String,
    val licenseNumber: String,
    val vehicleRegistration: String,
    val maxPermittedPassengers: Int,
    val isLegallyVerified: Boolean,
    val permitExpiryEpochMs: Long
)

data class RoutePostingRequestDto(
    val driverId: String,
    val designatedPickupStand: String,
    val destinationStand: String,
    val viaPointsSummary: String,
    val totalBookingFare: Double,
    val availableSeats: Int,
    val maxPermittedPassengers: Int,
    val isLegallyVerified: Boolean
)

data class RoutePostingResponseDto(
    val postingId: String,
    val driverId: String,
    val designatedPickupStand: String,
    val destinationStand: String,
    val totalBookingFare: Double,
    val availableSeats: Int,
    val createdAtEpochMs: Long,
    val isCurrentlyActive: Boolean
)

data class RouteSearchQueryDto(
    val pickupQuery: String,
    val destinationQuery: String,
    val railwayLine: String?,
    val eastWestFilter: String?
)

data class BookingRequestDto(
    val postingId: String,
    val passengerPartyId: String,
    val passengerCount: Int,
    val contactRef: String,
    val proposedTotalFare: Double
)

data class BookingResponseDto(
    val bookingId: String,
    val postingId: String,
    val passengerPartyId: String,
    val passengerCount: Int,
    val agreedTotalFare: Double,
    val designatedStationStand: String,
    val destination: String,
    val state: String,
    val lockedFareInr: Double?,
    val cancellationReason: String?,
    val updatedAtEpochMs: Long
)

data class StateTransitionRequestDto(
    val bookingId: String,
    val targetState: String,
    val reason: String?,
    val clientTimestampMs: Long
)

data class NotificationResponseDto(
    val notificationId: String,
    val category: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAtEpochMs: Long
)

// ==========================================
// API SERVICE INTERFACE (RETROFIT STYLE HTTPS)
// ==========================================

/**
 * HTTPS REST API Service Interface for Mumbai Share Auto backend coordination.
 * Enforces HTTPS base URL configuration, secure Bearer token authentication headers,
 * and server-side validation semantics.
 */
interface MumbaiShareAutoApiService {

    suspend fun registerUser(request: RegisterRequestDto): NetworkResult<AuthResponseDto>

    suspend fun authenticateUser(request: AuthRequestDto): NetworkResult<AuthResponseDto>

    suspend fun getDriverProfile(authToken: String, driverId: String): NetworkResult<DriverProfileDto>

    suspend fun postDriverRoute(authToken: String, request: RoutePostingRequestDto): NetworkResult<RoutePostingResponseDto>

    suspend fun searchActiveRoutes(authToken: String, query: RouteSearchQueryDto): NetworkResult<List<RoutePostingResponseDto>>

    suspend fun createBookingRequest(authToken: String, request: BookingRequestDto): NetworkResult<BookingResponseDto>

    suspend fun transitionBookingState(authToken: String, request: StateTransitionRequestDto): NetworkResult<BookingResponseDto>

    suspend fun getBookingStatus(authToken: String, bookingId: String): NetworkResult<BookingResponseDto>

    suspend fun getNotifications(authToken: String): NetworkResult<List<NotificationResponseDto>>
}

// ==========================================
// AUTHENTICATION ABSTRACTION
// ==========================================

interface AuthRepositoryDataSource {
    val isAuthenticated: StateFlow<Boolean>
    val currentUserToken: StateFlow<String?>
    val currentUserRole: StateFlow<UserMode>
    suspend fun login(email: String, passwordRaw: String): NetworkResult<AuthResponseDto>
    suspend fun register(email: String, passwordRaw: String, fullName: String, role: UserMode): NetworkResult<AuthResponseDto>
    suspend fun logout(): NetworkResult<Unit>
}

// ==========================================
// BOOKING SYNCHRONIZATION ABSTRACTION
// ==========================================

interface BookingSyncDataSource {
    suspend fun syncBookingWithServer(bookingId: String): NetworkResult<BookingResponseDto>
    suspend fun submitBookingRequest(request: BookingRequestDto): NetworkResult<BookingResponseDto>
    suspend fun updateBookingLifecycle(bookingId: String, nextState: BookingLifecycleState, reason: String?): NetworkResult<BookingResponseDto>
}

// ==========================================
// DRIVER ROUTE SYNCHRONIZATION ABSTRACTION
// ==========================================

interface DriverRouteSyncDataSource {
    suspend fun fetchActiveLiveRoutes(pickupStand: String): NetworkResult<List<RoutePostingResponseDto>>
    suspend fun broadcastDriverRoute(request: RoutePostingRequestDto): NetworkResult<RoutePostingResponseDto>
}

// ==========================================
// OFFLINE-AWARE REPOSITORY ARCHITECTURE DOCUMENTATION
// ==========================================
/**
 * ARCHITECTURE NOTE:
 * When network connectivity is absent or no backend server is configured:
 * 1. App falls back strictly to Room SQLite local persistence (Phase 6).
 * 2. Cached local data is explicitly distinguished from live data; cached driver offers are
 *    NEVER displayed as currently available online.
 * 3. UI displays banner: "Online matching unavailable. Showing offline local cache."
 * 4. Server enforces all critical business invariants (HTTPS transmission, password hashing,
 *    fare locking, party isolation, and RTO vehicle capacity verification).
 */
