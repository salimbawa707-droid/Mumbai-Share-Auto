package com.mumbaishareauto.app.data.local

import android.content.SharedPreferences
import com.mumbaishareauto.app.BookingAgreement
import com.mumbaishareauto.app.BookingLifecycleState
import com.mumbaishareauto.app.UserMode
import com.mumbaishareauto.app.ValidationResult
import com.mumbaishareauto.app.data.local.db.BookingDao
import com.mumbaishareauto.app.data.local.db.BookingEntity
import com.mumbaishareauto.app.data.local.db.DriverRouteDao
import com.mumbaishareauto.app.data.local.db.DriverRouteEntity
import com.mumbaishareauto.app.data.local.db.NotificationDao
import com.mumbaishareauto.app.data.local.db.NotificationEntity
import com.mumbaishareauto.app.data.local.db.TransactionRunner
import com.mumbaishareauto.app.data.local.db.UserPreferencesDao
import com.mumbaishareauto.app.data.local.db.UserPreferencesEntity
import com.mumbaishareauto.app.ui.theme.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Explicit outcome wrapper for local persistence operations.
 */
sealed class PersistenceResult<out T> {
    data class Success<T>(val data: T) : PersistenceResult<T>()
    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : PersistenceResult<Nothing>()
}

interface FavoriteRoutesDataSource {
    val favoriteKeys: StateFlow<Set<String>>
    val lastError: StateFlow<String?>
    fun isFavorite(routeKey: String): Boolean
    fun toggleFavorite(routeKey: String)
    fun removeFavorite(routeKey: String)
    fun clearAllFavorites()
    suspend fun toggleFavoritePersist(routeKey: String): PersistenceResult<Set<String>>
    suspend fun removeFavoritePersist(routeKey: String): PersistenceResult<Set<String>>
    suspend fun clearAllFavoritesPersist(): PersistenceResult<Unit>
    suspend fun migrateLegacyFavorites(): PersistenceResult<Int>
    fun clearError()
}

interface BookingsDataSource {
    val bookings: StateFlow<List<BookingAgreement>>
    val corruptedRecordsQuarantined: StateFlow<Int>
    val lastError: StateFlow<String?>
    suspend fun registerNewBooking(agreement: BookingAgreement): PersistenceResult<BookingAgreement>
    suspend fun transitionBooking(
        bookingId: String,
        nextState: BookingLifecycleState,
        reason: String? = null
    ): PersistenceResult<BookingAgreement>
    suspend fun updateTotalFare(
        bookingId: String,
        newTotalFare: Double
    ): PersistenceResult<BookingAgreement>
    suspend fun clearAllBookings(): PersistenceResult<Unit>
    fun clearError()
}

interface DriverRoutesDataSource {
    val driverRoutes: StateFlow<List<DriverRouteEntity>>
    val lastError: StateFlow<String?>
    suspend fun saveLocalRoutePosting(entity: DriverRouteEntity): PersistenceResult<DriverRouteEntity>
    suspend fun deleteRoutePosting(postingId: String): PersistenceResult<Unit>
    suspend fun clearAllRoutes(): PersistenceResult<Unit>
    fun clearError()
}

interface UserPreferencesDataSource {
    val preferences: StateFlow<UserPreferencesEntity>
    val lastError: StateFlow<String?>
    suspend fun updateOnboardingCompleted(completed: Boolean): PersistenceResult<UserPreferencesEntity>
    suspend fun updatePreferredMode(mode: UserMode): PersistenceResult<UserPreferencesEntity>
    suspend fun updateThemePreference(theme: ThemePreference): PersistenceResult<UserPreferencesEntity>
    suspend fun resetPreferences(): PersistenceResult<UserPreferencesEntity>
    fun clearError()
}

interface NotificationsDataSource {
    val notifications: StateFlow<List<NotificationEntity>>
    val unreadCount: StateFlow<Int>
    val lastError: StateFlow<String?>
    suspend fun seedLocalNoticesIfEmpty(): PersistenceResult<Int>
    suspend fun recordLocalNotice(notice: NotificationEntity): PersistenceResult<NotificationEntity>
    suspend fun markRead(id: String): PersistenceResult<Unit>
    suspend fun markAllRead(): PersistenceResult<Unit>
    suspend fun clearAllNotifications(): PersistenceResult<Unit>
    fun clearError()
}

class RoomBookingRepository(
    private val bookingDao: BookingDao,
    private val userPreferencesDao: UserPreferencesDao,
    private val transactionRunner: TransactionRunner,
    private val legacyPrefs: SharedPreferences? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    externalScope: CoroutineScope? = null,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : BookingsDataSource {

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _bookings = MutableStateFlow<List<BookingAgreement>>(emptyList())
    override val bookings: StateFlow<List<BookingAgreement>> = _bookings.asStateFlow()

    private val _corruptedRecordsQuarantined = MutableStateFlow(0)
    override val corruptedRecordsQuarantined: StateFlow<Int> = _corruptedRecordsQuarantined.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        scope.launch {
            migrateLegacyAgreementsIfNeeded()
        }
        scope.launch {
            bookingDao.observeAll()
                .catch { error ->
                    _lastError.value = "Failed to read persisted bookings: ${error.message ?: "unknown error"}"
                }
                .collect { entities ->
                    var quarantined = 0
                    val valid = mutableListOf<BookingAgreement>()
                    entities.forEach { entity ->
                        val domain = entity.toDomain()
                        if (domain == null || domain.validateIntegrity() is ValidationResult.Invalid) {
                            quarantined++
                        } else {
                            valid.add(domain)
                        }
                    }
                    _corruptedRecordsQuarantined.value = quarantined
                    _bookings.value = valid
                }
        }
    }

    suspend fun migrateLegacyAgreementsIfNeeded(): PersistenceResult<Int> = withContext(ioDispatcher) {
        val prefs = legacyPrefs ?: return@withContext PersistenceResult.Success(0)
        try {
            val migratedCount = transactionRunner.runInTransaction {
                val currentPrefs = userPreferencesDao.getOnce() ?: UserPreferencesEntity()
                if (currentPrefs.agreementsMigratedFromPrefs) {
                    return@runInTransaction 0
                }
                val rawSet = prefs.getStringSet(BookingAgreementRepository.KEY_BOOKING_AGREEMENTS, emptySet())
                    ?: emptySet()
                var count = 0
                val now = clockMs()
                rawSet.forEach { serialized ->
                    val decoded = BookingAgreementRepository.deserializeAgreement(serialized)
                    if (decoded != null && decoded.validateIntegrity() is ValidationResult.Valid) {
                        if (bookingDao.getById(decoded.bookingId) == null) {
                            bookingDao.insertNew(
                                BookingEntity.fromDomain(
                                    agreement = decoded,
                                    updatedAtMs = now,
                                    dataOrigin = BookingEntity.ORIGIN_MIGRATED_PREFS
                                )
                            )
                            count++
                        }
                    }
                }
                userPreferencesDao.upsert(
                    currentPrefs.copy(
                        agreementsMigratedFromPrefs = true,
                        updatedAtMs = now
                    )
                )
                count
            }
            PersistenceResult.Success(migratedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Legacy booking migration failed: ${e.message ?: "database error"}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override suspend fun registerNewBooking(
        agreement: BookingAgreement
    ): PersistenceResult<BookingAgreement> = withContext(ioDispatcher) {
        val integrity = agreement.validateIntegrity()
        if (integrity is ValidationResult.Invalid) {
            val msg = integrity.summary
            _lastError.value = msg
            return@withContext PersistenceResult.Failure(msg)
        }
        try {
            transactionRunner.runInTransaction {
                if (bookingDao.getById(agreement.bookingId) != null) {
                    throw IllegalStateException(
                        "Duplicate booking ID rejected: ${agreement.bookingId} already exists"
                    )
                }
                bookingDao.insertNew(
                    BookingEntity.fromDomain(
                        agreement = agreement,
                        updatedAtMs = clockMs(),
                        dataOrigin = BookingEntity.ORIGIN_DEVICE_LOCAL
                    )
                )
            }
            refreshBookingsSnapshot()
            PersistenceResult.Success(agreement)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Unable to persist booking ${agreement.bookingId}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override suspend fun transitionBooking(
        bookingId: String,
        nextState: BookingLifecycleState,
        reason: String?
    ): PersistenceResult<BookingAgreement> = withContext(ioDispatcher) {
        try {
            val updatedAgreement = transactionRunner.runInTransaction {
                val existingEntity = bookingDao.getById(bookingId)
                    ?: throw NoSuchElementException("Booking ID $bookingId was not found")
                val existingDomain = existingEntity.toDomain()
                    ?: throw IllegalStateException("Booking record $bookingId is corrupted")

                val (updated, result) = existingDomain.transitionTo(nextState, reason = reason)
                if (result is ValidationResult.Invalid) {
                    throw IllegalArgumentException(result.summary)
                }
                bookingDao.updateExisting(
                    BookingEntity.fromDomain(
                        agreement = updated,
                        updatedAtMs = clockMs(),
                        dataOrigin = existingEntity.dataOrigin
                    )
                )
                updated
            }
            refreshBookingsSnapshot()
            PersistenceResult.Success(updatedAgreement)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Unable to transition booking $bookingId"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override suspend fun updateTotalFare(
        bookingId: String,
        newTotalFare: Double
    ): PersistenceResult<BookingAgreement> = withContext(ioDispatcher) {
        try {
            val updatedAgreement = transactionRunner.runInTransaction {
                val existingEntity = bookingDao.getById(bookingId)
                    ?: throw NoSuchElementException("Booking ID $bookingId was not found")
                val existingDomain = existingEntity.toDomain()
                    ?: throw IllegalStateException("Booking record $bookingId is corrupted")

                val (updated, result) = existingDomain.withProposedFareUpdate(newTotalFare)
                if (result is ValidationResult.Invalid) {
                    throw IllegalArgumentException(result.summary)
                }
                bookingDao.updateExisting(
                    BookingEntity.fromDomain(
                        agreement = updated,
                        updatedAtMs = clockMs(),
                        dataOrigin = existingEntity.dataOrigin
                    )
                )
                updated
            }
            refreshBookingsSnapshot()
            PersistenceResult.Success(updatedAgreement)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Unable to update total booking fare"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override suspend fun clearAllBookings(): PersistenceResult<Unit> = withContext(ioDispatcher) {
        try {
            transactionRunner.runInTransaction {
                bookingDao.clearAll()
            }
            _bookings.value = emptyList()
            _corruptedRecordsQuarantined.value = 0
            PersistenceResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Failed to clear bookings: ${e.message ?: "database error"}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    private suspend fun refreshBookingsSnapshot() {
        val entities = bookingDao.getAllOnce()
        var quarantined = 0
        val valid = mutableListOf<BookingAgreement>()
        entities.forEach { entity ->
            val domain = entity.toDomain()
            if (domain == null || domain.validateIntegrity() is ValidationResult.Invalid) {
                quarantined++
            } else {
                valid.add(domain)
            }
        }
        _corruptedRecordsQuarantined.value = quarantined
        _bookings.value = valid
    }

    override fun clearError() {
        _lastError.value = null
    }
}

class RoomDriverRouteRepository(
    private val driverRouteDao: DriverRouteDao,
    private val transactionRunner: TransactionRunner,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    externalScope: CoroutineScope? = null
) : DriverRoutesDataSource {

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _driverRoutes = MutableStateFlow<List<DriverRouteEntity>>(emptyList())
    override val driverRoutes: StateFlow<List<DriverRouteEntity>> = _driverRoutes.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        scope.launch {
            driverRouteDao.observeAll()
                .catch { error ->
                    _lastError.value = "Failed to observe driver routes: ${error.message ?: "unknown error"}"
                }
                .collect { rows ->
                    _driverRoutes.value = rows.filter { !it.isLiveServerBroadcast }
                }
        }
    }

    override suspend fun saveLocalRoutePosting(
        entity: DriverRouteEntity
    ): PersistenceResult<DriverRouteEntity> = withContext(ioDispatcher) {
        if (entity.isLiveServerBroadcast) {
            val msg = "Refused to persist driver route as live server availability: backend matching is unavailable."
            _lastError.value = msg
            return@withContext PersistenceResult.Failure(msg)
        }
        val structural = entity.toDriverRoutePosting(isTestFixture = true).validate()
        if (structural is ValidationResult.Invalid) {
            val msg = structural.summary
            _lastError.value = msg
            return@withContext PersistenceResult.Failure(msg)
        }
        try {
            val safeEntity = entity.copy(
                isLiveServerBroadcast = false,
                dataOrigin = DriverRouteEntity.ORIGIN_DEVICE_LOCAL
            )
            transactionRunner.runInTransaction {
                driverRouteDao.upsert(safeEntity)
            }
            _driverRoutes.value = driverRouteDao.getAllOnce().filter { !it.isLiveServerBroadcast }
            PersistenceResult.Success(safeEntity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Failed to save driver route: ${e.message ?: "database error"}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override suspend fun deleteRoutePosting(postingId: String): PersistenceResult<Unit> =
        withContext(ioDispatcher) {
            try {
                transactionRunner.runInTransaction {
                    driverRouteDao.deleteById(postingId)
                }
                _driverRoutes.value = driverRouteDao.getAllOnce().filter { !it.isLiveServerBroadcast }
                PersistenceResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = "Failed to delete driver route: ${e.message ?: "database error"}"
                _lastError.value = msg
                PersistenceResult.Failure(msg, e)
            }
        }

    override suspend fun clearAllRoutes(): PersistenceResult<Unit> = withContext(ioDispatcher) {
        try {
            transactionRunner.runInTransaction {
                driverRouteDao.clearAll()
            }
            _driverRoutes.value = emptyList()
            PersistenceResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Failed to clear driver routes: ${e.message ?: "database error"}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override fun clearError() {
        _lastError.value = null
    }
}

class RoomUserPreferencesRepository(
    private val userPreferencesDao: UserPreferencesDao,
    private val transactionRunner: TransactionRunner,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    externalScope: CoroutineScope? = null,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : UserPreferencesDataSource {

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _preferences = MutableStateFlow(UserPreferencesEntity())
    override val preferences: StateFlow<UserPreferencesEntity> = _preferences.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        scope.launch {
            userPreferencesDao.observe()
                .catch { error ->
                    _lastError.value = "Failed to observe user preferences: ${error.message ?: "unknown error"}"
                }
                .collect { entity ->
                    if (entity != null) {
                        _preferences.value = entity
                    }
                }
        }
    }

    override suspend fun updateOnboardingCompleted(
        completed: Boolean
    ): PersistenceResult<UserPreferencesEntity> = updateEntity { current ->
        current.copy(onboardingCompleted = completed, updatedAtMs = clockMs())
    }

    override suspend fun updatePreferredMode(
        mode: UserMode
    ): PersistenceResult<UserPreferencesEntity> = updateEntity { current ->
        current.copy(preferredMode = mode.name, updatedAtMs = clockMs())
    }

    override suspend fun updateThemePreference(
        theme: ThemePreference
    ): PersistenceResult<UserPreferencesEntity> = updateEntity { current ->
        current.copy(themePreference = theme.name, updatedAtMs = clockMs())
    }

    override suspend fun resetPreferences(): PersistenceResult<UserPreferencesEntity> =
        updateEntity { current ->
            UserPreferencesEntity(
                id = UserPreferencesEntity.SINGLETON_ID,
                onboardingCompleted = false,
                preferredMode = UserMode.PASSENGER.name,
                themePreference = ThemePreference.SYSTEM.name,
                favoritesMigratedFromPrefs = current.favoritesMigratedFromPrefs,
                agreementsMigratedFromPrefs = current.agreementsMigratedFromPrefs,
                updatedAtMs = clockMs()
            )
        }

    private suspend fun updateEntity(
        transform: (UserPreferencesEntity) -> UserPreferencesEntity
    ): PersistenceResult<UserPreferencesEntity> = withContext(ioDispatcher) {
        try {
            val next = transactionRunner.runInTransaction {
                val existing = userPreferencesDao.getOnce() ?: UserPreferencesEntity()
                val updated = transform(existing)
                userPreferencesDao.upsert(updated)
                updated
            }
            _preferences.value = next
            PersistenceResult.Success(next)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Failed to save preferences: ${e.message ?: "database error"}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override fun clearError() {
        _lastError.value = null
    }
}

class RoomNotificationsRepository(
    private val notificationDao: NotificationDao,
    private val transactionRunner: TransactionRunner,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    externalScope: CoroutineScope? = null,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : NotificationsDataSource {

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _notifications = MutableStateFlow<List<NotificationEntity>>(emptyList())
    override val notifications: StateFlow<List<NotificationEntity>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    override val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        scope.launch {
            notificationDao.observeAll()
                .catch { error ->
                    _lastError.value = "Failed to observe notifications: ${error.message ?: "unknown error"}"
                }
                .collect { items ->
                    val localOnly = items.filter { it.isLocalNotice }
                    _notifications.value = localOnly
                    _unreadCount.value = localOnly.count { !it.isRead }
                }
        }
    }

    override suspend fun seedLocalNoticesIfEmpty(): PersistenceResult<Int> = withContext(ioDispatcher) {
        try {
            val seededCount = transactionRunner.runInTransaction {
                if (notificationDao.getCount() > 0) {
                    return@runInTransaction 0
                }
                val baseTime = clockMs()
                val defaults = listOf(
                    NotificationEntity(
                        id = "local_notice_rto_capacity",
                        category = NotificationEntity.CATEGORY_COMPLIANCE,
                        title = "RTO Capacity Verification Pending",
                        message = "Vehicle capacity defaults remain unverified (isLegallyVerified = false). Commercial operations stay blocked in BookingDomain until RTO permit verification is connected.",
                        isRead = false,
                        isLocalNotice = true,
                        createdAtMs = baseTime
                    ),
                    NotificationEntity(
                        id = "local_notice_offline_room",
                        category = NotificationEntity.CATEGORY_STORAGE,
                        title = "Local Room Persistence Active",
                        message = "Favorites, preferences, route drafts, and local notices are stored offline in SQLite via Room. Live push alerts (FCM) remain offline.",
                        isRead = false,
                        isLocalNotice = true,
                        createdAtMs = baseTime - 1_000L
                    ),
                    NotificationEntity(
                        id = "local_notice_fare_rule",
                        category = NotificationEntity.CATEGORY_FARE_RULE,
                        title = "V1 Total-Booking Fare Policy",
                        message = "Quotes apply as a single total fare per passenger party booking and lock only after DRIVER_ACCEPTED -> PASSENGER_CONFIRMED -> LOCKED.",
                        isRead = true,
                        isLocalNotice = true,
                        createdAtMs = baseTime - 2_000L
                    )
                )
                notificationDao.insertIfAbsent(defaults)
                defaults.size
            }
            refreshSnapshot()
            PersistenceResult.Success(seededCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Failed to initialize local notices: ${e.message ?: "database error"}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override suspend fun recordLocalNotice(
        notice: NotificationEntity
    ): PersistenceResult<NotificationEntity> = withContext(ioDispatcher) {
        if (!notice.isLocalNotice) {
            val msg = "Rejected non-local notification: simulated server push notifications are prohibited."
            _lastError.value = msg
            return@withContext PersistenceResult.Failure(msg)
        }
        try {
            transactionRunner.runInTransaction {
                notificationDao.upsert(notice)
            }
            refreshSnapshot()
            PersistenceResult.Success(notice)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Failed to record local notice: ${e.message ?: "database error"}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override suspend fun markRead(id: String): PersistenceResult<Unit> = withContext(ioDispatcher) {
        try {
            transactionRunner.runInTransaction {
                notificationDao.markRead(id)
            }
            refreshSnapshot()
            PersistenceResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Failed to mark notice as read: ${e.message ?: "database error"}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override suspend fun markAllRead(): PersistenceResult<Unit> = withContext(ioDispatcher) {
        try {
            transactionRunner.runInTransaction {
                notificationDao.markAllRead()
            }
            refreshSnapshot()
            PersistenceResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Failed to mark all notices as read: ${e.message ?: "database error"}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override suspend fun clearAllNotifications(): PersistenceResult<Unit> = withContext(ioDispatcher) {
        try {
            transactionRunner.runInTransaction {
                notificationDao.clearAll()
            }
            _notifications.value = emptyList()
            _unreadCount.value = 0
            PersistenceResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Failed to clear notifications: ${e.message ?: "database error"}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    private suspend fun refreshSnapshot() {
        val localOnly = notificationDao.getAllOnce().filter { it.isLocalNotice }
        _notifications.value = localOnly
        _unreadCount.value = localOnly.count { !it.isRead }
    }

    override fun clearError() {
        _lastError.value = null
    }
}
