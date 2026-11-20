package com.mumbaishareauto.app

import com.mumbaishareauto.app.data.local.BookingAgreementRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive Phase 4 unit tests against production BookingDomain and
 * restart-safe BookingAgreementRepository logic.
 */
class BookingSecurityAndLifecycleTest {

    private val verifiedFixtureCapacity = VehicleCapacityConfig(
        maxPermittedPassengers = 3,
        configurationLabel = "Explicit Test Fixture Capacity",
        isLegallyVerified = true,
        isTestFixture = true
    )

    private fun validAgreement(
        bookingId: String = BookingDomainCoordinator.generateUniqueBookingId("TEST_BK"),
        partyCount: Int = 2,
        totalFare: Double = 120.0,
        state: BookingLifecycleState = BookingLifecycleState.DRAFT,
        lockedFare: Double? = if (state == BookingLifecycleState.LOCKED) totalFare else null
    ): BookingAgreement = BookingAgreement(
        bookingId = bookingId,
        postingId = "POST_BANDRA_01",
        passengerParty = PassengerParty(
            partyId = "PARTY_$partyCount",
            passengerCount = partyCount
        ),
        agreedTotalFare = totalFare,
        designatedStationStand = "Bandra East BKC Auto Bay",
        destination = "BKC Diamond Bourse",
        state = state,
        lockedFareInr = lockedFare
    )

    @Test
    fun exhaustiveStateMachineMatrix_allowsOnlyAuthorizedTransitionsAndBlocksAllUnauthorizedJumps() {
        val allowedMatrix: Map<BookingLifecycleState, Set<BookingLifecycleState>> = mapOf(
            BookingLifecycleState.DRAFT to setOf(
                BookingLifecycleState.OFFERED,
                BookingLifecycleState.CANCELLED
            ),
            BookingLifecycleState.OFFERED to setOf(
                BookingLifecycleState.DRIVER_ACCEPTED,
                BookingLifecycleState.DECLINED,
                BookingLifecycleState.CANCELLED
            ),
            BookingLifecycleState.DRIVER_ACCEPTED to setOf(
                BookingLifecycleState.PASSENGER_CONFIRMED,
                BookingLifecycleState.CANCELLED
            ),
            BookingLifecycleState.PASSENGER_CONFIRMED to setOf(
                BookingLifecycleState.LOCKED,
                BookingLifecycleState.CANCELLED
            ),
            BookingLifecycleState.LOCKED to emptySet(),
            BookingLifecycleState.DECLINED to emptySet(),
            BookingLifecycleState.CANCELLED to emptySet()
        )

        BookingLifecycleState.entries.forEach { fromState ->
            val allowedTargets = allowedMatrix.getValue(fromState)
            BookingLifecycleState.entries.forEach { toState ->
                val base = validAgreement(state = fromState)
                val (resultingAgreement, result) = base.transitionTo(toState)
                if (toState in allowedTargets) {
                    assertTrue(
                        "Transition $fromState -> $toState should be valid",
                        result is ValidationResult.Valid
                    )
                    assertEquals(toState, resultingAgreement.state)
                    if (toState == BookingLifecycleState.LOCKED) {
                        assertEquals(120.0, resultingAgreement.lockedFareInr ?: -1.0, 0.001)
                    } else {
                        assertNull(resultingAgreement.lockedFareInr)
                    }
                } else {
                    assertTrue(
                        "Unauthorized transition $fromState -> $toState must be rejected",
                        result is ValidationResult.Invalid
                    )
                    assertEquals(
                        "Failed transition $fromState -> $toState must preserve original state",
                        fromState,
                        resultingAgreement.state
                    )
                }
            }
        }
    }

    @Test
    fun fareInvariance_partySizes1_2_And_3_KeepExactSameTotalBookingFareForPassengerAndDriver() {
        val posting = DriverRoutePosting(
            postingId = "POST_INV_120",
            driverId = "DRV_MH02_5555",
            designatedPickupStand = "Kurla West Auto Stand",
            destinationStand = "BKC ICICI Tower",
            totalBookingFare = 120.0,
            availableSeats = 3,
            capacityConfig = verifiedFixtureCapacity
        )

        listOf(1, 2, 3).forEach { partySize ->
            val bookingId = BookingDomainCoordinator.generateUniqueBookingId("INV_P$partySize")
            val party = PassengerParty(partyId = "PARTY_$partySize", passengerCount = partySize)

            val (offered, offerRes) = BookingDomainCoordinator.createBookingOffer(bookingId, posting, party)
            assertTrue("Party size $partySize offer must succeed", offerRes is ValidationResult.Valid)
            assertNotNull(offered)

            val (accepted, acceptRes) = BookingDomainCoordinator.driverAcceptBooking(offered!!)
            assertTrue(acceptRes is ValidationResult.Valid)

            val (confirmed, confirmRes) = BookingDomainCoordinator.passengerConfirmBooking(accepted)
            assertTrue(confirmRes is ValidationResult.Valid)

            val (locked, lockRes) = BookingDomainCoordinator.lockFareAfterConfirmation(confirmed)
            assertTrue(lockRes is ValidationResult.Valid)

            assertEquals(120.0, locked.calculateEffectiveFare(), 0.001)
            val snapshot = locked.fareSnapshot()
            assertTrue(snapshot.faresMatchExactly)
            assertTrue(snapshot.isFareLocked)
            assertEquals(120.0, snapshot.passengerVisibleTotalFare, 0.001)
            assertEquals(120.0, snapshot.driverVisibleTotalFare, 0.001)
            assertEquals(120.0, snapshot.lockedFareInr ?: -1.0, 0.001)
            assertEquals(partySize, snapshot.passengerCount)
        }
    }

    @Test
    fun fareSecurity_rejectsInvalidFaresAndPreventsSilentPostAcceptanceOrPostLockFareMutations() {
        val corruptedZero = validAgreement(totalFare = 0.0, state = BookingLifecycleState.DRAFT)
        val (_, zeroAdvanceResult) = BookingDomainCoordinator.submitDraftOffer(corruptedZero)
        assertTrue("Zero fare cannot advance to OFFERED", zeroAdvanceResult is ValidationResult.Invalid)

        val corruptedNaN = validAgreement(totalFare = Double.NaN, state = BookingLifecycleState.DRAFT)
        val (_, nanAdvanceResult) = BookingDomainCoordinator.submitDraftOffer(corruptedNaN)
        assertTrue("NaN fare cannot advance to OFFERED", nanAdvanceResult is ValidationResult.Invalid)

        // Updating fare in DRAFT / OFFERED before driver acceptance is allowed for valid positive finite amounts
        val draft = validAgreement(totalFare = 100.0, state = BookingLifecycleState.DRAFT)
        val (badUpdate, badUpdateRes) = draft.withProposedFareUpdate(-50.0)
        assertTrue(badUpdateRes is ValidationResult.Invalid)
        assertEquals(100.0, badUpdate.agreedTotalFare, 0.001)

        val (validDraftUpdate, validDraftUpdateRes) = draft.withProposedFareUpdate(120.0)
        assertTrue(validDraftUpdateRes is ValidationResult.Valid)
        assertEquals(120.0, validDraftUpdate.agreedTotalFare, 0.001)

        // Advance to DRIVER_ACCEPTED -> PASSENGER_CONFIRMED -> LOCKED
        val (offered, _) = BookingDomainCoordinator.submitDraftOffer(validDraftUpdate)
        val (accepted, _) = BookingDomainCoordinator.driverAcceptBooking(offered)
        val (_, acceptedMutationRes) = accepted.withProposedFareUpdate(180.0)
        assertTrue("Silent fare change must be rejected after DRIVER_ACCEPTED", acceptedMutationRes is ValidationResult.Invalid)

        val (confirmed, _) = BookingDomainCoordinator.passengerConfirmBooking(accepted)
        val (_, confirmedMutationRes) = confirmed.withProposedFareUpdate(200.0)
        assertTrue("Silent fare change must be rejected after PASSENGER_CONFIRMED", confirmedMutationRes is ValidationResult.Invalid)

        val (locked, _) = BookingDomainCoordinator.lockFareAfterConfirmation(confirmed)
        val (unchangedLocked, lockedMutationRes) = locked.withProposedFareUpdate(250.0)
        assertTrue("Fare change after LOCKED must be rejected", lockedMutationRes is ValidationResult.Invalid)
        assertEquals(120.0, unchangedLocked.calculateEffectiveFare(), 0.001)

        // Detect external tampering even if copy modifies agreedTotalFare on LOCKED booking
        val tampered = locked.copy(agreedTotalFare = 999.0)
        val tamperingCheck = tampered.validateIntegrity()
        assertTrue("Tampered LOCKED agreement must fail validateIntegrity()", tamperingCheck is ValidationResult.Invalid)
        assertEquals(120.0, tampered.calculateEffectiveFare(), 0.001)
    }

    @Test
    fun partyIsolation_separatePassengerPartiesNeverAutomaticallyMerge() {
        val party1 = PassengerParty(partyId = "PARTY_SOLO_1", passengerCount = 1)
        val party2 = PassengerParty(partyId = "PARTY_PAIR_2", passengerCount = 2)
        assertFalse(BookingDomainCoordinator.canAutomaticallyMergeParties(party1, party2))
        assertFalse(BookingDomainCoordinator.canAutomaticallyMergeParties(party1, party1))
    }

    @Test
    fun uniqueBookingIds_duplicateConfirmationCancellationAndRestartSafetyWorkEndToEnd() {
        val generatedIds = (1..200).map { BookingDomainCoordinator.generateUniqueBookingId("MSA_BK") }.toSet()
        assertEquals("All 200 generated booking IDs must be strictly unique", 200, generatedIds.size)

        val prefs = InMemorySharedPreferences()
        val repoBeforeRestart = BookingAgreementRepository(prefs)

        val agreement = validAgreement(bookingId = generatedIds.first(), partyCount = 3, totalFare = 120.0)
        assertTrue(repoBeforeRestart.registerNewBooking(agreement) is ValidationResult.Valid)

        // Reject duplicate registration of identical booking ID
        val dupRegResult = repoBeforeRestart.registerNewBooking(agreement)
        assertTrue("Duplicate booking ID registration must fail", dupRegResult is ValidationResult.Invalid)

        // Drive DRAFT -> OFFERED -> DRIVER_ACCEPTED -> PASSENGER_CONFIRMED
        assertTrue(repoBeforeRestart.transitionBooking(agreement.bookingId, BookingLifecycleState.OFFERED).second is ValidationResult.Valid)
        assertTrue(repoBeforeRestart.transitionBooking(agreement.bookingId, BookingLifecycleState.DRIVER_ACCEPTED).second is ValidationResult.Valid)
        assertTrue(repoBeforeRestart.transitionBooking(agreement.bookingId, BookingLifecycleState.PASSENGER_CONFIRMED).second is ValidationResult.Valid)

        // Prevent duplicate confirmation immediately
        val (_, dupConfirm) = repoBeforeRestart.transitionBooking(agreement.bookingId, BookingLifecycleState.PASSENGER_CONFIRMED)
        assertTrue("Duplicate confirmation must be rejected", dupConfirm is ValidationResult.Invalid)
        assertTrue((dupConfirm as ValidationResult.Invalid).summary.contains("Duplicate confirmation"))

        // Lock fare
        val (lockedBeforeRestart, lockRes) = repoBeforeRestart.transitionBooking(agreement.bookingId, BookingLifecycleState.LOCKED)
        assertTrue(lockRes is ValidationResult.Valid)
        assertEquals(120.0, lockedBeforeRestart?.lockedFareInr ?: -1.0, 0.001)

        // Create a second booking and cancel cleanly from DRIVER_ACCEPTED
        val cancelBookingId = BookingDomainCoordinator.generateUniqueBookingId("CANCEL_BK")
        val cancelTarget = validAgreement(bookingId = cancelBookingId, partyCount = 1, totalFare = 90.0)
        assertTrue(repoBeforeRestart.registerNewBooking(cancelTarget) is ValidationResult.Valid)
        assertTrue(repoBeforeRestart.transitionBooking(cancelBookingId, BookingLifecycleState.OFFERED).second is ValidationResult.Valid)
        assertTrue(repoBeforeRestart.transitionBooking(cancelBookingId, BookingLifecycleState.DRIVER_ACCEPTED).second is ValidationResult.Valid)
        val (cancelled, cancelRes) = repoBeforeRestart.transitionBooking(
            cancelBookingId,
            BookingLifecycleState.CANCELLED,
            reason = "Passenger delayed at platform"
        )
        assertTrue(cancelRes is ValidationResult.Valid)
        assertEquals(BookingLifecycleState.CANCELLED, cancelled?.state)
        assertEquals("Passenger delayed at platform", cancelled?.cancellationReason)

        // Simulate full application restart with new repository instance backed by same storage
        val repoAfterRestart = BookingAgreementRepository(prefs)
        assertEquals(0, repoAfterRestart.corruptedRecordsQuarantined)

        val restoredLocked = repoAfterRestart.findBooking(agreement.bookingId)
        assertNotNull(restoredLocked)
        assertEquals(BookingLifecycleState.LOCKED, restoredLocked!!.state)
        assertEquals(120.0, restoredLocked.calculateEffectiveFare(), 0.001)

        // Duplicate confirmation & fare tampering remain blocked even after app restart
        val (_, postRestartDupConfirm) = repoAfterRestart.transitionBooking(agreement.bookingId, BookingLifecycleState.PASSENGER_CONFIRMED)
        assertTrue(postRestartDupConfirm is ValidationResult.Invalid)
        val (_, postRestartFareChange) = repoAfterRestart.updateTotalFare(agreement.bookingId, 200.0)
        assertTrue(postRestartFareChange is ValidationResult.Invalid)
        val (_, postRestartCancelLocked) = repoAfterRestart.transitionBooking(agreement.bookingId, BookingLifecycleState.CANCELLED)
        assertTrue("Cannot cancel an already LOCKED booking", postRestartCancelLocked is ValidationResult.Invalid)

        val restoredCancelled = repoAfterRestart.findBooking(cancelBookingId)
        assertNotNull(restoredCancelled)
        assertEquals(BookingLifecycleState.CANCELLED, restoredCancelled!!.state)
        assertEquals("Passenger delayed at platform", restoredCancelled.cancellationReason)
    }

    @Test
    fun bookingAgreementRepository_quarantinesCorruptedOrInconsistentPersistedStateOnRestart() {
        val prefs = InMemorySharedPreferences()
        val tamperedLocked = BookingAgreementRepository.serializeAgreement(
            BookingAgreement(
                bookingId = "TAMPERED_LOCKED",
                postingId = "P1",
                passengerParty = PassengerParty("PA", 2),
                agreedTotalFare = 999.0,
                designatedStationStand = "Bandra East Stand",
                destination = "BKC",
                state = BookingLifecycleState.LOCKED,
                lockedFareInr = 120.0
            )
        )
        val lockWithoutRecordedFare = BookingAgreementRepository.serializeAgreement(
            BookingAgreement(
                bookingId = "MISSING_LOCK_FARE",
                postingId = "P2",
                passengerParty = PassengerParty("PB", 1),
                agreedTotalFare = 90.0,
                designatedStationStand = "Andheri East Stand",
                destination = "SEEPZ Gate 1",
                state = BookingLifecycleState.LOCKED,
                lockedFareInr = null
            )
        )
        val validPersisted = BookingAgreementRepository.serializeAgreement(
            validAgreement(bookingId = "VALID_RESTORED", partyCount = 2, totalFare = 120.0, state = BookingLifecycleState.LOCKED)
        )

        prefs.edit().putStringSet(
            BookingAgreementRepository.KEY_BOOKING_AGREEMENTS,
            setOf(tamperedLocked, lockWithoutRecordedFare, validPersisted, "GARBAGE_UNPARSEABLE_ROW")
        ).apply()

        val recoveredRepo = BookingAgreementRepository(prefs)
        assertEquals(3, recoveredRepo.corruptedRecordsQuarantined)
        assertEquals(1, recoveredRepo.agreements.value.size)
        assertNotNull(recoveredRepo.findBooking("VALID_RESTORED"))
        assertNull(recoveredRepo.findBooking("TAMPERED_LOCKED"))
        assertNull(recoveredRepo.findBooking("MISSING_LOCK_FARE"))
    }
}
