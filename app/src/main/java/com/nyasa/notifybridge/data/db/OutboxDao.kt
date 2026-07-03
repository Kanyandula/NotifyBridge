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

    // ----- NEW for spec §3.2 head-of-line protection -----

    /** Oldest [limit] rows in `PENDING` status. Excludes `FAILED_TERMINAL`
     *  rows so the drain loop never retries a permanently-failed head. */
    @Query("SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY id ASC LIMIT :limit")
    suspend fun oldestPending(limit: Int): List<OutboxEntity>

    /** Sets status to `FAILED_TERMINAL`; leaves `attemptCount` unchanged. */
    @Query("UPDATE outbox SET status = 'FAILED_TERMINAL' WHERE id = :id")
    suspend fun markFailedTerminal(id: Long)

    /** Used internally by [bumpAttemptOrFail]. */
    @Query("SELECT * FROM outbox WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): OutboxEntity?

    /** Spec §3.2: increment attempt count; if it reaches [maxAttempts],
     *  also transition status to `FAILED_TERMINAL`. Single transaction so a
     *  concurrent reader never sees `attemptCount >= maxAttempts` while
     *  `status = 'PENDING'`. */
    @Transaction
    suspend fun bumpAttemptOrFail(id: Long, maxAttempts: Int) {
        bumpAttempt(id)
        val updated = findById(id) ?: return
        if (updated.attemptCount >= maxAttempts && updated.status == OutboxStatus.PENDING.name) {
            markFailedTerminal(id)
        }
    }

    /** Spec §3.5: live count of `FAILED_TERMINAL` rows for the Diagnostics UI. */
    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'FAILED_TERMINAL'")
    fun failedDropCountFlow(): Flow<Int>

    /** Spec §3.5: live count of `PENDING` rows (the drainable outbox depth). */
    @Query("SELECT COUNT(*) FROM outbox WHERE status = 'PENDING'")
    fun pendingCountFlow(): Flow<Int>

    /** Test-only helper — full table dump in insertion order. */
    @Query("SELECT * FROM outbox ORDER BY id ASC")
    suspend fun allForTest(): List<OutboxEntity>
}
