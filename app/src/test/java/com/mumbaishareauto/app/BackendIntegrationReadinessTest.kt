package com.mumbaishareauto.app

import com.mumbaishareauto.app.auth.AuthUserRole
import com.mumbaishareauto.app.auth.AuthenticatedUserProfile
import com.mumbaishareauto.app.data.local.FavoriteRouteRepository
import com.mumbaishareauto.app.data.local.RoomBookingRepository
import com.mumbaishareauto.app.data.local.RoomDriverRouteRepository
import com.mumbaishareauto.app.data.local.RoomNotificationsRepository
import com.mumbaishareauto.app.data.local.RoomUserPreferencesRepository
import com.mumbaishareauto.app.data.remote.FirestoreActionResult
import com.mumbaishareauto.app.data.remote.LiveAutoRoute
import com.mumbaishareauto.app.data.remote.LiveRouteTransactionEngine
import com.mumbaishareauto.app.data.remote.LiveRoutesDataSource
import com.mumbaishareauto.app.data.remote.LiveRoutesStreamState
import com.mumbaishareauto.app.data.remote.NetworkConnectivityManager
import com.mumbaishareauto.app.data.remote.PublishRouteCommand
import com.mumbaishareauto.app.data.remote.RouteSeatLock
import com.mumbaishareauto.app.model.StationSide
import com.mumbaishareauto.app.ui.MumbaiShareAutoViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class BackendIntegrationReadinessTest {

    @Test
    fun liveRouteTransactionEngine_enforcesOneToThreeSeatsPositiveFlatFareAndValidStand() {
        val validCommand = PublishRouteCommand(
            driverUid = "driver_uid_01",
            driverAutoRegNumber = "MH02AB1234",
            pickupStation = "Andheri",
            pickupSide = StationSide.EAST,
            designatedStand = "Andheri East Auto Deck Stand",
            destination = "SEEPZ Gate 1",
            viaPoints = listOf("Chakala", "MIDC"),
            totalBookingFare = 120.0,
            availableSeats = 3
        )
        assertTrue(LiveRouteTransactionEngine.validatePublishCommand(validCommand) is FirestoreActionResult.Success)

        // 0 seats and 4 seats must fail strictly
        val zeroSeats = LiveRouteTransactionEngine.validatePublishCommand(validCommand.copy(availableSeats = 0))
        assertTrue(zeroSeats is FirestoreActionResult.Failure)

        val fourSeats = LiveRouteTransactionEngine.validatePublishCommand(validCommand.copy(availableSeats = 4))
        assertTrue(fourSeats is FirestoreActionResult.Failure)

        // Zero or negative fare must fail
        val badFare = LiveRouteTransactionEngine.validatePublishCommand(validCommand.copy(totalBookingFare = 0.0))
        assertTrue(badFare is FirestoreActionResult.Failure)

        // Identical pickup & destination must fail
        val identical = LiveRouteTransactionEngine.validatePublishCommand(
            validCommand.copy(destination = "Andheri East Auto Deck Stand")
        )
        assertTrue(identical is FirestoreActionResult.Failure)
    }

    @Test
    fun liveRouteTransactionEngine_preventsDoubleBookingAcrossConcurrentRequests() {
        val initialRouteResult = LiveRouteTransactionEngine.buildNewLiveRoute(
            postingId = "ROUTE_ANDHERI_01",
            command = PublishRouteCommand(
                driverUid = "driver_uid_1",
                driverAutoRegNumber = "MH02AB1234",
                pickupStation = "Andheri",
                pickupSide = StationSide.EAST,
                designatedStand = "Andheri East Auto Deck Stand",
                destination = "SEEPZ",
                viaPoints = listOf("MIDC Central"),
                totalBookingFare = 120.0,
                availableSeats = 3
            ),
            nowMs = 1_000L
        )
        assertTrue(initialRouteResult is FirestoreActionResult.Success)
        val initialRoute = (initialRouteResult as FirestoreActionResult.Success).data
        assertEquals(3, initialRoute.availableSeats)
        assertEquals(0, initialRoute.activeQueueCount)
        assertFalse(initialRoute.isLegallyVerified)

        // Passenger A atomically locks 2 seats -> 1 seat left, queue #1
        val lockAResult = LiveRouteTransactionEngine.computeSeatLockTransition(
            currentRoute = initialRoute,
            existingLock = null,
            passengerUid = "pax_uid_A",
            seatsRequested = 2,
            nowMs = 2_000L
        )
        assertTrue(lockAResult is FirestoreActionResult.Success)
        val afterA = (lockAResult as FirestoreActionResult.Success).data
        assertEquals(1, afterA.updatedRoute.availableSeats)
        assertEquals(1, afterA.updatedRoute.activeQueueCount)
        assertEquals(1, afterA.createdOrUpdatedLock.queuePosition)
        assertEquals(120.0, afterA.createdOrUpdatedLock.agreedTotalFare, 0.001)

        // Passenger B attempts to lock 2 seats against the post-transaction route -> rejected!
        val overbookB = LiveRouteTransactionEngine.computeSeatLockTransition(
            currentRoute = afterA.updatedRoute,
            existingLock = null,
            passengerUid = "pax_uid_B",
            seatsRequested = 2,
            nowMs = 2_500L
        )
        assertTrue(overbookB is FirestoreActionResult.Failure)
        assertTrue((overbookB as FirestoreActionResult.Failure).isDoubleBookingConflict)

        // Duplicate lock by Passenger A on the same route -> rejected!
        val dupLockA = LiveRouteTransactionEngine.computeSeatLockTransition(
            currentRoute = afterA.updatedRoute,
            existingLock = afterA.createdOrUpdatedLock,
            passengerUid = "pax_uid_A",
            seatsRequested = 1,
            nowMs = 2_600L
        )
        assertTrue(dupLockA is FirestoreActionResult.Failure)
        assertTrue((dupLockA as FirestoreActionResult.Failure).isDoubleBookingConflict)

        // Driver cannot self-book own route -> rejected!
        val driverSelfBook = LiveRouteTransactionEngine.computeSeatLockTransition(
            currentRoute = afterA.updatedRoute,
            existingLock = null,
            passengerUid = "driver_uid_1",
            seatsRequested = 1,
            nowMs = 2_700L
        )
        assertTrue(driverSelfBook is FirestoreActionResult.Failure)

        // Passenger B locks final 1 seat -> 0 seats left, queue #2
        val lockBResult = LiveRouteTransactionEngine.computeSeatLockTransition(
            currentRoute = afterA.updatedRoute,
            existingLock = null,
            passengerUid = "pax_uid_B",
            seatsRequested = 1,
            nowMs = 3_000L
        )
        assertTrue(lockBResult is FirestoreActionResult.Success)
        val afterB = (lockBResult as FirestoreActionResult.Success).data
        assertEquals(0, afterB.updatedRoute.availableSeats)
        assertEquals(2, afterB.updatedRoute.activeQueueCount)
        assertEquals(2, afterB.createdOrUpdatedLock.queuePosition)

        // Passenger C attempts to lock 1 seat when 0 left -> rejected!
        val lockCFull = LiveRouteTransactionEngine.computeSeatLockTransition(
            currentRoute = afterB.updatedRoute,
            existingLock = null,
            passengerUid = "pax_uid_C",
            seatsRequested = 1,
            nowMs = 3_500L
        )
        assertTrue(lockCFull is FirestoreActionResult.Failure)
        assertTrue((lockCFull as FirestoreActionResult.Failure).isDoubleBookingConflict)

        // Passenger B releases lock -> 1 seat restored safely
        val releaseB = LiveRouteTransactionEngine.computeSeatReleaseTransition(
            currentRoute = afterB.updatedRoute,
            existingLock = afterB.createdOrUpdatedLock,
            passengerUid = "pax_uid_B",
            nowMs = 4_000L
        )
        assertTrue(releaseB is FirestoreActionResult.Success)
        val afterRelease = (releaseB as FirestoreActionResult.Success).data
        assertEquals(1, afterRelease.updatedRoute.availableSeats)
        assertEquals(RouteSeatLock.STATUS_RELEASED, afterRelease.createdOrUpdatedLock.status)
    }

    @Test
    fun liveAutoRoute_firestorePayloadRoundTripAndSearchFilterWorkCleanly() {
        val route = LiveAutoRoute(
            postingId = "ROUTE_BANDRA_01",
            driverUid = "drv_bandra_01",
            driverAutoRegNumber = "MH01XY9999",
            pickupStation = "Bandra",
            pickupSide = StationSide.EAST,
            designatedStand = "Bandra Station East Auto Stand",
            destination = "BKC Diamond Bourse",
            viaPoints = listOf("Kalanagar", "Family Court"),
            totalBookingFare = 90.0,
            totalCapacitySeats = 3,
            availableSeats = 2,
            activeQueueCount = 1,
            isActive = true,
            isLegallyVerified = false
        )
        val fields = route.toBaseFirestoreFields()
        assertFalse(fields.containsKey("phoneNumber")) // Privacy guard: no phone PII in public routes
        assertEquals(false, fields[LiveAutoRoute.FIELD_IS_LEGALLY_VERIFIED])

        val restored = LiveAutoRoute.fromFirestoreFields("ROUTE_BANDRA_01", fields)
        assertNotNull(restored)
        assertEquals("Bandra Station East Auto Stand", restored?.designatedStand)
        assertEquals(90.0, restored?.totalBookingFare ?: 0.0, 0.001)
        assertEquals(2, restored?.availableSeats)
        assertTrue(restored?.matchesSearch("Bandra", StationSide.EAST, "Kalanagar") == true)
        assertFalse(restored?.matchesSearch("Bandra", StationSide.WEST, "Kalanagar") == true)
    }

    @Test
    fun viewModelFirestoreLiveSync_publishesRouteStreamsQueueAndLocksSeatsAtomically() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val prefs = InMemorySharedPreferences()
            val prefsDao = FakeUserPreferencesDao()
            val txRunner = FakeTransactionRunner()
            val repoScope = CoroutineScope(SupervisorJob() + dispatcher)
            val fakeAuth = FakePhoneAuthGateway(isFirebaseConfigured = true)
            val fakeProfileStore = FakeUserProfileRemoteDataSource(isFirestoreConfigured = true)
            val liveStore = InMemoryTransactionalFirestoreRoutesRepo(isFirestoreConfigured = true)

            val vm = MumbaiShareAutoViewModel(
                favoriteRoutesRepo = FavoriteRouteRepository(prefs, FakeFavoriteRouteDao(), prefsDao, txRunner, dispatcher, repoScope),
                bookingsRepo = RoomBookingRepository(FakeBookingDao(), prefsDao, txRunner, null, dispatcher, repoScope),
                driverRoutesRepo = RoomDriverRouteRepository(FakeDriverRouteDao(), txRunner, dispatcher, repoScope),
                userPreferencesRepo = RoomUserPreferencesRepository(prefsDao, txRunner, dispatcher, repoScope),
                notificationsRepo = RoomNotificationsRepository(FakeNotificationDao(), txRunner, dispatcher, repoScope),
                phoneAuthGateway = fakeAuth,
                userProfileRemoteDataSource = fakeProfileStore,
                liveRoutesGateway = liveStore
            )
            advanceUntilIdle()

            // Driver signs in with OTP and posts active route (3 seats)
            fakeAuth.emitTestUser("drv_uid_01", "+919820011223")
            fakeProfileStore.saveAuthenticatedProfile(
                AuthenticatedUserProfile("drv_uid_01", "+919820011223", AuthUserRole.DRIVER, "MH02AB1234")
            )
            advanceUntilIdle()

            var publishedRouteId: String? = null
            vm.publishLiveRouteToFirestore(
                pickupStation = "Kurla",
                pickupSide = StationSide.WEST,
                designatedStand = "Kurla Station West Auto Stand",
                destination = "BKC Equinix",
                viaPoints = listOf("LBS Marg"),
                totalBookingFare = 110.0,
                availableSeats = 3
            ) { created ->
                publishedRouteId = created.postingId
            }
            advanceUntilIdle()

            assertNotNull(publishedRouteId)
            val liveState = vm.liveRoutesState.value
            assertTrue(liveState is LiveRoutesStreamState.Live)
            assertEquals(1, (liveState as LiveRoutesStreamState.Live).routes.size)
            assertEquals(3, liveState.routes.first().availableSeats)
            assertEquals(1, vm.driverLiveRoutes.value.size)

            // Passenger signs in, sees live route, and atomically locks 2 seats
            fakeAuth.emitTestUser("pax_uid_01", "+919820099887")
            fakeProfileStore.saveAuthenticatedProfile(
                AuthenticatedUserProfile("pax_uid_01", "+919820099887", AuthUserRole.PASSENGER)
            )
            advanceUntilIdle()

            vm.lockSeatOnLiveRoute(publishedRouteId!!, seatsRequested = 2)
            advanceUntilIdle()

            val postLockRoutes = (vm.liveRoutesState.value as LiveRoutesStreamState.Live).routes
            assertEquals(1, postLockRoutes.first().availableSeats)
            assertEquals(1, postLockRoutes.first().activeQueueCount)
            assertEquals(1, vm.liveRouteActionState.value.selectedQueueLocks.size)
            assertEquals(1, vm.liveRouteActionState.value.selectedQueueLocks.first().queuePosition)

            // Second passenger tries locking 2 seats when only 1 left -> rejected safely
            fakeAuth.emitTestUser("pax_uid_02", "+919820077665")
            fakeProfileStore.saveAuthenticatedProfile(
                AuthenticatedUserProfile("pax_uid_02", "+919820077665", AuthUserRole.PASSENGER)
            )
            advanceUntilIdle()
            vm.lockSeatOnLiveRoute(publishedRouteId!!, seatsRequested = 2)
            advanceUntilIdle()

            assertTrue(vm.liveRouteActionState.value.lastActionError?.contains("Only 1 seat(s) left") == true)

            repoScope.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun networkConnectivityContract_tracksOnlineOfflineTransitions() = runTest {
        val connectivity = TestConnectivityManager(initialConnected = true)
        assertTrue(connectivity.checkConnectivityNow())
        connectivity.setConnected(false)
        assertFalse(connectivity.checkConnectivityNow())
        assertEquals("Offline", connectivity.connectionType.value)
    }
}

private class InMemoryTransactionalFirestoreRoutesRepo(
    override val isFirestoreConfigured: Boolean
) : LiveRoutesDataSource {
    private val routesMap = linkedMapOf<String, LiveAutoRoute>()
    private val locksMap = linkedMapOf<String, MutableMap<String, RouteSeatLock>>()

    private val activeRoutesFlow = MutableStateFlow<LiveRoutesStreamState>(
        if (isFirestoreConfigured) LiveRoutesStreamState.Live(emptyList()) else LiveRoutesStreamState.NotConfigured()
    )
    private val queueFlows = mutableMapOf<String, MutableStateFlow<List<RouteSeatLock>>>()

    private fun emitAll() {
        val activeList = routesMap.values.filter { it.isActive }.toList()
        activeRoutesFlow.value = LiveRoutesStreamState.Live(activeList)
        locksMap.forEach { (postingId, paxLocks) ->
            queueFlows.getOrPut(postingId) { MutableStateFlow(emptyList()) }.value =
                paxLocks.values.filter { it.status == RouteSeatLock.STATUS_LOCKED }.sortedBy { it.queuePosition }
        }
    }

    override fun observeActiveRoutes(): Flow<LiveRoutesStreamState> = activeRoutesFlow.asStateFlow()

    override fun observeDriverActiveRoutes(driverUid: String): Flow<List<LiveAutoRoute>> =
        activeRoutesFlow.map { state ->
            when (state) {
                is LiveRoutesStreamState.Live -> state.routes.filter { it.driverUid == driverUid }
                else -> emptyList()
            }
        }

    override fun observeRouteQueueLocks(postingId: String): Flow<List<RouteSeatLock>> =
        queueFlows.getOrPut(postingId) { MutableStateFlow(emptyList()) }.asStateFlow()

    override suspend fun publishActiveRoute(command: PublishRouteCommand): FirestoreActionResult<LiveAutoRoute> {
        val postingId = "ROUTE_${UUID.randomUUID().toString().take(8).uppercase()}"
        return when (val res = LiveRouteTransactionEngine.buildNewLiveRoute(postingId, command)) {
            is FirestoreActionResult.Failure -> res
            is FirestoreActionResult.Success -> {
                routesMap[postingId] = res.data
                locksMap.getOrPut(postingId) { linkedMapOf() }
                emitAll()
                res
            }
        }
    }

    override suspend fun lockSeatTransaction(
        postingId: String,
        passengerUid: String,
        seatsRequested: Int,
        agreedTotalFare: Double?,
        negotiationId: String?
    ): FirestoreActionResult<RouteSeatLock> {
        val current = routesMap[postingId]
            ?: return FirestoreActionResult.Failure("Route not found.", isDoubleBookingConflict = true)
        val existingLock = locksMap[postingId]?.get(passengerUid)
        return when (val transition = LiveRouteTransactionEngine.computeSeatLockTransition(
            currentRoute = if (agreedTotalFare == null) current else current.copy(totalBookingFare = agreedTotalFare),
            existingLock = existingLock,
            passengerUid = passengerUid,
            seatsRequested = seatsRequested,
            agreedTotalFare = agreedTotalFare,
            negotiationId = negotiationId
        )) {
            is FirestoreActionResult.Failure -> transition
            is FirestoreActionResult.Success -> {
                routesMap[postingId] = transition.data.updatedRoute
                locksMap.getOrPut(postingId) { linkedMapOf() }[passengerUid] = transition.data.createdOrUpdatedLock
                emitAll()
                FirestoreActionResult.Success(transition.data.createdOrUpdatedLock)
            }
        }
    }

    override suspend fun releaseSeatTransaction(
        postingId: String,
        passengerUid: String
    ): FirestoreActionResult<Unit> {
        val current = routesMap[postingId]
            ?: return FirestoreActionResult.Failure("Route not found.")
        val existingLock = locksMap[postingId]?.get(passengerUid)
        return when (val transition = LiveRouteTransactionEngine.computeSeatReleaseTransition(
            currentRoute = current,
            existingLock = existingLock,
            passengerUid = passengerUid
        )) {
            is FirestoreActionResult.Failure -> transition
            is FirestoreActionResult.Success -> {
                routesMap[postingId] = transition.data.updatedRoute
                locksMap.getOrPut(postingId) { linkedMapOf() }[passengerUid] = transition.data.createdOrUpdatedLock
                emitAll()
                FirestoreActionResult.Success(Unit)
            }
        }
    }

    override suspend fun deactivateDriverRoute(
        postingId: String,
        driverUid: String
    ): FirestoreActionResult<Unit> {
        val current = routesMap[postingId] ?: return FirestoreActionResult.Failure("Route not found.")
        if (current.driverUid != driverUid) return FirestoreActionResult.Failure("Unauthorized.")
        routesMap[postingId] = current.copy(isActive = false)
        emitAll()
        return FirestoreActionResult.Success(Unit)
    }
}

private class TestConnectivityManager(initialConnected: Boolean) : NetworkConnectivityManager {
    private val _isConnected = MutableStateFlow(initialConnected)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionType = MutableStateFlow(if (initialConnected) "Wi-Fi" else "Offline")
    override val connectionType: StateFlow<String> = _connectionType.asStateFlow()

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
        _connectionType.value = if (connected) "Wi-Fi" else "Offline"
    }

    override suspend fun checkConnectivityNow(): Boolean = _isConnected.value
}
