package com.nyasa.notifybridge.data.db

import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class OutboxRepositoryImpl @Inject constructor(
    private val dao: OutboxDao,
) : OutboxRepository {
    override suspend fun enqueue(item: OutboxItem) =
        dao.insert(OutboxEntity(
            topic = item.topic, payload = item.payload,
            createdAt = item.createdAt, attemptCount = item.attemptCount)).let {}
    override suspend fun nextBatch(limit: Int): List<OutboxItem> =
        dao.oldest(limit).map {
            OutboxItem(it.id, it.topic, it.payload, it.createdAt, it.attemptCount) }
    override suspend fun markPublished(id: Long) = dao.deleteById(id)
    override suspend fun recordFailure(id: Long) = dao.bumpAttempt(id)
    override suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int) {
        dao.deleteOlderThan(nowMs - ttlMs)
        dao.trimToMax(maxRows)
    }
    override fun depth(): Flow<Int> = dao.countFlow()
}
