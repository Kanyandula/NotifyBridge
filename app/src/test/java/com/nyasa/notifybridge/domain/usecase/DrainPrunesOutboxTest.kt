package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import com.nyasa.notifybridge.fakes.FakeMqttClientManager
import com.nyasa.notifybridge.fakes.NoopRecentNotificationsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

private class RecordingOutbox : OutboxRepository {
    data class PruneCall(val nowMs: Long, val ttlMs: Long, val maxRows: Int)

    var pruneCall: PruneCall? = null

    override suspend fun enqueue(item: OutboxItem) {}
    override suspend fun nextBatch(limit: Int): List<OutboxItem> = emptyList()
    override suspend fun markPublished(id: Long) {}
    override suspend fun recordFailureOrFailTerminal(id: Long, maxAttempts: Int) {}
    override suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int) {
        pruneCall = PruneCall(nowMs, ttlMs, maxRows)
    }
    override fun depth(): Flow<Int> = flowOf(0)
    override fun failedDropCount(): Flow<Int> = flowOf(0)
    override fun pendingCount(): Flow<Int> = flowOf(0)
}

class DrainPrunesOutboxTest {

    @Test
    fun drain_calls_pruneExpired_with_spec_constants() = runTest {
        val outbox = RecordingOutbox()
        DrainOutboxUseCase(outbox, FakeMqttClientManager(), NoopRecentNotificationsRepository())()

        val call = outbox.pruneCall
        assertNotNull("pruneExpired was never called", call)
        assertEquals(7L * 24 * 60 * 60 * 1000, call!!.ttlMs)
        assertEquals(5_000, call.maxRows)
    }
}
