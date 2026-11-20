package com.mumbaishareauto.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mumbaishareauto.app.data.local.FavoriteRouteRepository
import com.mumbaishareauto.app.data.local.PersistenceResult
import com.mumbaishareauto.app.data.local.RoomBookingRepository
import com.mumbaishareauto.app.data.local.RoomDriverRouteRepository
import com.mumbaishareauto.app.data.local.db.BookingEntity
import com.mumbaishareauto.app.data.local.db.DriverRouteEntity
import com.mumbaishareauto.app.data.local.db.FavoriteRouteEntity
import com.mumbaishareauto.app.data.local.db.MumbaiShareAutoDatabase
import com.mumbaishareauto.app.data.local.db.NotificationEntity
import com.mumbaishareauto.app.data.local.db.RoomTransactionRunner
import com.mumbaishareauto.app.data.local.db.UserPreferencesEntity
import com.mumbaishareauto.app.ui.theme.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationAndPersistenceTest {

    @Test
    fun migration1To2_preservesLegacyV1RowsAndCreatesNewTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_1_2_test.db"
        context.deleteDatabase(dbName)

        // Build an authentic Version 1 SQLite schema directly using FrameworkSQLiteOpenHelper
        val v1Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `favorite_routes` (
                                `routeKey` TEXT NOT NULL,
                                `savedAtMs` INTEGER NOT NULL,
                                PRIMARY KEY(`routeKey`)
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `user_preferences` (
                                `id` INTEGER NOT NULL,
                                `onboardingCompleted` INTEGER NOT NULL,
                                `preferredMode` TEXT NOT NULL,
                                `themePreference` TEXT NOT NULL,
                                `updatedAtMs` INTEGER NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO `favorite_routes` (`routeKey`, `savedAtMs`) VALUES ('w_bnd_e_bkc', 1700000000000)"
                        )
                        db.execSQL(
                            "INSERT INTO `user_preferences` (`id`, `onboardingCompleted`, `preferredMode`, `themePreference`, `updatedAtMs`) VALUES (1, 1, 'DRIVER', 'DARK', 1700000000000)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        v1Helper.writableDatabase.close()
        v1Helper.close()

        // Open same file via Room v2 with MIGRATION_1_2
        val migratedDb = Room.databaseBuilder(context, MumbaiShareAutoDatabase::class.java, dbName)
            .addMigrations(MumbaiShareAutoDatabase.MIGRATION_1_2)
            .build()

        val favorites = migratedDb.favoriteRouteDao().getAllOnce()
        assertEquals(1, favorites.size)
        assertEquals("w_bnd_e_bkc", favorites.first().routeKey)
        assertEquals(FavoriteRouteEntity.SOURCE_DEVICE_LOCAL, favorites.first().sourceScope)

        val prefs = migratedDb.userPreferencesDao().getOnce()
        assertEquals("DRIVER", prefs?.preferredMode)
        assertEquals(false, prefs?.favoritesMigratedFromPrefs)

        // Verify newly created v2 tables work cleanly
        migratedDb.notificationDao().upsert(
            NotificationEntity(
                id = "migrated_notice",
                category = NotificationEntity.CATEGORY_STORAGE,
                title = "Migration Verified",
                message = "Schema upgraded 1 -> 2 safely",
                isRead = false,
                isLocalNotice = true,
                createdAtMs = 1700000005000L
            )
        )
        assertEquals(1, migratedDb.notificationDao().getCount())

        migratedDb.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun databaseReopenAfterClose_persistsCrudAndLegacySharedPreferencesMigration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "restart_persistence_test.db"
        val prefsName = "restart_fav_prefs_test"
        context.deleteDatabase(dbName)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()

        val legacyPrefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        legacyPrefs.edit()
            .putStringSet(FavoriteRouteRepository.KEY_FAVORITE_ROUTES, setOf("c_dadar_e_matunga"))
            .commit()

        val db1 = Room.databaseBuilder(context, MumbaiShareAutoDatabase::class.java, dbName)
            .addMigrations(MumbaiShareAutoDatabase.MIGRATION_1_2)
            .build()
        val txRunner1 = RoomTransactionRunner(db1)
        val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val favRepo1 = FavoriteRouteRepository(
            prefs = legacyPrefs,
            favoriteRouteDao = db1.favoriteRouteDao(),
            userPreferencesDao = db1.userPreferencesDao(),
            transactionRunner = txRunner1,
            externalScope = repoScope
        )
        assertTrue(favRepo1.migrateLegacyFavorites() is PersistenceResult.Success)
        assertTrue(favRepo1.toggleFavoritePersist("w_adh_e_seepz") is PersistenceResult.Success)

        val bookingRepo1 = RoomBookingRepository(
            bookingDao = db1.bookingDao(),
            userPreferencesDao = db1.userPreferencesDao(),
            transactionRunner = txRunner1,
            externalScope = repoScope
        )
        val booking = BookingAgreement(
            bookingId = "BK_ANDROID_1",
            postingId = "POST_ANDROID_1",
            passengerParty = PassengerParty("PARTY_ANDROID_1", 3),
            agreedTotalFare = 150.0,
            designatedStationStand = "Andheri Station East Auto Stand",
            destination = "SEEPZ Main Gate",
            state = BookingLifecycleState.OFFERED
        )
        assertTrue(bookingRepo1.registerNewBooking(booking) is PersistenceResult.Success)
        // Duplicate insert must fail at Room SQLite OnConflictStrategy.ABORT layer too
        assertTrue(bookingRepo1.registerNewBooking(booking) is PersistenceResult.Failure)

        val driverRepo1 = RoomDriverRouteRepository(
            driverRouteDao = db1.driverRouteDao(),
            transactionRunner = txRunner1,
            externalScope = repoScope
        )
        assertTrue(
            driverRepo1.saveLocalRoutePosting(
                DriverRouteEntity(
                    postingId = "POST_ANDROID_1",
                    driverId = "DRV_ANDROID_1",
                    designatedPickupStand = "Andheri Station East Auto Stand",
                    destinationStand = "SEEPZ Main Gate",
                    viaPointsSummary = "Western Express Highway",
                    totalBookingFare = 150.0,
                    availableSeats = 3,
                    maxPermittedPassengers = 3,
                    isLegallyVerified = false,
                    isLiveServerBroadcast = false,
                    createdAtMs = 1700000010000L
                )
            ) is PersistenceResult.Success
        )
        db1.userPreferencesDao().upsert(
            UserPreferencesEntity(
                onboardingCompleted = true,
                preferredMode = UserMode.PASSENGER.name,
                themePreference = ThemePreference.DARK.name,
                favoritesMigratedFromPrefs = true,
                updatedAtMs = 1700000010000L
            )
        )
        repoScope.cancel()
        db1.close()

        // Reopen database cleanly to simulate process restart
        val db2 = Room.databaseBuilder(context, MumbaiShareAutoDatabase::class.java, dbName)
            .addMigrations(MumbaiShareAutoDatabase.MIGRATION_1_2)
            .build()

        val reopenedKeys = db2.favoriteRouteDao().observeRouteKeys().first().toSet()
        assertEquals(setOf("c_dadar_e_matunga", "w_adh_e_seepz"), reopenedKeys)

        val reopenedBookings: List<BookingEntity> = db2.bookingDao().getAllOnce()
        assertEquals(1, reopenedBookings.size)
        assertEquals("BK_ANDROID_1", reopenedBookings.first().bookingId)

        val reopenedRoutes = db2.driverRouteDao().getAllOnce()
        assertEquals(1, reopenedRoutes.size)
        assertFalse(reopenedRoutes.first().isLiveServerBroadcast)

        val reopenedPrefs = db2.userPreferencesDao().getOnce()
        assertEquals(ThemePreference.DARK.name, reopenedPrefs?.themePreference)

        db2.close()
        context.deleteDatabase(dbName)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
