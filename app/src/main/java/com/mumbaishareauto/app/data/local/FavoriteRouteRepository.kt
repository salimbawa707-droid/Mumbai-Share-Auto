package com.mumbaishareauto.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.mumbaishareauto.app.data.local.db.FavoriteRouteDao
import com.mumbaishareauto.app.data.local.db.FavoriteRouteEntity
import com.mumbaishareauto.app.data.local.db.MumbaiShareAutoDatabase
import com.mumbaishareauto.app.data.local.db.RoomTransactionRunner
import com.mumbaishareauto.app.data.local.db.TransactionRunner
import com.mumbaishareauto.app.data.local.db.UserPreferencesDao
import com.mumbaishareauto.app.data.local.db.UserPreferencesEntity
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
 * Repository managing locally bookmarked station routes.
 *
 * Architecture:
 * - Retains synchronous SharedPreferences constructor/behavior for existing tests and immediate startup.
 * - Migrates legacy SharedPreferences favorites (`favorite_route_keys`) once inside an atomic Room
 *   transaction into `favorite_routes` (`FavoriteRouteEntity`) on startup.
 * - Uses Room as the authoritative local source of truth when configured, surfacing persistence
 *   failures via `lastError` and `PersistenceResult`.
 */
class FavoriteRouteRepository internal constructor(
    private val prefs: SharedPreferences,
    private val favoriteRouteDao: FavoriteRouteDao? = null,
    private val userPreferencesDao: UserPreferencesDao? = null,
    private val transactionRunner: TransactionRunner? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    externalScope: CoroutineScope? = null,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : FavoriteRoutesDataSource {

    constructor(context: Context) : this(
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        favoriteRouteDao = MumbaiShareAutoDatabase.getInstance(context).favoriteRouteDao(),
        userPreferencesDao = MumbaiShareAutoDatabase.getInstance(context).userPreferencesDao(),
        transactionRunner = RoomTransactionRunner(MumbaiShareAutoDatabase.getInstance(context))
    )

    private val scope = externalScope ?: CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _favoriteKeys = MutableStateFlow(loadLegacyFavoriteKeys())
    override val favoriteKeys: StateFlow<Set<String>> = _favoriteKeys.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    init {
        if (favoriteRouteDao != null) {
            scope.launch {
                migrateLegacyFavorites()
                favoriteRouteDao.observeRouteKeys()
                    .catch { error ->
                        _lastError.value =
                            "Failed to observe favorite stands: ${error.message ?: "database error"}"
                    }
                    .collect { keys ->
                        val keySet = keys.toSet()
                        prefs.edit().putStringSet(KEY_FAVORITE_ROUTES, keySet).apply()
                        _favoriteKeys.value = keySet
                    }
            }
        }
    }

    private fun loadLegacyFavoriteKeys(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITE_ROUTES, emptySet())?.toSet() ?: emptySet()
    }

    override suspend fun migrateLegacyFavorites(): PersistenceResult<Int> = withContext(ioDispatcher) {
        val dao = favoriteRouteDao ?: return@withContext PersistenceResult.Success(0)
        try {
            val runner = transactionRunner
            val action: suspend () -> Int = {
                val prefState = userPreferencesDao?.getOnce() ?: UserPreferencesEntity()
                if (prefState.favoritesMigratedFromPrefs) {
                    0
                } else {
                    val legacyKeys = loadLegacyFavoriteKeys()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    val now = clockMs()
                    if (legacyKeys.isNotEmpty()) {
                        val entities = legacyKeys.map { key ->
                            FavoriteRouteEntity(
                                routeKey = key,
                                savedAtMs = now,
                                sourceScope = FavoriteRouteEntity.SOURCE_MIGRATED_PREFS
                            )
                        }
                        dao.upsertAll(entities)
                    }
                    userPreferencesDao?.upsert(
                        prefState.copy(
                            favoritesMigratedFromPrefs = true,
                            updatedAtMs = now
                        )
                    )
                    legacyKeys.size
                }
            }
            val count = if (runner != null) runner.runInTransaction(action) else action()
            val refreshed = dao.getAllOnce().map { it.routeKey }.toSet()
            _favoriteKeys.value = refreshed
            prefs.edit().putStringSet(KEY_FAVORITE_ROUTES, refreshed).apply()
            PersistenceResult.Success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "Legacy favorites migration failed: ${e.message ?: "database error"}"
            _lastError.value = msg
            PersistenceResult.Failure(msg, e)
        }
    }

    override fun isFavorite(routeKey: String): Boolean {
        return _favoriteKeys.value.contains(routeKey)
    }

    override fun toggleFavorite(routeKey: String) {
        val cleanKey = routeKey.trim()
        if (cleanKey.isEmpty()) return
        val current = _favoriteKeys.value.toMutableSet()
        val added = if (current.contains(cleanKey)) {
            current.remove(cleanKey)
            false
        } else {
            current.add(cleanKey)
            true
        }
        val next = current.toSet()
        prefs.edit().putStringSet(KEY_FAVORITE_ROUTES, next).apply()
        _favoriteKeys.value = next

        if (favoriteRouteDao != null) {
            scope.launch {
                runRoomWrite {
                    if (added) {
                        favoriteRouteDao.upsert(
                            FavoriteRouteEntity(
                                routeKey = cleanKey,
                                savedAtMs = clockMs(),
                                sourceScope = FavoriteRouteEntity.SOURCE_DEVICE_LOCAL
                            )
                        )
                    } else {
                        favoriteRouteDao.deleteByKey(cleanKey)
                    }
                }
            }
        }
    }

    override fun removeFavorite(routeKey: String) {
        val cleanKey = routeKey.trim()
        val current = _favoriteKeys.value.toMutableSet()
        if (current.remove(cleanKey)) {
            val next = current.toSet()
            prefs.edit().putStringSet(KEY_FAVORITE_ROUTES, next).apply()
            _favoriteKeys.value = next
        }
        if (favoriteRouteDao != null) {
            scope.launch {
                runRoomWrite { favoriteRouteDao.deleteByKey(cleanKey) }
            }
        }
    }

    override fun clearAllFavorites() {
        prefs.edit().remove(KEY_FAVORITE_ROUTES).apply()
        _favoriteKeys.value = emptySet()
        if (favoriteRouteDao != null) {
            scope.launch {
                runRoomWrite { favoriteRouteDao.clearAll() }
            }
        }
    }

    override suspend fun toggleFavoritePersist(routeKey: String): PersistenceResult<Set<String>> =
        withContext(ioDispatcher) {
            val cleanKey = routeKey.trim()
            if (cleanKey.isEmpty()) {
                return@withContext PersistenceResult.Failure("Route key cannot be blank")
            }
            val dao = favoriteRouteDao
            if (dao == null) {
                toggleFavorite(cleanKey)
                return@withContext PersistenceResult.Success(_favoriteKeys.value)
            }
            try {
                val updatedSet = executeInTransaction {
                    val existing = dao.getByKey(cleanKey)
                    if (existing != null) {
                        dao.deleteByKey(cleanKey)
                    } else {
                        dao.upsert(
                            FavoriteRouteEntity(
                                routeKey = cleanKey,
                                savedAtMs = clockMs(),
                                sourceScope = FavoriteRouteEntity.SOURCE_DEVICE_LOCAL
                            )
                        )
                    }
                    dao.getAllOnce().map { it.routeKey }.toSet()
                }
                prefs.edit().putStringSet(KEY_FAVORITE_ROUTES, updatedSet).apply()
                _favoriteKeys.value = updatedSet
                PersistenceResult.Success(updatedSet)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = "Failed to toggle favorite stand: ${e.message ?: "database error"}"
                _lastError.value = msg
                PersistenceResult.Failure(msg, e)
            }
        }

    override suspend fun removeFavoritePersist(routeKey: String): PersistenceResult<Set<String>> =
        withContext(ioDispatcher) {
            val cleanKey = routeKey.trim()
            val dao = favoriteRouteDao
            if (dao == null) {
                removeFavorite(cleanKey)
                return@withContext PersistenceResult.Success(_favoriteKeys.value)
            }
            try {
                val updatedSet = executeInTransaction {
                    dao.deleteByKey(cleanKey)
                    dao.getAllOnce().map { it.routeKey }.toSet()
                }
                prefs.edit().putStringSet(KEY_FAVORITE_ROUTES, updatedSet).apply()
                _favoriteKeys.value = updatedSet
                PersistenceResult.Success(updatedSet)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = "Failed to remove favorite stand: ${e.message ?: "database error"}"
                _lastError.value = msg
                PersistenceResult.Failure(msg, e)
            }
        }

    override suspend fun clearAllFavoritesPersist(): PersistenceResult<Unit> =
        withContext(ioDispatcher) {
            val dao = favoriteRouteDao
            if (dao == null) {
                clearAllFavorites()
                return@withContext PersistenceResult.Success(Unit)
            }
            try {
                executeInTransaction {
                    dao.clearAll()
                }
                prefs.edit().remove(KEY_FAVORITE_ROUTES).apply()
                _favoriteKeys.value = emptySet()
                PersistenceResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = "Failed to clear favorite stands: ${e.message ?: "database error"}"
                _lastError.value = msg
                PersistenceResult.Failure(msg, e)
            }
        }

    override fun clearError() {
        _lastError.value = null
    }

    private suspend fun runRoomWrite(block: suspend () -> Unit) {
        try {
            executeInTransaction(block)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _lastError.value = "Failed to persist favorite change: ${e.message ?: "database error"}"
        }
    }

    private suspend fun <T> executeInTransaction(block: suspend () -> T): T {
        val runner = transactionRunner
        return if (runner != null) runner.runInTransaction(block) else block()
    }

    companion object {
        const val PREFS_NAME = "mumbai_share_auto_favorites"
        const val KEY_FAVORITE_ROUTES = "favorite_route_keys"
    }
}
