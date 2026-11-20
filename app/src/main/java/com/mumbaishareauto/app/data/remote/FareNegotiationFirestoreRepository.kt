package com.mumbaishareauto.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mumbaishareauto.app.FareNegotiationActor
import com.mumbaishareauto.app.FareNegotiationEvent
import com.mumbaishareauto.app.FareNegotiationEventType
import com.mumbaishareauto.app.FareNegotiationSession
import com.mumbaishareauto.app.FareNegotiationStatus
import com.mumbaishareauto.app.ValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Authenticated, transaction-backed persistence for passenger/driver fare negotiations.
 *
 * A negotiation is stored at /fare_negotiations/{bookingId}. Every transition reads the
 * latest document inside a Firestore transaction, checks the authenticated participant,
 * applies the pure domain transition, and writes the complete append-only event history.
 */
class FareNegotiationFirestoreRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    fun observeSession(bookingId: String): Flow<FareNegotiationSession?> = callbackFlow {
        val id = bookingId.trim()
        if (id.isEmpty() || auth.currentUser == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val registration: ListenerRegistration = firestore.collection(COLLECTION)
            .document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                } else {
                    trySend(snapshot?.takeIf { it.exists() }?.toSession())
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun createPassengerOffer(
        routeId: String,
        passengerCount: Int,
        pickupStand: String,
        destination: String,
        totalGroupFareInr: Double
    ): Result<FareNegotiationSession> {
        val passengerUid = auth.currentUser?.uid ?: return Result.failure(
            IllegalStateException("Sign in before sending a fare offer.")
        )
        val route = firestore.collection("active_routes").document(routeId.trim()).get().await()
        val driverUid = route.getString("driverUid")
            ?: return Result.failure(IllegalStateException("Driver route was not found."))
        val bookingId = "FARE_${UUID.randomUUID().toString().replace("-", "").take(20).uppercase()}"
        val (session, validation) = FareNegotiationSession.createPassengerOffer(
            bookingId = bookingId,
            routeId = routeId.trim(),
            passengerUid = passengerUid,
            driverUid = driverUid,
            passengerCount = passengerCount,
            pickupStand = pickupStand,
            destination = destination,
            proposedTotalFareInr = totalGroupFareInr,
            nowMs = System.currentTimeMillis()
        )
        if (validation !is ValidationResult.Valid || session == null) {
            return Result.failure(IllegalArgumentException((validation as? ValidationResult.Invalid)?.errors?.joinToString() ?: "Invalid fare offer."))
        }
        return try {
            firestore.collection(COLLECTION).document(bookingId)
                .set(session.toFirestoreMap() + mapOf("createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
                .await()
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun driverAccept(bookingId: String) = transition(bookingId, FareNegotiationActor.DRIVER) {
        it.driverAcceptPassengerOffer(System.currentTimeMillis())
    }

    suspend fun driverCounter(bookingId: String, totalGroupFareInr: Double) = transition(bookingId, FareNegotiationActor.DRIVER) {
        it.driverCounterOffer(totalGroupFareInr, System.currentTimeMillis())
    }

    suspend fun driverDecline(bookingId: String) = transition(bookingId, FareNegotiationActor.DRIVER) {
        it.driverDeclinePassengerOffer(System.currentTimeMillis())
    }

    suspend fun passengerAcceptCounter(bookingId: String) = transition(bookingId, FareNegotiationActor.PASSENGER) {
        it.passengerAcceptDriverCounter(System.currentTimeMillis())
    }

    suspend fun passengerDeclineCounter(bookingId: String) = transition(bookingId, FareNegotiationActor.PASSENGER) {
        it.passengerDeclineDriverCounter(System.currentTimeMillis())
    }

    suspend fun cancel(bookingId: String) = transition(bookingId, null) { session ->
        val actor = when (auth.currentUser?.uid) {
            session.passengerUid -> FareNegotiationActor.PASSENGER
            session.driverUid -> FareNegotiationActor.DRIVER
            else -> return@transition session to ValidationResult.Invalid(listOf("Only this booking's passenger or driver can cancel."))
        }
        session.cancel(actor, System.currentTimeMillis())
    }

    private suspend fun transition(
        bookingId: String,
        requiredActor: FareNegotiationActor?,
        change: (FareNegotiationSession) -> Pair<FareNegotiationSession, ValidationResult>
    ): Result<FareNegotiationSession> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Sign in to continue."))
        val id = bookingId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Booking ID is required."))
        return try {
            val updated = firestore.runTransaction { tx ->
                val ref = firestore.collection(COLLECTION).document(id)
                val snapshot = tx.get(ref)
                val current = snapshot.takeIf { it.exists() }?.toSession()
                    ?: throw IllegalStateException("Fare negotiation was not found.")
                val actor = when (uid) {
                    current.passengerUid -> FareNegotiationActor.PASSENGER
                    current.driverUid -> FareNegotiationActor.DRIVER
                    else -> throw SecurityException("You are not a participant in this fare negotiation.")
                }
                if (requiredActor != null && actor != requiredActor) {
                    throw SecurityException("This action is available only to the ${requiredActor.name.lowercase()}.")
                }
                val (next, validation) = change(current)
                if (validation !is ValidationResult.Valid) {
                    throw IllegalArgumentException((validation as? ValidationResult.Invalid)?.errors?.joinToString() ?: "Invalid fare transition.")
                }
                tx.update(
                    ref,
                    mapOf(
                        "events" to next.events.map { it.toMap() },
                        "status" to next.status.name,
                        "agreedTotalFareInr" to next.agreedTotalFareInr,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                next
            }.await()
            Result.success(updated)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun FareNegotiationSession.toFirestoreMap(): Map<String, Any?> = mapOf(
        "bookingId" to bookingId,
        "routeId" to routeId,
        "passengerUid" to passengerUid,
        "driverUid" to driverUid,
        "passengerCount" to passengerCount,
        "pickupStand" to pickupStand,
        "destination" to destination,
        "events" to events.map { it.toMap() },
        "status" to status.name,
        "agreedTotalFareInr" to agreedTotalFareInr
    )

    private fun FareNegotiationEvent.toMap(): Map<String, Any?> = mapOf(
        "eventId" to eventId,
        "sequence" to sequence,
        "actor" to actor.name,
        "type" to type.name,
        "totalFareInr" to totalFareInr,
        "createdAtEpochMs" to createdAtEpochMs
    )

    private fun DocumentSnapshot.toSession(): FareNegotiationSession {
        val events = (get("events") as? List<*>)?.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val actor = (map["actor"] as? String)?.let { runCatching { FareNegotiationActor.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            val type = (map["type"] as? String)?.let { runCatching { FareNegotiationEventType.valueOf(it) }.getOrNull() } ?: return@mapNotNull null
            FareNegotiationEvent(
                eventId = map["eventId"] as? String ?: return@mapNotNull null,
                sequence = (map["sequence"] as? Number)?.toInt() ?: return@mapNotNull null,
                actor = actor,
                type = type,
                totalFareInr = (map["totalFareInr"] as? Number)?.toDouble(),
                createdAtEpochMs = (map["createdAtEpochMs"] as? Number)?.toLong() ?: return@mapNotNull null
            )
        }.orEmpty()
        return FareNegotiationSession(
            bookingId = getString("bookingId") ?: id,
            routeId = getString("routeId") ?: "",
            passengerUid = getString("passengerUid") ?: "",
            driverUid = getString("driverUid") ?: "",
            passengerCount = getLong("passengerCount")?.toInt() ?: 0,
            pickupStand = getString("pickupStand") ?: "",
            destination = getString("destination") ?: "",
            events = events,
            status = FareNegotiationStatus.valueOf(getString("status") ?: FareNegotiationStatus.OPEN.name),
            agreedTotalFareInr = getDouble("agreedTotalFareInr")
        )
    }

    companion object {
        const val COLLECTION = "fare_negotiations"
    }
}
