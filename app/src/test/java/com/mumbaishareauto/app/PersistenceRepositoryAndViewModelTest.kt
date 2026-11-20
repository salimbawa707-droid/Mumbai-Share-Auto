package com.mumbaishareauto.app

import com.mumbaishareauto.app.data.local.FavoriteRouteRepository
import com.mumbaishareauto.app.data.local.PersistenceResult
import com.mumbaishareauto.app.data.local.RoomBookingRepository
import com.mumbaishareauto.app.data.local.RoomDriverRouteRepository
import com.mumbaishareauto.app.data.local.RoomNotificationsRepository
import com.mumbaishareauto.app.data.local.RoomUserPreferencesRepository
import com.mumbaishareauto.app.data.local.db.BookingDao
import com.mumbaishareauto.app.data.local.db.BookingEntity
import com.mumbaishareauto.app.data.local.db.DriverRouteDao
import com.mumbaishareauto.app.data.local.db.DriverRouteEntity
import com.mumbaishareauto.app.data.local.db.FavoriteRouteDao
import com.mumbaishareauto.app.data.local.db.FavoriteRouteEntity
import com.mumbaishareauto.app.data.local.db.NotificationDao
import com.mumbaishareauto.app.data.local.db.NotificationEntity
import com.mumbaishareauto.app.data.local.db.TransactionRunner
import com.mumbaishareauto.app.data.local.db.UserPreferencesDao
import com.mumbaishareauto.app.data.local.db.UserPreferencesEntity
import com.mumbaishareauto.app.ui.MumbaiShareAutoViewModel
import com.mumbaishareauto.app.ui.theme.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersistenceRepositoryAndViewModelTest {

    @Test
    fun legacyFavoritesMigration_migratesOnceAndStaysIdempotentAcrossRestarts() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val prefs = InMemorySharedPreferences()
        prefs.edit().putStringSet(
            FavoriteRouteRepository.KEY_FAVORITE_ROUTES,
            setOf("w_bnd_e_bkc", "c_ghk_e_powai")
        ).apply()

        val favDao = FakeFavoriteRouteDao()
        val prefsDao = FakeUserPreferencesDao()
        val txRunner = FakeTransactionRunner()
        val scope1 = CoroutineScope(SupervisorJob() + dispatcher)

        val repo1 = FavoriteRouteRepository(
            prefs = prefs,
            favoriteRouteDao = favDao,
            userPreferencesDao = prefsDao,
            transactionRunner = txRunner,
            ioDispatcher = dispatcher,
            externalScope = scope1,
            clockMs = { 1_700_000_000_000L }
        )
        advanceUntilIdle()

        assertEquals(setOf("w_bnd_e_bkc", "c_ghk_e_powai"), repo1.favoriteKeys.value)
        assertTrue(prefsDao.getOnce()?.favoritesMigratedFromPrefs == true)
        assertEquals(
            FavoriteRouteEntity.SOURCE_MIGRATED_PREFS,
            favDao.getByKey("w_bnd_e_bkc")?.sourceScope
        )

        // Delete one migrated favorite, then recreate repository to verify removed key is NOT re-imported
        val removeResult = repo1.removeFavoritePersist("w_bnd_e_bkc")
        assertTrue(removeResult is PersistenceResult.Success)
        assertEquals(setOf("c_ghk_e_powai"), repo1.favoriteKeys.value)
        scope1.cancel()

        val scope2 = CoroutineScope(SupervisorJob() + dispatcher)
        val repo2 = FavoriteRouteRepository(
            prefs = prefs,
            favoriteRouteDao = favDao,
            userPreferencesDao = prefsDao,
            transactionRunner = txRunner,
            ioDispatcher = dispatcher,
            externalScope = scope2
        )
        advanceUntilIdle()

        assertEquals(setOf("c_ghk_e_powai"), repo2.favoriteKeys.value)
        assertFalse(repo2.isFavorite("w_bnd_e_bkc"))
        scope2.cancel()
    }

    @Test
    fun repositoryWriteFailure_returnsFailureAndSurfacesError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val prefs = InMemorySharedPreferences()
        val favDao = FakeFavoriteRouteDao()
        val prefsDao = FakeUserPreferencesDao()
        val failingRunner = FakeTransactionRunner(failOnNextWrite = true)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val repo = FavoriteRouteRepository(
            prefs = prefs,
            favoriteRouteDao = favDao,
            userPreferencesDao = prefsDao,
            transactionRunner = failingRunner,
            ioDispatcher = dispatcher,
            externalScope = scope
        )
        advanceUntilIdle()

        val result = repo.toggleFavoritePersist("h_vsh_w_infotech")
        assertTrue(result is PersistenceResult.Failure)
        assertNotNull(repo.lastError.value)
        assertTrue(repo.favoriteKeys.value.isEmpty())

        repo.clearError()
        assertNull(repo.lastError.value)
        scope.cancel()
    }

    @Test
    fun bookingRepository_enforcesLifecycleAndQuarantinesTamperedRowsAfterRestart() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val bookingDao = FakeBookingDao()
        val prefsDao = FakeUserPreferencesDao()
        val txRunner = FakeTransactionRunner()
        val scope1 = CoroutineScope(SupervisorJob() + dispatcher)

        val repo1 = RoomBookingRepository(
            bookingDao = bookingDao,
            userPreferencesDao = prefsDao,
            transactionRunner = txRunner,
            ioDispatcher = dispatcher,
            externalScope = scope1,
            clockMs = { 1_700_000_001_000L }
        )
        advanceUntilIdle()

        val agreement = BookingAgreement(
            bookingId = "BK_ROOM_01",
            postingId = "POST_ROOM_01",
            passengerParty = PassengerParty(partyId = "PARTY_1", passengerCount = 2),
            agreedTotalFare = 120.0,
            designatedStationStand = "Bandra Station East Auto Stand",
            destination = "BKC Diamond Bourse",
            state = BookingLifecycleState.OFFERED
        )
        assertTrue(repo1.registerNewBooking(agreement) is PersistenceResult.Success)
        // Duplicate ID must fail
        assertTrue(repo1.registerNewBooking(agreement) is PersistenceResult.Failure)

        assertTrue(
            repo1.transitionBooking("BK_ROOM_01", BookingLifecycleState.DRIVER_ACCEPTED)
                is PersistenceResult.Success
        )
        assertTrue(
            repo1.transitionBooking("BK_ROOM_01", BookingLifecycleState.PASSENGER_CONFIRMED)
                is PersistenceResult.Success
        )
        val lockedRes = repo1.transitionBooking("BK_ROOM_01", BookingLifecycleState.LOCKED)
        assertTrue(lockedRes is PersistenceResult.Success)
        assertEquals(120.0, (lockedRes as PersistenceResult.Success).data.lockedFareInr)

        // Inject tampered row directly into underlying storage (simulating corrupt SQLite data before restart)
        bookingDao.injectRawEntityForTest(
            BookingEntity.fromDomain(
                agreement = BookingAgreement(
                    bookingId = "BK_TAMPERED",
                    postingId = "POST_TAMPER",
                    passengerParty = PassengerParty("P_TAMPER", 2),
                    agreedTotalFare = 90.0,
                    designatedStationStand = "Andheri Station East",
                    destination = "SEEPZ Gate",
                    state = BookingLifecycleState.LOCKED,
                    lockedFareInr = 250.0
                ),
                updatedAtMs = 1_700_000_002_000L
            )
        )
        scope1.cancel()

        // Recreate repository after simulated app restart
        val scope2 = CoroutineScope(SupervisorJob() + dispatcher)
        val repo2 = RoomBookingRepository(
            bookingDao = bookingDao,
            userPreferencesDao = prefsDao,
            transactionRunner = txRunner,
            ioDispatcher = dispatcher,
            externalScope = scope2
        )
        advanceUntilIdle()

        assertEquals(1, repo2.bookings.value.size)
        assertEquals("BK_ROOM_01", repo2.bookings.value.first().bookingId)
        assertEquals(BookingLifecycleState.LOCKED, repo2.bookings.value.first().state)
        assertEquals(1, repo2.corruptedRecordsQuarantined.value)
        scope2.cancel()
    }

    @Test
    fun driverRouteRepository_rejectsFakeLiveServerBroadcastAndAcceptsLocalEvaluations() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val driverDao = FakeDriverRouteDao()
        val txRunner = FakeTransactionRunner()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)

        val repo = RoomDriverRouteRepository(
            driverRouteDao = driverDao,
            transactionRunner = txRunner,
            ioDispatcher = dispatcher,
            externalScope = scope
        )
        advanceUntilIdle()

        val fakeLiveBroadcast = DriverRouteEntity(
            postingId = "LIVE_FAKE_1",
            driverId = "DRV_1",
            designatedPickupStand = "Bandra Station East Auto Stand",
            destinationStand = "BKC",
            viaPointsSummary = "Kalanagar",
            totalBookingFare = 120.0,
            availableSeats = 3,
            maxPermittedPassengers = 3,
            isLegallyVerified = false,
            isLiveServerBroadcast = true,
            createdAtMs = 1_700_000_000_000L
        )
        val liveReject = repo.saveLocalRoutePosting(fakeLiveBroadcast)
        assertTrue(liveReject is PersistenceResult.Failure)
        assertTrue(repo.driverRoutes.value.isEmpty())

        val validLocalDraft = fakeLiveBroadcast.copy(
            postingId = "LOCAL_DRAFT_1",
            isLiveServerBroadcast = false
        )
        val localSave = repo.saveLocalRoutePosting(validLocalDraft)
        assertTrue(localSave is PersistenceResult.Success)
        assertEquals(1, repo.driverRoutes.value.size)
        scope.cancel()
    }

    @Test
    fun viewModel_reactivelyUpdatesNotificationsFavoritesAndPreferences() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val prefs = InMemorySharedPreferences()
            val favDao = FakeFavoriteRouteDao()
            val prefsDao = FakeUserPreferencesDao()
            val bookingDao = FakeBookingDao()
            val driverDao = FakeDriverRouteDao()
            val notificationDao = FakeNotificationDao()
            val txRunner = FakeTransactionRunner()
            val repoScope = CoroutineScope(SupervisorJob() + dispatcher)

            val favRepo = FavoriteRouteRepository(prefs, favDao, prefsDao, txRunner, dispatcher, repoScope)
            val bookRepo = RoomBookingRepository(bookingDao, prefsDao, txRunner, null, dispatcher, repoScope)
            val driverRepo = RoomDriverRouteRepository(driverDao, txRunner, dispatcher, repoScope)
            val userPrefsRepo = RoomUserPreferencesRepository(prefsDao, txRunner, dispatcher, repoScope)
            val notifRepo = RoomNotificationsRepository(notificationDao, txRunner, dispatcher, repoScope)

            val vm = MumbaiShareAutoViewModel(
                favoriteRoutesRepo = favRepo,
                bookingsRepo = bookRepo,
                driverRoutesRepo = driverRepo,
                userPreferencesRepo = userPrefsRepo,
                notificationsRepo = notifRepo
            )
            advanceUntilIdle()

            // Verify honest local notices seeded (2 unread initially)
            assertEquals(3, vm.notifications.value.size)
            assertEquals(2, vm.unreadNotificationCount.value)
            assertTrue(vm.notifications.value.all { it.isLocalNotice })

            // Reject non-local fake push notification at repository level
            val fakePush = notifRepo.recordLocalNotice(
                NotificationEntity(
                    id = "fake_push",
                    category = "MATCH",
                    title = "Fake Driver Nearby",
                    message = "Should be rejected",
                    isLocalNotice = false,
                    createdAtMs = 1L
                )
            )
            assertTrue(fakePush is PersistenceResult.Failure)
            vm.dismissPersistenceError()

            vm.markAllNotificationsRead()
            advanceUntilIdle()
            assertEquals(0, vm.unreadNotificationCount.value)

            vm.toggleFavorite("w_dd_e_siddhivinayak")
            vm.updatePreferredMode(UserMode.DRIVER)
            vm.updateThemePreference(ThemePreference.DARK)
            advanceUntilIdle()

            assertTrue(vm.favoriteKeys.value.contains("w_dd_e_siddhivinayak"))
            assertEquals(UserMode.DRIVER.name, vm.userPreferences.value.preferredMode)
            assertEquals(ThemePreference.DARK.name, vm.userPreferences.value.themePreference)

            repoScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}

internal class FakeTransactionRunner(
    private val failOnNextWrite: Boolean = false
) : TransactionRunner {
    private var armed = failOnNextWrite
    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        if (armed) {
            armed = false
            throw IllegalStateException("Simulated SQLite write error")
        }
        return block()
    }
}

internal class FakeFavoriteRouteDao : FavoriteRouteDao {
    private val rows = MutableStateFlow<LinkedHashMap<String, FavoriteRouteEntity>>(linkedMapOf())

    override fun observeAll(): Flow<List<FavoriteRouteEntity>> =
        rows.map { map -> map.values.sortedByDescending { it.savedAtMs } }

    override fun observeRouteKeys(): Flow<List<String>> =
        rows.map { map -> map.values.sortedByDescending { it.savedAtMs }.map { it.routeKey } }

    override suspend fun getAllOnce(): List<FavoriteRouteEntity> =
        rows.value.values.sortedByDescending { it.savedAtMs }

    override suspend fun getByKey(routeKey: String): FavoriteRouteEntity? = rows.value[routeKey]

    override suspend fun upsert(entity: FavoriteRouteEntity) {
        val next = LinkedHashMap(rows.value)
        next[entity.routeKey] = entity
        rows.value = next
    }

    override suspend fun upsertAll(entities: List<FavoriteRouteEntity>) {
        val next = LinkedHashMap(rows.value)
        entities.forEach { next[it.routeKey] = it }
        rows.value = next
    }

    override suspend fun deleteByKey(routeKey: String): Int {
        val next = LinkedHashMap(rows.value)
        val removed = if (next.remove(routeKey) != null) 1 else 0
        rows.value = next
        return removed
    }

    override suspend fun clearAll() {
        rows.value = linkedMapOf()
    }
}

internal class FakeBookingDao : BookingDao {
    private val rows = MutableStateFlow<LinkedHashMap<String, BookingEntity>>(linkedMapOf())

    fun injectRawEntityForTest(entity: BookingEntity) {
        val next = LinkedHashMap(rows.value)
        next[entity.bookingId] = entity
        rows.value = next
    }

    override fun observeAll(): Flow<List<BookingEntity>> =
        rows.map { map -> map.values.sortedByDescending { it.updatedAtMs } }

    override suspend fun getAllOnce(): List<BookingEntity> =
        rows.value.values.sortedByDescending { it.updatedAtMs }

    override suspend fun getById(bookingId: String): BookingEntity? = rows.value[bookingId]

    override suspend fun insertNew(entity: BookingEntity) {
        if (rows.value.containsKey(entity.bookingId)) {
            throw IllegalStateException("PRIMARY KEY constraint failed for ${entity.bookingId}")
        }
        val next = LinkedHashMap(rows.value)
        next[entity.bookingId] = entity
        rows.value = next
    }

    override suspend fun updateExisting(entity: BookingEntity): Int {
        if (!rows.value.containsKey(entity.bookingId)) return 0
        val next = LinkedHashMap(rows.value)
        next[entity.bookingId] = entity
        rows.value = next
        return 1
    }

    override suspend fun deleteById(bookingId: String): Int {
        val next = LinkedHashMap(rows.value)
        val removed = if (next.remove(bookingId) != null) 1 else 0
        rows.value = next
        return removed
    }

    override suspend fun clearAll() {
        rows.value = linkedMapOf()
    }
}

internal class FakeDriverRouteDao : DriverRouteDao {
    private val rows = MutableStateFlow<LinkedHashMap<String, DriverRouteEntity>>(linkedMapOf())

    override fun observeAll(): Flow<List<DriverRouteEntity>> =
        rows.map { map -> map.values.sortedByDescending { it.createdAtMs } }

    override suspend fun getAllOnce(): List<DriverRouteEntity> =
        rows.value.values.sortedByDescending { it.createdAtMs }

    override suspend fun getById(postingId: String): DriverRouteEntity? = rows.value[postingId]

    override suspend fun upsert(entity: DriverRouteEntity) {
        val next = LinkedHashMap(rows.value)
        next[entity.postingId] = entity
        rows.value = next
    }

    override suspend fun deleteById(postingId: String): Int {
        val next = LinkedHashMap(rows.value)
        val removed = if (next.remove(postingId) != null) 1 else 0
        rows.value = next
        return removed
    }

    override suspend fun clearAll() {
        rows.value = linkedMapOf()
    }
}

internal class FakeUserPreferencesDao : UserPreferencesDao {
    private val row = MutableStateFlow<UserPreferencesEntity?>(null)

    override fun observe(): Flow<UserPreferencesEntity?> = row
    override suspend fun getOnce(): UserPreferencesEntity? = row.value
    override suspend fun upsert(entity: UserPreferencesEntity) {
        row.value = entity
    }

    override suspend fun clearAll() {
        row.value = null
    }
}

internal class FakeNotificationDao : NotificationDao {
    private val rows = MutableStateFlow<LinkedHashMap<String, NotificationEntity>>(linkedMapOf())

    override fun observeAll(): Flow<List<NotificationEntity>> =
        rows.map { map -> map.values.sortedByDescending { it.createdAtMs } }

    override fun observeUnreadCount(): Flow<Int> =
        rows.map { map -> map.values.count { !it.isRead } }

    override suspend fun getAllOnce(): List<NotificationEntity> =
        rows.value.values.sortedByDescending { it.createdAtMs }

    override suspend fun getById(id: String): NotificationEntity? = rows.value[id]

    override suspend fun getCount(): Int = rows.value.size

    override suspend fun upsert(entity: NotificationEntity) {
        val next = LinkedHashMap(rows.value)
        next[entity.id] = entity
        rows.value = next
    }

    override suspend fun insertIfAbsent(entities: List<NotificationEntity>) {
        val next = LinkedHashMap(rows.value)
        entities.forEach { if (!next.containsKey(it.id)) next[it.id] = it }
        rows.value = next
    }

    override suspend fun markRead(id: String): Int {
        val existing = rows.value[id] ?: return 0
        val next = LinkedHashMap(rows.value)
        next[id] = existing.copy(isRead = true)
        rows.value = next
        return 1
    }

    override suspend fun markAllRead(): Int {
        val next = LinkedHashMap<String, NotificationEntity>()
        rows.value.forEach { (k, v) -> next[k] = v.copy(isRead = true) }
        rows.value = next
        return next.size
    }

    override suspend fun deleteById(id: String): Int {
        val next = LinkedHashMap(rows.value)
        val removed = if (next.remove(id) != null) 1 else 0
        rows.value = next
        return removed
    }

    override suspend fun clearAll() {
        rows.value = linkedMapOf()
    }
}
