package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.MAX_PUBLISH_ATTEMPTS
import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.model.RecentItem
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import com.nyasa.notifybridge.domain.repo.RecentNotificationsRepository
import com.nyasa.notifybridge.fakes.FakeMqttClientManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec §3.2 head-of-line protection — on publish failure the drain routes
 *  through [OutboxRepository.recordFailureOrFailTerminal] (with the spec cap)
 *  rather than the plain [OutboxRepository.recordFailure]. */
class DrainOutboxHeadOfLineTest {

    private class RecordingOutbox(private val batch: List<OutboxItem>) : OutboxRepository {
        data class FailTerminalCall(val id: Long, val maxAttempts: Int)

        var failTerminalCall: FailTerminalCall? = null
        var plainRecordFailureId: Long? = null

        override suspend fun enqueue(item: OutboxItem) {}
        override suspend fun nextBatch(limit: Int): List<OutboxItem> = batch
        override suspend fun markPublished(id: Long) {}
        override suspend fun recordFailure(id: Long) { plainRecordFailureId = id }
        override suspend fun recordFailureOrFailTerminal(id: Long, maxAttempts: Int) {
            failTerminalCall = FailTerminalCall(id, maxAttempts)
        }
        override suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int) {}
        override fun depth(): Flow<Int> = flowOf(0)
        override fun failedDropCount(): Flow<Int> = flowOf(0)
        override fun pendingCount(): Flow<Int> = flowOf(0)
    }

    private class NoopRecent : RecentNotificationsRepository {
        override val recent: Flow<List<RecentItem>> = flowOf(emptyList())
        override suspend fun recordPublished(item: OutboxItem, publishedAt: Long) = Unit
    }

    private fun item(id: Long = 1L) =
        OutboxItem(id = id, topic = "t", payload = "p", createdAt = 1L, attemptCount = 0)

    @Test
    fun publish_failure_routes_through_recordFailureOrFailTerminal_with_spec_cap() = runTest {
        val outbox = RecordingOutbox(batch = listOf(item(id = 1L)))
        val mqtt = FakeMqttClientManager().apply { failPublish = true }

        DrainOutboxUseCase(outbox, mqtt, NoopRecent())()

        assertEquals(
            RecordingOutbox.FailTerminalCall(1L, MAX_PUBLISH_ATTEMPTS),
            outbox.failTerminalCall,
        )
        assertNull("plain recordFailure must not be used in the drain path", outbox.plainRecordFailureId)
    }

    @Test
    fun successful_publish_never_records_a_failure() = runTest {
        val outbox = RecordingOutbox(batch = listOf(item(id = 1L)))
        val mqtt = FakeMqttClientManager() // publishes successfully

        DrainOutboxUseCase(outbox, mqtt, NoopRecent())()

        assertNull(outbox.failTerminalCall)
        assertNull(outbox.plainRecordFailureId)
    }
}
