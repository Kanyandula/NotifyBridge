package com.nyasa.notifybridge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentNotificationDao {
    @Insert
    suspend fun insert(entity: RecentNotificationEntity): Long

    @Query("SELECT * FROM recent_notifications ORDER BY id DESC LIMIT :limit")
    fun observeNewest(limit: Int): Flow<List<RecentNotificationEntity>>

    @Query(
        """
        DELETE FROM recent_notifications
        WHERE id NOT IN (
            SELECT id FROM recent_notifications ORDER BY id DESC LIMIT :max
        )
        """,
    )
    suspend fun trimToMax(max: Int)

    @Query("SELECT COUNT(*) FROM recent_notifications")
    suspend fun count(): Int

    @Transaction
    suspend fun insertAndTrim(entity: RecentNotificationEntity, max: Int): Long {
        val id = insert(entity)
        trimToMax(max)
        return id
    }
}
