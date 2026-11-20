package com.mumbaishareauto.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Abstraction for atomic database transactions across production Room DB and JVM unit tests.
 */
interface TransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

class RoomTransactionRunner(
    private val database: RoomDatabase
) : TransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        database.withTransaction(block)
}

/**
 * Local SQLite persistence database for Mumbai Share Auto.
 *
 * Schema History:
 * - Version 1: Initial `favorite_routes(routeKey, savedAtMs)` and `user_preferences(...)` foundation.
 * - Version 2: Adds `sourceScope` to `favorite_routes`, migration flags to `user_preferences`,
 *   and creates `bookings`, `driver_routes`, and `notifications` tables with verified indices.
 *
 * Strictly preserves user data across upgrades via explicit `MIGRATION_1_2` (no silent destructive fallback).
 */
@Database(
    entities = [
        FavoriteRouteEntity::class,
        BookingEntity::class,
        DriverRouteEntity::class,
        UserPreferencesEntity::class,
        NotificationEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class MumbaiShareAutoDatabase : RoomDatabase() {

    abstract fun favoriteRouteDao(): FavoriteRouteDao
    abstract fun bookingDao(): BookingDao
    abstract fun driverRouteDao(): DriverRouteDao
    abstract fun userPreferencesDao(): UserPreferencesDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        const val DATABASE_NAME = "mumbai_share_auto.db"

        /**
         * Preserves existing Version 1 rows cleanly while adding Phase 6 columns and tables.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `favorite_routes` ADD COLUMN `sourceScope` TEXT NOT NULL DEFAULT 'DEVICE_LOCAL'"
                )
                db.execSQL(
                    "ALTER TABLE `user_preferences` ADD COLUMN `favoritesMigratedFromPrefs` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE `user_preferences` ADD COLUMN `agreementsMigratedFromPrefs` INTEGER NOT NULL DEFAULT 0"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bookings` (
                        `bookingId` TEXT NOT NULL,
                        `postingId` TEXT NOT NULL,
                        `partyId` TEXT NOT NULL,
                        `passengerCount` INTEGER NOT NULL,
                        `contactRef` TEXT NOT NULL,
                        `agreedTotalFare` REAL NOT NULL,
                        `designatedStationStand` TEXT NOT NULL,
                        `destination` TEXT NOT NULL,
                        `state` TEXT NOT NULL,
                        `lockedFareInr` REAL,
                        `cancellationReason` TEXT,
                        `dataOrigin` TEXT NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`bookingId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bookings_state` ON `bookings` (`state`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bookings_postingId` ON `bookings` (`postingId`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `driver_routes` (
                        `postingId` TEXT NOT NULL,
                        `driverId` TEXT NOT NULL,
                        `designatedPickupStand` TEXT NOT NULL,
                        `destinationStand` TEXT NOT NULL,
                        `viaPointsSummary` TEXT NOT NULL,
                        `totalBookingFare` REAL NOT NULL,
                        `availableSeats` INTEGER NOT NULL,
                        `maxPermittedPassengers` INTEGER NOT NULL,
                        `isLegallyVerified` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `isLiveServerBroadcast` INTEGER NOT NULL,
                        `dataOrigin` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`postingId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_driver_routes_status` ON `driver_routes` (`status`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_driver_routes_isLiveServerBroadcast` ON `driver_routes` (`isLiveServerBroadcast`)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `id` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `isRead` INTEGER NOT NULL,
                        `isLocalNotice` INTEGER NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notifications_isRead` ON `notifications` (`isRead`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notifications_createdAtMs` ON `notifications` (`createdAtMs`)"
                )
            }
        }

        @Volatile
        private var instance: MumbaiShareAutoDatabase? = null

        fun getInstance(context: Context): MumbaiShareAutoDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MumbaiShareAutoDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
