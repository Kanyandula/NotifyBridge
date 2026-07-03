package com.nyasa.notifybridge.data.db

import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutboxRepositoryImpl @Inject constructor(
    private val dao: OutboxDao,
) : OutboxRepository {
    override suspend fun enqueue(item: OutboxItem) {
        // Insert returns the new row id, which callers don't need.
        dao.insert(OutboxEntity(
            topic = item.topic,
            payload = item.payload,
            createdAt = item.createdAt,
            attemptCount = item.attemptCount,
        ))
    }
    override suspend fun nextBatch(limit: Int): List<OutboxItem> =
        dao.oldestPending(limit).map {
            OutboxItem(it.id, it.topic, it.payload, it.createdAt, it.attemptCount) }
    override suspend fun markPublished(id: Long) = dao.deleteById(id)
    override suspend fun recordFailureOrFailTerminal(id: Long, maxAttempts: Int) =
        dao.bumpAttemptOrFail(id, maxAttempts)
    override suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int) =
        dao.prune(cutoff = nowMs - ttlMs, max = maxRows)
    override fun depth(): Flow<Int> = dao.countFlow()
    override fun failedDropCount(): Flow<Int> = dao.failedDropCountFlow()
    override fun pendingCount(): Flow<Int> = dao.pendingCountFlow()
}
