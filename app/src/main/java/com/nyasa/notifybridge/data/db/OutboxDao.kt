package com.nyasa.notifybridge.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert suspend fun insert(e: OutboxEntity): Long
    @Query("SELECT * FROM outbox ORDER BY id ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<OutboxEntity>
    @Query("DELETE FROM outbox WHERE id = :id") suspend fun deleteById(id: Long)
    @Query("UPDATE outbox SET attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun bumpAttempt(id: Long)
    @Query("SELECT COUNT(*) FROM outbox") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM outbox") fun countFlow(): Flow<Int>
    @Query("DELETE FROM outbox WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
    @Query("DELETE FROM outbox WHERE id NOT IN (SELECT id FROM outbox ORDER BY id DESC LIMIT :max)")
    suspend fun trimToMax(max: Int)

    /**
     * TTL + cap prune as one transaction so a concurrent enqueue / `markPublished`
     * can't observe a half-pruned state (and `trimToMax`'s subquery sees a
     * consistent table).
     */
    @Transaction
    suspend fun prune(cutoff: Long, max: Int) {
        deleteOlderThan(cutoff)
        trimToMax(max)
    }
}
