package com.nyasa.notifybridge.domain.repo

import com.nyasa.notifybridge.domain.model.OutboxItem
import kotlinx.coroutines.flow.Flow

interface OutboxRepository {
    suspend fun enqueue(item: OutboxItem)
    suspend fun nextBatch(limit: Int): List<OutboxItem>
    suspend fun markPublished(id: Long)

    /** Spec §3.2 head-of-line: the drain's failure path. Atomically bumps
     *  attemptCount and transitions the row to FAILED_TERMINAL once the cap
     *  is reached, so a poison row stops blocking the queue head. */
    suspend fun recordFailureOrFailTerminal(id: Long, maxAttempts: Int)

    suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int)

    /** Spec §3.5 Diagnostics — live count of `FAILED_TERMINAL` (dropped) rows. */
    fun failedDropCount(): Flow<Int>

    /** Spec §3.5 Diagnostics — live count of `PENDING` (drainable) rows. */
    fun pendingCount(): Flow<Int>
}
