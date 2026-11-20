package com.mumbaishareauto.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteRouteDao {
    @Query("SELECT * FROM favorite_routes ORDER BY savedAtMs DESC")
    fun observeAll(): Flow<List<FavoriteRouteEntity>>

    @Query("SELECT routeKey FROM favorite_routes ORDER BY savedAtMs DESC")
    fun observeRouteKeys(): Flow<List<String>>

    @Query("SELECT * FROM favorite_routes ORDER BY savedAtMs DESC")
    suspend fun getAllOnce(): List<FavoriteRouteEntity>

    @Query("SELECT * FROM favorite_routes WHERE routeKey = :routeKey LIMIT 1")
    suspend fun getByKey(routeKey: String): FavoriteRouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteRouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<FavoriteRouteEntity>)

    @Query("DELETE FROM favorite_routes WHERE routeKey = :routeKey")
    suspend fun deleteByKey(routeKey: String): Int

    @Query("DELETE FROM favorite_routes")
    suspend fun clearAll()
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<BookingEntity>>

    @Query("SELECT * FROM bookings ORDER BY updatedAtMs DESC")
    suspend fun getAllOnce(): List<BookingEntity>

    @Query("SELECT * FROM bookings WHERE bookingId = :bookingId LIMIT 1")
    suspend fun getById(bookingId: String): BookingEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNew(entity: BookingEntity)

    @Update
    suspend fun updateExisting(entity: BookingEntity): Int

    @Query("DELETE FROM bookings WHERE bookingId = :bookingId")
    suspend fun deleteById(bookingId: String): Int

    @Query("DELETE FROM bookings")
    suspend fun clearAll()
}

@Dao
interface DriverRouteDao {
    @Query("SELECT * FROM driver_routes ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<DriverRouteEntity>>

    @Query("SELECT * FROM driver_routes ORDER BY createdAtMs DESC")
    suspend fun getAllOnce(): List<DriverRouteEntity>

    @Query("SELECT * FROM driver_routes WHERE postingId = :postingId LIMIT 1")
    suspend fun getById(postingId: String): DriverRouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DriverRouteEntity)

    @Query("DELETE FROM driver_routes WHERE postingId = :postingId")
    suspend fun deleteById(postingId: String): Int

    @Query("DELETE FROM driver_routes")
    suspend fun clearAll()
}

@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    fun observe(): Flow<UserPreferencesEntity?>

    @Query("SELECT * FROM user_preferences WHERE id = 1 LIMIT 1")
    suspend fun getOnce(): UserPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserPreferencesEntity)

    @Query("DELETE FROM user_preferences")
    suspend fun clearAll()
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("SELECT * FROM notifications ORDER BY createdAtMs DESC")
    suspend fun getAllOnce(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NotificationEntity?

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entities: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String): Int

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllRead(): Int

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
