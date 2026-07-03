package com.nyasa.notifybridge.domain.repo

import com.nyasa.notifybridge.domain.model.OutboxItem
import kotlinx.coroutines.flow.Flow

interface OutboxRepository {
    suspend fun enqueue(item: OutboxItem)
    suspend fun nextBatch(limit: Int): List<OutboxItem>
    suspend fun markPublished(id: Long)
    suspend fun recordFailure(id: Long)

    /** Spec §3.2 head-of-line: replaces [recordFailure] in the drain path.
     *  Atomically bumps attemptCount and transitions to FAILED_TERMINAL when
     *  the cap is reached, so a poison row stops blocking the queue head. */
    suspend fun recordFailureOrFailTerminal(id: Long, maxAttempts: Int)

    suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int)
    fun depth(): Flow<Int>

    /** Spec §3.5 Diagnostics — live count of `FAILED_TERMINAL` (dropped) rows. */
    fun failedDropCount(): Flow<Int>

    /** Spec §3.5 Diagnostics — live count of `PENDING` (drainable) rows. */
    fun pendingCount(): Flow<Int>
}
