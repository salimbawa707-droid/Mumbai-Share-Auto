package com.mumbaishareauto.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.mumbaishareauto.app.BookingAgreement
import com.mumbaishareauto.app.BookingLifecycleState
import com.mumbaishareauto.app.PassengerParty
import com.mumbaishareauto.app.ValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Restart-safe persistence & integrity store for BookingAgreement records.
 *
 * Guarantees:
 * - Duplicate booking IDs cannot be registered blindly.
 * - Corrupted/inconsistent records (e.g., tampered locked fare, invalid totals, identical stands)
 *   are rejected upon reload after an app restart instead of hydrating inconsistent state.
 * - Monotonic lifecycle enforcement prevents reverting terminal/confirmed bookings across restarts.
 */
class BookingAgreementRepository internal constructor(
    private val prefs: SharedPreferences
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    private var _corruptedRecordsQuarantined: Int = 0
    val corruptedRecordsQuarantined: Int
        get() = _corruptedRecordsQuarantined

    private val _agreements = MutableStateFlow(loadAgreements())
    val agreements: StateFlow<List<BookingAgreement>> = _agreements.asStateFlow()

    private fun loadAgreements(): List<BookingAgreement> {
        val rawSet = prefs.getStringSet(KEY_BOOKING_AGREEMENTS, emptySet()) ?: emptySet()
        val loaded = mutableMapOf<String, BookingAgreement>()
        var skipped = 0
        rawSet.forEach { entry ->
            val decoded = deserializeAgreement(entry)
            if (decoded == null || decoded.validateIntegrity() is ValidationResult.Invalid) {
                skipped++
            } else {
                val existing = loaded[decoded.bookingId]
                if (existing == null || stateOrder(decoded.state) >= stateOrder(existing.state)) {
                    loaded[decoded.bookingId] = decoded
                } else {
                    skipped++
                }
            }
        }
        _corruptedRecordsQuarantined = skipped
        return loaded.values.toList()
    }

    private fun persist(list: List<BookingAgreement>) {
        prefs.edit()
            .putStringSet(KEY_BOOKING_AGREEMENTS, list.map { serializeAgreement(it) }.toSet())
            .apply()
        _agreements.value = list
    }

    fun registerNewBooking(agreement: BookingAgreement): ValidationResult {
        val integrity = agreement.validateIntegrity()
        if (integrity is ValidationResult.Invalid) {
            return integrity
        }
        val current = _agreements.value
        if (current.any { it.bookingId == agreement.bookingId }) {
            return ValidationResult.Invalid(
                listOf("Duplicate booking ID rejected: ${agreement.bookingId} already exists")
            )
        }
        persist(current + agreement)
        return ValidationResult.Valid
    }

    fun findBooking(bookingId: String): BookingAgreement? {
        return _agreements.value.firstOrNull { it.bookingId == bookingId }
    }

    fun transitionBooking(
        bookingId: String,
        nextState: BookingLifecycleState,
        reason: String? = null
    ): Pair<BookingAgreement?, ValidationResult> {
        val existing = findBooking(bookingId)
            ?: return Pair(
                null,
                ValidationResult.Invalid(listOf("Booking ID $bookingId was not found"))
            )

        val (updated, result) = existing.transitionTo(nextState, reason = reason)
        if (result is ValidationResult.Invalid) {
            return Pair(existing, result)
        }

        val replaced = _agreements.value.map { if (it.bookingId == bookingId) updated else it }
        persist(replaced)
        return Pair(updated, ValidationResult.Valid)
    }

    fun updateTotalFare(
        bookingId: String,
        newTotalFare: Double
    ): Pair<BookingAgreement?, ValidationResult> {
        val existing = findBooking(bookingId)
            ?: return Pair(
                null,
                ValidationResult.Invalid(listOf("Booking ID $bookingId was not found"))
            )

        val (updated, result) = existing.withProposedFareUpdate(newTotalFare)
        if (result is ValidationResult.Invalid) {
            return Pair(existing, result)
        }

        val replaced = _agreements.value.map { if (it.bookingId == bookingId) updated else it }
        persist(replaced)
        return Pair(updated, ValidationResult.Valid)
    }

    fun clearAllAgreements() {
        prefs.edit().remove(KEY_BOOKING_AGREEMENTS).apply()
        _agreements.value = emptyList()
    }

    companion object {
        const val PREFS_NAME = "mumbai_share_auto_bookings"
        const val KEY_BOOKING_AGREEMENTS = "persisted_booking_agreements"

        private fun stateOrder(state: BookingLifecycleState): Int = when (state) {
            BookingLifecycleState.DRAFT -> 0
            BookingLifecycleState.OFFERED -> 1
            BookingLifecycleState.DRIVER_ACCEPTED -> 2
            BookingLifecycleState.PASSENGER_CONFIRMED -> 3
            BookingLifecycleState.LOCKED -> 4
            BookingLifecycleState.DECLINED -> 5
            BookingLifecycleState.CANCELLED -> 6
        }

        private fun escapeField(value: String): String = value
            .replace("\\", "\\\\")
            .replace("|", "\\p")
            .replace("\n", "\\n")

        private fun unescapeField(value: String): String {
            val out = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    when (val next = value[i + 1]) {
                        'p' -> {
                            out.append('|')
                            i += 2
                        }
                        'n' -> {
                            out.append('\n')
                            i += 2
                        }
                        '\\' -> {
                            out.append('\\')
                            i += 2
                        }
                        else -> {
                            out.append(next)
                            i += 2
                        }
                    }
                } else {
                    out.append(c)
                    i++
                }
            }
            return out.toString()
        }

        private fun splitEscapedRecord(raw: String): List<String> {
            val fields = mutableListOf<String>()
            val current = StringBuilder()
            var i = 0
            while (i < raw.length) {
                val c = raw[i]
                if (c == '\\' && i + 1 < raw.length) {
                    current.append(c)
                    current.append(raw[i + 1])
                    i += 2
                } else if (c == '|') {
                    fields.add(unescapeField(current.toString()))
                    current.clear()
                    i++
                } else {
                    current.append(c)
                    i++
                }
            }
            fields.add(unescapeField(current.toString()))
            return fields
        }

        fun serializeAgreement(agreement: BookingAgreement): String {
            return listOf(
                agreement.bookingId,
                agreement.postingId,
                agreement.passengerParty.partyId,
                agreement.passengerParty.passengerCount.toString(),
                agreement.passengerParty.contactRef,
                agreement.agreedTotalFare.toString(),
                agreement.designatedStationStand,
                agreement.destination,
                agreement.state.name,
                agreement.lockedFareInr?.toString() ?: "",
                agreement.cancellationReason ?: ""
            ).joinToString("|") { escapeField(it) }
        }

        fun deserializeAgreement(raw: String): BookingAgreement? {
            val parts = splitEscapedRecord(raw)
            if (parts.size < 11) return null
            return try {
                val state = BookingLifecycleState.entries.firstOrNull { it.name == parts[8] } ?: return null
                val fare = parts[5].toDoubleOrNull() ?: return null
                val count = parts[3].toIntOrNull() ?: return null
                val lockedFare = if (parts[9].isBlank()) {
                    null
                } else {
                    parts[9].toDoubleOrNull() ?: return null
                }
                BookingAgreement(
                    bookingId = parts[0],
                    postingId = parts[1],
                    passengerParty = PassengerParty(
                        partyId = parts[2],
                        passengerCount = count,
                        contactRef = parts[4]
                    ),
                    agreedTotalFare = fare,
                    designatedStationStand = parts[6],
                    destination = parts[7],
                    state = state,
                    lockedFareInr = lockedFare,
                    cancellationReason = parts[10].takeIf { it.isNotBlank() }
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
