package com.nyasa.notifybridge.domain.repo

import com.nyasa.notifybridge.domain.model.OutboxItem
import kotlinx.coroutines.flow.Flow

interface OutboxRepository {
    suspend fun enqueue(item: OutboxItem)
    suspend fun nextBatch(limit: Int): List<OutboxItem>
    suspend fun markPublished(id: Long)
    suspend fun recordFailure(id: Long)
    suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int)
    fun depth(): Flow<Int>
}
