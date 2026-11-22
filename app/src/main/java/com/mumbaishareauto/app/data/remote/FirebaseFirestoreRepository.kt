package com.mumbaishareauto.app.data.remote

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.mumbaishareauto.app.auth.AuthDomainValidator
import com.mumbaishareauto.app.auth.FirebaseRuntimeInspector
import com.mumbaishareauto.app.auth.FirestoreErrorInfo
import com.mumbaishareauto.app.auth.FirestoreOperationType
import com.mumbaishareauto.app.model.StationSide
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Live active share-auto route stored in Cloud Firestore at `/active_routes/{postingId}`.
 *
 * Rules preserved:
 * - Total booking fare is flat per booking (`totalBookingFare`), never multiplied per seat.
 * - `availableSeats` is constrained to 1..3 when published and decremented atomically via
 *   Firestore transactions as passengers lock seats.
 * - `isLegallyVerified` stays `false` until an official RTO verification service exists.
 * - No phone numbers or personal PII are stored in public route documents.
 */
data class LiveAutoRoute(
    val postingId: String,
    val driverUid: String,
    val driverAutoRegNumber: String? = null,
    val pickupStation: String,
    val pickupSide: StationSide,
    val designatedStand: String,
    val destination: String,
    val viaPoints: List<String> = emptyList(),
    val totalBookingFare: Double,
    val totalCapacitySeats: Int,
    val availableSeats: Int,
    val activeQueueCount: Int = 0,
    val queueSequence: Int = 0,
    val isActive: Boolean = true,
    val isLegallyVerified: Boolean = false,
    val createdAtMs: Long? = null,
    val updatedAtMs: Long? = null
) {
    fun matchesSearch(
        stationQuery: String = "",
        sideFilter: StationSide? = null,
        destinationOrCorridorQuery: String = ""
    ): Boolean {
        val cleanStation = stationQuery.trim()
        val cleanCorridor = destinationOrCorridorQuery.trim()

        val stationOk = cleanStation.isEmpty() ||
            pickupStation.contains(cleanStation, ignoreCase = true) ||
            designatedStand.contains(cleanStation, ignoreCase = true)

        val sideOk = sideFilter == null || pickupSide == sideFilter

        val corridorOk = cleanCorridor.isEmpty() ||
            destination.contains(cleanCorridor, ignoreCase = true) ||
            designatedStand.contains(cleanCorridor, ignoreCase = true) ||
            pickupStation.contains(cleanCorridor, ignoreCase = true) ||
            viaPoints.any { it.contains(cleanCorridor, ignoreCase = true) }

        return stationOk && sideOk && corridorOk
    }

    fun toBaseFirestoreFields(): Map<String, Any> {
        val fields = linkedMapOf<String, Any>(
            FIELD_POSTING_ID to postingId.trim(),
            FIELD_DRIVER_UID to driverUid.trim(),
            FIELD_PICKUP_STATION to pickupStation.trim(),
            FIELD_PICKUP_SIDE to pickupSide.name,
            FIELD_DESIGNATED_STAND to designatedStand.trim(),
            FIELD_DESTINATION to destination.trim(),
            FIELD_VIA_POINTS to viaPoints.map { it.trim() }.filter { it.isNotEmpty() }.take(10),
            FIELD_TOTAL_BOOKING_FARE to totalBookingFare,
            FIELD_TOTAL_CAPACITY_SEATS to totalCapacitySeats,
            FIELD_AVAILABLE_SEATS to availableSeats,
            FIELD_ACTIVE_QUEUE_COUNT to activeQueueCount,
            FIELD_QUEUE_SEQUENCE to queueSequence,
            FIELD_IS_ACTIVE to isActive,
            FIELD_IS_LEGALLY_VERIFIED to false
        )
        // Vehicle registration is private profile data; never publish it in the public live-route feed.
        return fields
    }

    companion object {
        const val COLLECTION_ACTIVE_ROUTES = "active_routes"
        const val SUBCOLLECTION_SEAT_LOCKS = "seat_locks"

        const val FIELD_POSTING_ID = "postingId"
        const val FIELD_DRIVER_UID = "driverUid"
        const val FIELD_DRIVER_AUTO_REG_NUMBER = "driverAutoRegNumber"
        const val FIELD_PICKUP_STATION = "pickupStation"
        const val FIELD_PICKUP_SIDE = "pickupSide"
        const val FIELD_DESIGNATED_STAND = "designatedStand"
        const val FIELD_DESTINATION = "destination"
        const val FIELD_VIA_POINTS = "viaPoints"
        const val FIELD_TOTAL_BOOKING_FARE = "totalBookingFare"
        const val FIELD_TOTAL_CAPACITY_SEATS = "totalCapacitySeats"
        const val FIELD_AVAILABLE_SEATS = "availableSeats"
        const val FIELD_ACTIVE_QUEUE_COUNT = "activeQueueCount"
        const val FIELD_QUEUE_SEQUENCE = "queueSequence"
        const val FIELD_IS_ACTIVE = "isActive"
        const val FIELD_IS_LEGALLY_VERIFIED = "isLegallyVerified"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"

        const val MIN_SEATS = 1
        const val MAX_SEATS = 3

        fun fromFirestoreFields(
            documentId: String,
            data: Map<String, Any?>,
            createdAtMs: Long? = null,
            updatedAtMs: Long? = null
        ): LiveAutoRoute? {
            val postingId = (data[FIELD_POSTING_ID] as? String)?.trim()?.ifEmpty { null }
                ?: documentId.trim().ifEmpty { null }
                ?: return null
            val driverUid = (data[FIELD_DRIVER_UID] as? String)?.trim()?.ifEmpty { null } ?: return null
            val pickupStation = (data[FIELD_PICKUP_STATION] as? String)?.trim()?.ifEmpty { null } ?: return null
            val rawSide = (data[FIELD_PICKUP_SIDE] as? String)?.trim()
            val pickupSide = StationSide.entries.firstOrNull { it.name.equals(rawSide, ignoreCase = true) }
                ?: return null
            val designatedStand = (data[FIELD_DESIGNATED_STAND] as? String)?.trim()?.ifEmpty { null } ?: return null
            val destination = (data[FIELD_DESTINATION] as? String)?.trim()?.ifEmpty { null } ?: return null
            val viaPoints = (data[FIELD_VIA_POINTS] as? List<*>)?.mapNotNull { (it as? String)?.trim()?.ifEmpty { null } }
                ?: emptyList()
            val totalFare = (data[FIELD_TOTAL_BOOKING_FARE] as? Number)?.toDouble() ?: return null
            if (!totalFare.isFinite() || totalFare <= 0.0) return null

            val totalCapacity = (data[FIELD_TOTAL_CAPACITY_SEATS] as? Number)?.toInt() ?: return null
            val available = (data[FIELD_AVAILABLE_SEATS] as? Number)?.toInt() ?: return null
            // Reject corrupt inventory instead of coercing it into a plausible-looking seat count.
            if (totalCapacity !in MIN_SEATS..MAX_SEATS || available !in 0..totalCapacity) return null
            val queueCount = ((data[FIELD_ACTIVE_QUEUE_COUNT] as? Number)?.toInt() ?: 0).takeIf { it >= 0 } ?: return null
            val queueSequence = ((data[FIELD_QUEUE_SEQUENCE] as? Number)?.toInt() ?: queueCount).takeIf { it >= queueCount } ?: return null
            // Missing active state is unknown; never silently advertise an unverified route as active.
            val isActive = data[FIELD_IS_ACTIVE] as? Boolean ?: return null
            val isLegallyVerified = (data[FIELD_IS_LEGALLY_VERIFIED] as? Boolean) ?: false
            // Older route documents may contain a public registration plate. Do not surface it
            // through the route model; new route writes never include this field.
            return LiveAutoRoute(
                postingId = postingId,
                driverUid = driverUid,
                driverAutoRegNumber = null,
                pickupStation = pickupStation,
                pickupSide = pickupSide,
                designatedStand = designatedStand,
                destination = destination,
                viaPoints = viaPoints,
                totalBookingFare = totalFare,
                totalCapacitySeats = totalCapacity,
                availableSeats = available,
                activeQueueCount = queueCount,
                queueSequence = queueSequence,
                isActive = isActive,
                isLegallyVerified = isLegallyVerified,
                createdAtMs = createdAtMs,
                updatedAtMs = updatedAtMs
            )
        }
    }
}

/**
 * Atomic passenger seat-lock / queue entry stored under
 * `/active_routes/{postingId}/seat_locks/{passengerUid}`.
 */
data class RouteSeatLock(
    val lockId: String,
    val postingId: String,
    val passengerUid: String,
    val driverUid: String,
    val negotiationId: String? = null,
    val seatsLocked: Int,
    val agreedTotalFare: Double,
    val queuePosition: Int,
    val status: String = STATUS_LOCKED,
    val lockedAtMs: Long? = null,
    val updatedAtMs: Long? = null
) {
    fun toBaseFirestoreFields(): Map<String, Any> = linkedMapOf<String, Any>(
        FIELD_LOCK_ID to lockId,
        FIELD_POSTING_ID to postingId,
        FIELD_PASSENGER_UID to passengerUid,
        FIELD_DRIVER_UID to driverUid,
        FIELD_SEATS_LOCKED to seatsLocked,
        FIELD_AGREED_TOTAL_FARE to agreedTotalFare,
        FIELD_QUEUE_POSITION to queuePosition,
        FIELD_STATUS to status
    ).apply {
        negotiationId?.takeIf { it.isNotBlank() }?.let { put(FIELD_NEGOTIATION_ID, it) }
    }

    companion object {
        const val STATUS_LOCKED = "LOCKED"
        const val STATUS_RELEASED = "RELEASED"

        const val FIELD_LOCK_ID = "lockId"
        const val FIELD_POSTING_ID = "postingId"
        const val FIELD_PASSENGER_UID = "passengerUid"
        const val FIELD_DRIVER_UID = "driverUid"
        const val FIELD_NEGOTIATION_ID = "negotiationId"
        const val FIELD_SEATS_LOCKED = "seatsLocked"
        const val FIELD_AGREED_TOTAL_FARE = "agreedTotalFare"
        const val FIELD_QUEUE_POSITION = "queuePosition"
        const val FIELD_STATUS = "status"
        const val FIELD_LOCKED_AT = "lockedAt"
        const val FIELD_UPDATED_AT = "updatedAt"

        fun fromFirestoreFields(
            documentId: String,
            data: Map<String, Any?>,
            lockedAtMs: Long? = null,
            updatedAtMs: Long? = null
        ): RouteSeatLock? {
            val lockId = (data[FIELD_LOCK_ID] as? String)?.trim()?.ifEmpty { null }
                ?: documentId.trim().ifEmpty { null }
                ?: return null
            val postingId = (data[FIELD_POSTING_ID] as? String)?.trim()?.ifEmpty { null } ?: return null
            val passengerUid = (data[FIELD_PASSENGER_UID] as? String)?.trim()?.ifEmpty { null } ?: return null
            val driverUid = (data[FIELD_DRIVER_UID] as? String)?.trim()?.ifEmpty { null } ?: return null
            val seatsLocked = (data[FIELD_SEATS_LOCKED] as? Number)?.toInt() ?: return null
            val agreedFare = (data[FIELD_AGREED_TOTAL_FARE] as? Number)?.toDouble() ?: return null
            val queuePosition = (data[FIELD_QUEUE_POSITION] as? Number)?.toInt() ?: 1
            val status = (data[FIELD_STATUS] as? String)?.trim()?.ifEmpty { null } ?: STATUS_LOCKED

            return RouteSeatLock(
                lockId = lockId,
                postingId = postingId,
                passengerUid = passengerUid,
                driverUid = driverUid,
                negotiationId = (data[FIELD_NEGOTIATION_ID] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                seatsLocked = seatsLocked,
                agreedTotalFare = agreedFare,
                queuePosition = queuePosition,
                status = status,
                lockedAtMs = lockedAtMs,
                updatedAtMs = updatedAtMs
            )
        }
    }
}

data class PublishRouteCommand(
    val driverUid: String,
    val driverAutoRegNumber: String? = null,
    val pickupStation: String,
    val pickupSide: StationSide?,
    val designatedStand: String,
    val destination: String,
    val viaPoints: List<String> = emptyList(),
    val totalBookingFare: Double,
    val availableSeats: Int
)

sealed class LiveRoutesStreamState {
    data class NotConfigured(
        val message: String = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE
    ) : LiveRoutesStreamState()

    data class AuthRequired(
        val message: String = "Sign in with +91 Mobile OTP to see real-time auto availability, queue updates, and lock a seat."
    ) : LiveRoutesStreamState()

    data object Connecting : LiveRoutesStreamState()

    data class Live(
        val routes: List<LiveAutoRoute>
    ) : LiveRoutesStreamState()

    data class Error(
        val message: String,
        val diagnosticJson: String? = null
    ) : LiveRoutesStreamState()
}

sealed class FirestoreActionResult<out T> {
    data class Success<T>(val data: T) : FirestoreActionResult<T>()
    data class Failure(
        val message: String,
        val isDoubleBookingConflict: Boolean = false,
        val diagnosticJson: String? = null
    ) : FirestoreActionResult<Nothing>()
}

/**
 * Pure JVM validator & atomic seat-lock transition engine shared between the
 * live Firestore transaction handler and unit tests verifying race-condition safety.
 */
object LiveRouteTransactionEngine {

    fun validatePublishCommand(
        command: PublishRouteCommand
    ): FirestoreActionResult<PublishRouteCommand> {
        val uid = command.driverUid.trim()
        if (uid.isEmpty() || !AuthDomainValidator.UID_REGEX.matches(uid)) {
            return FirestoreActionResult.Failure("Signed-in Firebase UID is required to publish a live route.")
        }
        val station = command.pickupStation.trim()
        if (station.length < 2) {
            return FirestoreActionResult.Failure("Select or enter a valid pickup railway station.")
        }
        val side = command.pickupSide
            ?: return FirestoreActionResult.Failure("Select East or West station side before publishing.")
        val stand = command.designatedStand.trim()
        if (stand.length < 3) {
            return FirestoreActionResult.Failure("Designated station stand must be at least 3 characters.")
        }
        val dest = command.destination.trim()
        if (dest.length < 3) {
            return FirestoreActionResult.Failure("Destination must be at least 3 characters.")
        }
        if (stand.equals(dest, ignoreCase = true)) {
            return FirestoreActionResult.Failure("Designated pickup stand and destination cannot be identical.")
        }
        if (!command.totalBookingFare.isFinite() || command.totalBookingFare <= 0.0) {
            return FirestoreActionResult.Failure("Total booking fare must be a positive finite amount.")
        }
        if (command.availableSeats !in LiveAutoRoute.MIN_SEATS..LiveAutoRoute.MAX_SEATS) {
            return FirestoreActionResult.Failure(
                "Available seats must be between ${LiveAutoRoute.MIN_SEATS} and ${LiveAutoRoute.MAX_SEATS}."
            )
        }

        val cleanCommand = command.copy(
            driverUid = uid,
            pickupStation = station,
            pickupSide = side,
            designatedStand = stand,
            destination = dest,
            viaPoints = command.viaPoints.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(10)
        )
        return FirestoreActionResult.Success(cleanCommand)
    }

    fun buildNewLiveRoute(
        postingId: String,
        command: PublishRouteCommand,
        nowMs: Long = System.currentTimeMillis()
    ): FirestoreActionResult<LiveAutoRoute> {
        val cleanPostingId = postingId.trim()
        if (cleanPostingId.isEmpty() || !AuthDomainValidator.UID_REGEX.matches(cleanPostingId)) {
            return FirestoreActionResult.Failure("Invalid route posting ID.")
        }
        val validated = when (val res = validatePublishCommand(command)) {
            is FirestoreActionResult.Failure -> return res
            is FirestoreActionResult.Success -> res.data
        }
        val route = LiveAutoRoute(
            postingId = cleanPostingId,
            driverUid = validated.driverUid,
            driverAutoRegNumber = validated.driverAutoRegNumber
                ?.let { AuthDomainValidator.normalizeAutoRegNumber(it) }
                ?.takeIf { AuthDomainValidator.INDIAN_AUTO_REG_REGEX.matches(it) },
            pickupStation = validated.pickupStation,
            pickupSide = validated.pickupSide ?: return FirestoreActionResult.Failure("Station side is required."),
            designatedStand = validated.designatedStand,
            destination = validated.destination,
            viaPoints = validated.viaPoints,
            totalBookingFare = validated.totalBookingFare,
            totalCapacitySeats = validated.availableSeats,
            availableSeats = validated.availableSeats,
            activeQueueCount = 0,
            queueSequence = 0,
            isActive = true,
            isLegallyVerified = false,
            createdAtMs = nowMs,
            updatedAtMs = nowMs
        )
        return FirestoreActionResult.Success(route)
    }

    data class SeatLockTransitionOutput(
        val updatedRoute: LiveAutoRoute,
        val createdOrUpdatedLock: RouteSeatLock
    )

    /**
     * Evaluates an atomic seat lock inside a transaction so concurrent requests cannot
     * double-book the same remaining seat(s) or allow a user to lock their own route.
     */
    fun computeSeatLockTransition(
        currentRoute: LiveAutoRoute,
        existingLock: RouteSeatLock?,
        passengerUid: String,
        seatsRequested: Int,
        agreedTotalFare: Double? = null,
        negotiationId: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): FirestoreActionResult<SeatLockTransitionOutput> {
        val cleanPassengerUid = passengerUid.trim()
        if (cleanPassengerUid.isEmpty() || !AuthDomainValidator.UID_REGEX.matches(cleanPassengerUid)) {
            return FirestoreActionResult.Failure("Sign in with +91 Mobile OTP before locking a seat.")
        }
        if (!currentRoute.isActive) {
            return FirestoreActionResult.Failure(
                message = "This share-auto route is no longer active.",
                isDoubleBookingConflict = true
            )
        }
        if (currentRoute.driverUid == cleanPassengerUid) {
            return FirestoreActionResult.Failure("Drivers cannot lock a seat on their own posted route.")
        }
        if (seatsRequested !in LiveAutoRoute.MIN_SEATS..LiveAutoRoute.MAX_SEATS) {
            return FirestoreActionResult.Failure(
                "Requested seats must be between ${LiveAutoRoute.MIN_SEATS} and ${LiveAutoRoute.MAX_SEATS}."
            )
        }
        if (existingLock != null && existingLock.status == RouteSeatLock.STATUS_LOCKED) {
            return FirestoreActionResult.Failure(
                message = "You already locked ${existingLock.seatsLocked} seat(s) on this auto (Queue #${existingLock.queuePosition}).",
                isDoubleBookingConflict = true
            )
        }
        if (currentRoute.availableSeats <= 0) {
            return FirestoreActionResult.Failure(
                message = "No seats remaining — another passenger just locked the last available seat.",
                isDoubleBookingConflict = true
            )
        }
        if (seatsRequested > currentRoute.availableSeats) {
            return FirestoreActionResult.Failure(
                message = "Only ${currentRoute.availableSeats} seat(s) left on this auto right now.",
                isDoubleBookingConflict = true
            )
        }

        val nextAvailableSeats = currentRoute.availableSeats - seatsRequested
        val nextQueueCount = currentRoute.activeQueueCount + 1
        val nextQueueSequence = currentRoute.queueSequence + 1

        val updatedRoute = currentRoute.copy(
            availableSeats = nextAvailableSeats,
            activeQueueCount = nextQueueCount,
            queueSequence = nextQueueSequence,
            updatedAtMs = nowMs
        )
        val lock = RouteSeatLock(
            lockId = cleanPassengerUid,
            postingId = currentRoute.postingId,
            passengerUid = cleanPassengerUid,
            driverUid = currentRoute.driverUid,
            negotiationId = negotiationId?.trim()?.takeIf { it.isNotEmpty() },
            seatsLocked = seatsRequested,
            agreedTotalFare = agreedTotalFare ?: currentRoute.totalBookingFare,
            queuePosition = nextQueueSequence,
            status = RouteSeatLock.STATUS_LOCKED,
            lockedAtMs = nowMs,
            updatedAtMs = nowMs
        )
        return FirestoreActionResult.Success(SeatLockTransitionOutput(updatedRoute, lock))
    }

    fun computeSeatReleaseTransition(
        currentRoute: LiveAutoRoute,
        existingLock: RouteSeatLock?,
        passengerUid: String,
        nowMs: Long = System.currentTimeMillis()
    ): FirestoreActionResult<SeatLockTransitionOutput> {
        val cleanPassengerUid = passengerUid.trim()
        if (existingLock == null || existingLock.passengerUid != cleanPassengerUid) {
            return FirestoreActionResult.Failure("No active seat lock found for your account on this route.")
        }
        if (existingLock.status != RouteSeatLock.STATUS_LOCKED) {
            return FirestoreActionResult.Failure("This seat lock has already been released.")
        }
        val restoredSeats = (currentRoute.availableSeats + existingLock.seatsLocked)
            .coerceAtMost(currentRoute.totalCapacitySeats)
        val updatedRoute = currentRoute.copy(
            availableSeats = restoredSeats,
            activeQueueCount = (currentRoute.activeQueueCount - 1).coerceAtLeast(0),
            updatedAtMs = nowMs
        )
        val releasedLock = existingLock.copy(
            status = RouteSeatLock.STATUS_RELEASED,
            updatedAtMs = nowMs
        )
        return FirestoreActionResult.Success(SeatLockTransitionOutput(updatedRoute, releasedLock))
    }
}

interface LiveRoutesDataSource {
    val isFirestoreConfigured: Boolean
    fun observeActiveRoutes(): Flow<LiveRoutesStreamState>
    fun observeDriverActiveRoutes(driverUid: String): Flow<List<LiveAutoRoute>>
    fun observeRouteQueueLocks(postingId: String): Flow<List<RouteSeatLock>>
    suspend fun publishActiveRoute(command: PublishRouteCommand): FirestoreActionResult<LiveAutoRoute>
    suspend fun lockSeatTransaction(
        postingId: String,
        passengerUid: String,
        seatsRequested: Int,
        agreedTotalFare: Double? = null,
        negotiationId: String? = null
    ): FirestoreActionResult<RouteSeatLock>
    suspend fun releaseSeatTransaction(
        postingId: String,
        passengerUid: String
    ): FirestoreActionResult<Unit>
    suspend fun deactivateDriverRoute(
        postingId: String,
        driverUid: String
    ): FirestoreActionResult<Unit>
}

private class SeatLockConflictException(
    val userMessage: String,
    val isDoubleBookingConflict: Boolean
) : RuntimeException(userMessage)

/**
 * Live Cloud Firestore repository for Mumbai Share Auto routes, queues, and bookings.
 *
 * Capabilities:
 * - Real-time snapshot listeners (`callbackFlow` + `addSnapshotListener`) on `/active_routes`
 *   and `/active_routes/{postingId}/seat_locks`.
 * - Atomic Cloud Firestore `runTransaction` seat-locking to guarantee zero double-booking.
 * - Safe unconfigured fallback when `google-services.json` is not present (never crashes or mocks).
 */
class FirebaseFirestoreRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LiveRoutesDataSource {

    override val isFirestoreConfigured: Boolean
        get() = FirebaseRuntimeInspector.isFirebaseInitialized(context)

    private val firestore: FirebaseFirestore?
        get() = if (isFirestoreConfigured) runCatching { FirebaseFirestore.getInstance() }.getOrNull() else null

    private val auth: FirebaseAuth?
        get() = if (isFirestoreConfigured) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null

    override fun observeActiveRoutes(): Flow<LiveRoutesStreamState> = callbackFlow {
        val db = firestore
        val firebaseAuth = auth
        if (db == null || firebaseAuth == null) {
            trySend(LiveRoutesStreamState.NotConfigured())
            awaitClose { }
            return@callbackFlow
        }

        var routeRegistration: ListenerRegistration? = null

        fun attachRoutesListener() {
            routeRegistration?.remove()
            trySend(LiveRoutesStreamState.Connecting)
            routeRegistration = db.collection(LiveAutoRoute.COLLECTION_ACTIVE_ROUTES)
                .whereEqualTo(LiveAutoRoute.FIELD_IS_ACTIVE, true)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        val info = buildDiagnostic(
                            error = error,
                            operation = FirestoreOperationType.LIST,
                            path = LiveAutoRoute.COLLECTION_ACTIVE_ROUTES
                        )
                        trySend(
                            LiveRoutesStreamState.Error(
                                message = mapFirestoreException(error),
                                diagnosticJson = info.toDiagnosticJson()
                            )
                        )
                        return@addSnapshotListener
                    }

                    val parsed = snapshot?.documents.orEmpty().mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        LiveAutoRoute.fromFirestoreFields(
                            documentId = doc.id,
                            data = data,
                            createdAtMs = (doc.getTimestamp(LiveAutoRoute.FIELD_CREATED_AT) as? Timestamp)?.toDate()?.time,
                            updatedAtMs = (doc.getTimestamp(LiveAutoRoute.FIELD_UPDATED_AT) as? Timestamp)?.toDate()?.time
                        )
                    }.sortedByDescending { it.updatedAtMs ?: it.createdAtMs ?: 0L }

                    trySend(LiveRoutesStreamState.Live(parsed))
                }
        }

        val authListener = FirebaseAuth.AuthStateListener { currentAuth ->
            val currentUser = currentAuth.currentUser
            if (currentUser == null) {
                routeRegistration?.remove()
                routeRegistration = null
                trySend(LiveRoutesStreamState.AuthRequired())
            } else if (routeRegistration == null) {
                attachRoutesListener()
            }
        }

        if (firebaseAuth.currentUser != null) {
            attachRoutesListener()
        } else {
            trySend(LiveRoutesStreamState.AuthRequired())
        }
        firebaseAuth.addAuthStateListener(authListener)

        awaitClose {
            routeRegistration?.remove()
            firebaseAuth.removeAuthStateListener(authListener)
        }
    }

    override fun observeDriverActiveRoutes(driverUid: String): Flow<List<LiveAutoRoute>> = callbackFlow {
        val cleanUid = driverUid.trim()
        val db = firestore
        if (db == null || cleanUid.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = db.collection(LiveAutoRoute.COLLECTION_ACTIVE_ROUTES)
            .whereEqualTo(LiveAutoRoute.FIELD_DRIVER_UID, cleanUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val parsed = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    LiveAutoRoute.fromFirestoreFields(
                        documentId = doc.id,
                        data = data,
                        createdAtMs = (doc.getTimestamp(LiveAutoRoute.FIELD_CREATED_AT) as? Timestamp)?.toDate()?.time,
                        updatedAtMs = (doc.getTimestamp(LiveAutoRoute.FIELD_UPDATED_AT) as? Timestamp)?.toDate()?.time
                    )
                }.sortedByDescending { it.updatedAtMs ?: it.createdAtMs ?: 0L }

                trySend(parsed)
            }

        awaitClose {
            registration.remove()
        }
    }

    override fun observeRouteQueueLocks(postingId: String): Flow<List<RouteSeatLock>> = callbackFlow {
        val cleanPostingId = postingId.trim()
        val db = firestore
        if (db == null || cleanPostingId.isEmpty() || auth?.currentUser == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val registration = db.collection(LiveAutoRoute.COLLECTION_ACTIVE_ROUTES)
            .document(cleanPostingId)
            .collection(LiveAutoRoute.SUBCOLLECTION_SEAT_LOCKS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val parsed = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    RouteSeatLock.fromFirestoreFields(
                        documentId = doc.id,
                        data = data,
                        lockedAtMs = (doc.getTimestamp(RouteSeatLock.FIELD_LOCKED_AT) as? Timestamp)?.toDate()?.time,
                        updatedAtMs = (doc.getTimestamp(RouteSeatLock.FIELD_UPDATED_AT) as? Timestamp)?.toDate()?.time
                    )
                }
                    .filter { it.status == RouteSeatLock.STATUS_LOCKED }
                    .sortedBy { it.queuePosition }

                trySend(parsed)
            }

        awaitClose {
            registration.remove()
        }
    }

    override suspend fun publishActiveRoute(
        command: PublishRouteCommand
    ): FirestoreActionResult<LiveAutoRoute> = withContext(ioDispatcher) {
        val db = firestore ?: return@withContext FirestoreActionResult.Failure(
            message = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE
        )
        val signedInUid = auth?.currentUser?.uid
        if (signedInUid.isNullOrBlank() || signedInUid != command.driverUid.trim()) {
            return@withContext FirestoreActionResult.Failure(
                message = "Sign in with your Driver +91 Mobile OTP account before publishing a live route."
            )
        }

        val postingId = "ROUTE_${UUID.randomUUID().toString().replace("-", "").take(14).uppercase()}"
        val builtRoute = when (val res = LiveRouteTransactionEngine.buildNewLiveRoute(postingId, command)) {
            is FirestoreActionResult.Failure -> return@withContext res
            is FirestoreActionResult.Success -> res.data
        }
        val path = "${LiveAutoRoute.COLLECTION_ACTIVE_ROUTES}/$postingId"

        try {
            val payload = builtRoute.toBaseFirestoreFields().toMutableMap().apply {
                put(LiveAutoRoute.FIELD_CREATED_AT, FieldValue.serverTimestamp())
                put(LiveAutoRoute.FIELD_UPDATED_AT, FieldValue.serverTimestamp())
            }
            val docRef = db.collection(LiveAutoRoute.COLLECTION_ACTIVE_ROUTES).document(postingId)
            docRef.set(payload).await()
            val savedSnap = docRef.get().await()
            val savedData = savedSnap.data
            val parsed = if (savedData != null) {
                LiveAutoRoute.fromFirestoreFields(
                    documentId = postingId,
                    data = savedData,
                    createdAtMs = (savedSnap.getTimestamp(LiveAutoRoute.FIELD_CREATED_AT) as? Timestamp)?.toDate()?.time,
                    updatedAtMs = (savedSnap.getTimestamp(LiveAutoRoute.FIELD_UPDATED_AT) as? Timestamp)?.toDate()?.time
                )
            } else {
                null
            }
            FirestoreActionResult.Success(parsed ?: builtRoute)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            failureFromException(
                exception = e,
                operation = FirestoreOperationType.CREATE,
                path = path
            )
        }
    }

    override suspend fun lockSeatTransaction(
        postingId: String,
        passengerUid: String,
        seatsRequested: Int,
        agreedTotalFare: Double?,
        negotiationId: String?
    ): FirestoreActionResult<RouteSeatLock> = withContext(ioDispatcher) {
        val cleanPostingId = postingId.trim()
        val cleanPassengerUid = passengerUid.trim()
        if (cleanPostingId.isEmpty() || cleanPassengerUid.isEmpty()) {
            return@withContext FirestoreActionResult.Failure("Route ID and Passenger UID are required.")
        }
        val cleanNegotiationId = negotiationId?.trim()?.takeIf { it.isNotEmpty() }
            ?: return@withContext FirestoreActionResult.Failure(
                "A confirmed fare negotiation is required before reserving seats."
            )
        if (agreedTotalFare == null || !agreedTotalFare.isFinite() || agreedTotalFare <= 0.0) {
            return@withContext FirestoreActionResult.Failure(
                "A positive, confirmed whole-group fare is required before reserving seats."
            )
        }
        val db = firestore ?: return@withContext FirestoreActionResult.Failure(
            message = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE
        )
        val signedInUid = auth?.currentUser?.uid
        if (signedInUid.isNullOrBlank() || signedInUid != cleanPassengerUid) {
            return@withContext FirestoreActionResult.Failure(
                message = "Sign in with your +91 Mobile OTP account to lock a seat."
            )
        }

        val routeRef = db.collection(LiveAutoRoute.COLLECTION_ACTIVE_ROUTES).document(cleanPostingId)
        val lockRef = routeRef.collection(LiveAutoRoute.SUBCOLLECTION_SEAT_LOCKS).document(cleanPassengerUid)
        val negotiationRef = db.collection(FareNegotiationFirestoreRepository.COLLECTION).document(cleanNegotiationId)
        val path = "${LiveAutoRoute.COLLECTION_ACTIVE_ROUTES}/$cleanPostingId/${LiveAutoRoute.SUBCOLLECTION_SEAT_LOCKS}/$cleanPassengerUid"

        try {
            val resultLock = db.runTransaction { tx ->
                val routeSnap = tx.get(routeRef)
                val routeData = routeSnap.data
                    ?: throw SeatLockConflictException("Selected share-auto route no longer exists.", true)
                val currentRoute = LiveAutoRoute.fromFirestoreFields(
                    documentId = routeSnap.id,
                    data = routeData
                ) ?: throw SeatLockConflictException("Route document data is invalid.", false)

                val existingLockSnap = tx.get(lockRef)
                val negotiationSnap = tx.get(negotiationRef)
                val negotiation = negotiationSnap.data
                    ?: throw SeatLockConflictException("Confirmed fare negotiation was not found.", false)
                val negotiatedFare = (negotiation["agreedTotalFareInr"] as? Number)?.toDouble()
                val negotiationValid =
                    negotiationSnap.exists() &&
                    negotiation["bookingId"] == cleanNegotiationId &&
                    negotiation["status"] == "AGREED" &&
                    negotiation["passengerUid"] == cleanPassengerUid &&
                    negotiation["driverUid"] == currentRoute.driverUid &&
                    negotiation["routeId"] == cleanPostingId &&
                    (negotiation["passengerCount"] as? Number)?.toInt() == seatsRequested &&
                    negotiatedFare != null && negotiatedFare.isFinite() &&
                    kotlin.math.abs(negotiatedFare - agreedTotalFare) < 0.000001
                if (!negotiationValid) {
                    throw SeatLockConflictException(
                        "This fare agreement is not valid for your account, route, passenger count or agreed total.",
                        false
                    )
                }
                val existingLock = if (existingLockSnap.exists()) {
                    existingLockSnap.data?.let { lockData ->
                        RouteSeatLock.fromFirestoreFields(existingLockSnap.id, lockData)
                    }
                } else {
                    null
                }

                val routeForFare = currentRoute.copy(totalBookingFare = agreedTotalFare)
                when (val transition = LiveRouteTransactionEngine.computeSeatLockTransition(
                    currentRoute = routeForFare,
                    existingLock = existingLock,
                    passengerUid = cleanPassengerUid,
                    seatsRequested = seatsRequested,
                    agreedTotalFare = agreedTotalFare,
                    negotiationId = cleanNegotiationId
                )) {
                    is FirestoreActionResult.Failure -> {
                        throw SeatLockConflictException(
                            userMessage = transition.message,
                            isDoubleBookingConflict = transition.isDoubleBookingConflict
                        )
                    }
                    is FirestoreActionResult.Success -> {
                        val output = transition.data
                        val routeUpdates = mapOf<String, Any>(
                            LiveAutoRoute.FIELD_AVAILABLE_SEATS to output.updatedRoute.availableSeats,
                            LiveAutoRoute.FIELD_ACTIVE_QUEUE_COUNT to output.updatedRoute.activeQueueCount,
                            LiveAutoRoute.FIELD_QUEUE_SEQUENCE to output.updatedRoute.queueSequence,
                            LiveAutoRoute.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                        )
                        val lockPayload = output.createdOrUpdatedLock.toBaseFirestoreFields().toMutableMap().apply {
                            put(RouteSeatLock.FIELD_LOCKED_AT, FieldValue.serverTimestamp())
                            put(RouteSeatLock.FIELD_UPDATED_AT, FieldValue.serverTimestamp())
                        }
                        tx.update(routeRef, routeUpdates)
                        tx.set(lockRef, lockPayload)
                        output.createdOrUpdatedLock
                    }
                }
            }.await()

            FirestoreActionResult.Success(resultLock)
        } catch (c: CancellationException) {
            throw c
        } catch (conflict: SeatLockConflictException) {
            FirestoreActionResult.Failure(
                message = conflict.userMessage,
                isDoubleBookingConflict = conflict.isDoubleBookingConflict
            )
        } catch (e: Exception) {
            failureFromException(
                exception = e,
                operation = FirestoreOperationType.WRITE,
                path = path
            )
        }
    }

    override suspend fun releaseSeatTransaction(
        postingId: String,
        passengerUid: String
    ): FirestoreActionResult<Unit> = withContext(ioDispatcher) {
        val cleanPostingId = postingId.trim()
        val cleanPassengerUid = passengerUid.trim()
        val db = firestore ?: return@withContext FirestoreActionResult.Failure(
            message = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE
        )
        if (cleanPostingId.isEmpty() || cleanPassengerUid.isEmpty()) {
            return@withContext FirestoreActionResult.Failure("Route ID and Passenger UID are required.")
        }
        val signedInUid = auth?.currentUser?.uid
        if (signedInUid.isNullOrBlank() || signedInUid != cleanPassengerUid) {
            return@withContext FirestoreActionResult.Failure(
                message = "Sign in with the same passenger account that locked this seat before releasing it."
            )
        }

        val routeRef = db.collection(LiveAutoRoute.COLLECTION_ACTIVE_ROUTES).document(cleanPostingId)
        val lockRef = routeRef.collection(LiveAutoRoute.SUBCOLLECTION_SEAT_LOCKS).document(cleanPassengerUid)
        val path = "${LiveAutoRoute.COLLECTION_ACTIVE_ROUTES}/$cleanPostingId/${LiveAutoRoute.SUBCOLLECTION_SEAT_LOCKS}/$cleanPassengerUid"

        try {
            db.runTransaction { tx ->
                val routeSnap = tx.get(routeRef)
                val routeData = routeSnap.data
                    ?: throw SeatLockConflictException("Selected share-auto route no longer exists.", false)
                val currentRoute = LiveAutoRoute.fromFirestoreFields(routeSnap.id, routeData)
                    ?: throw SeatLockConflictException("Route data invalid.", false)

                val lockSnap = tx.get(lockRef)
                val existingLock = if (lockSnap.exists()) {
                    lockSnap.data?.let { RouteSeatLock.fromFirestoreFields(lockSnap.id, it) }
                } else {
                    null
                }

                when (val transition = LiveRouteTransactionEngine.computeSeatReleaseTransition(
                    currentRoute = currentRoute,
                    existingLock = existingLock,
                    passengerUid = cleanPassengerUid
                )) {
                    is FirestoreActionResult.Failure -> {
                        throw SeatLockConflictException(transition.message, false)
                    }
                    is FirestoreActionResult.Success -> {
                        val output = transition.data
                        tx.update(
                            routeRef,
                            mapOf(
                                LiveAutoRoute.FIELD_AVAILABLE_SEATS to output.updatedRoute.availableSeats,
                                LiveAutoRoute.FIELD_ACTIVE_QUEUE_COUNT to output.updatedRoute.activeQueueCount,
                                LiveAutoRoute.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                            )
                        )
                        tx.update(
                            lockRef,
                            mapOf(
                                RouteSeatLock.FIELD_STATUS to RouteSeatLock.STATUS_RELEASED,
                                RouteSeatLock.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }.await()
            FirestoreActionResult.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (conflict: SeatLockConflictException) {
            FirestoreActionResult.Failure(conflict.userMessage)
        } catch (e: Exception) {
            failureFromException(
                exception = e,
                operation = FirestoreOperationType.UPDATE,
                path = path
            )
        }
    }

    override suspend fun deactivateDriverRoute(
        postingId: String,
        driverUid: String
    ): FirestoreActionResult<Unit> = withContext(ioDispatcher) {
        val cleanPostingId = postingId.trim()
        val cleanDriverUid = driverUid.trim()
        val db = firestore ?: return@withContext FirestoreActionResult.Failure(
            message = FirebaseRuntimeInspector.UNCONFIGURED_MESSAGE
        )
        if (cleanPostingId.isEmpty() || cleanDriverUid.isEmpty()) {
            return@withContext FirestoreActionResult.Failure("Route ID and Driver UID are required.")
        }
        val signedInUid = auth?.currentUser?.uid
        if (signedInUid.isNullOrBlank() || signedInUid != cleanDriverUid) {
            return@withContext FirestoreActionResult.Failure(
                message = "Sign in with the driver account that published this route before closing it."
            )
        }
        val path = "${LiveAutoRoute.COLLECTION_ACTIVE_ROUTES}/$cleanPostingId"
        try {
            db.collection(LiveAutoRoute.COLLECTION_ACTIVE_ROUTES)
                .document(cleanPostingId)
                .update(
                    mapOf(
                        LiveAutoRoute.FIELD_IS_ACTIVE to false,
                        LiveAutoRoute.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
                    )
                )
                .await()
            FirestoreActionResult.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            failureFromException(
                exception = e,
                operation = FirestoreOperationType.UPDATE,
                path = path
            )
        }
    }

    private fun buildDiagnostic(
        error: Exception,
        operation: FirestoreOperationType,
        path: String
    ): FirestoreErrorInfo {
        val user = auth?.currentUser
        return FirestoreErrorInfo(
            error = error.message ?: error.javaClass.simpleName,
            operationType = operation,
            path = path,
            uid = user?.uid,
            phoneNumber = user?.phoneNumber
        )
    }

    private fun <T> failureFromException(
        exception: Exception,
        operation: FirestoreOperationType,
        path: String
    ): FirestoreActionResult<T> {
        val info = buildDiagnostic(exception, operation, path)
        val isConflict = exception is FirebaseFirestoreException &&
            (exception.code == FirebaseFirestoreException.Code.ABORTED ||
                exception.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION)
        return FirestoreActionResult.Failure(
            message = mapFirestoreException(exception),
            isDoubleBookingConflict = isConflict,
            diagnosticJson = info.toDiagnosticJson()
        )
    }

    private fun mapFirestoreException(exception: Exception): String = when (exception) {
        is FirebaseFirestoreException -> when (exception.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Cloud Firestore permission denied. Sign in with +91 OTP and deploy the updated firestore.rules."
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.FAILED_PRECONDITION ->
                "Seat availability changed while locking. Refresh live routes and try again."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Cloud Firestore is currently unreachable or offline. Showing local reference data."
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                "Firestore daily quota limit reached."
            else -> exception.message ?: "Cloud Firestore request failed."
        }
        else -> exception.message ?: "Unable to sync with Cloud Firestore."
    }
}
