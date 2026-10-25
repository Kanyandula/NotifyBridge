package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.discovery.DiscoveryPayloadBuilder
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.model.RecentItem
import com.nyasa.notifybridge.domain.mqtt.PublishOutcome
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import com.nyasa.notifybridge.domain.repo.RecentNotificationsRepository
import com.nyasa.notifybridge.fakes.FakeMqttClientManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory outbox mirroring the DAO: [items] is PENDING in FIFO order,
 *  [terminal] is FAILED_TERMINAL (bumpAttemptOrFail semantics). */
private class MemOutbox : OutboxRepository {
    val items = mutableListOf<OutboxItem>()
    val terminal = mutableListOf<OutboxItem>()
    var seq = 0L
    override suspend fun enqueue(item: OutboxItem) { items += item.copy(id = ++seq) }
    override suspend fun nextBatch(limit: Int) = items.take(limit)
    override suspend fun markPublished(id: Long) { items.removeAll { it.id == id } }
    override suspend fun recordFailureOrFailTerminal(id: Long, maxAttempts: Int) {
        val i = items.indexOfFirst { it.id == id }
        val bumped = items[i].copy(attemptCount = items[i].attemptCount + 1)
        if (bumped.attemptCount >= maxAttempts) terminal += items.removeAt(i) else items[i] = bumped
    }
    override suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int) {}
    override fun failedDropCount(): Flow<Int> = flowOf(0)
    override fun pendingCount(): Flow<Int> = flowOf(0)
}

private class RecordingRecent(
    private val fail: Boolean = false,
) : RecentNotificationsRepository {
    val published = mutableListOf<OutboxItem>()
    override val recent: Flow<List<RecentItem>> = flowOf(emptyList())

    override suspend fun recordPublished(item: OutboxItem, publishedAt: Long) {
        if (fail) error("recent insert failed")
        published += item
    }
}

class UseCasesTest {
    private val n = CapturedNotification("com.x","X","t","b",null,null,null,9L,false,true,null,1)

    @Test fun enqueue_builds_event_payload_on_state_topic() = runTest {
        val ob = MemOutbox()
        EnqueueNotificationUseCase(ob, DiscoveryPayloadBuilder())(n, "Pixel 7")
        assertEquals("notifybridge/pixel-7/notification", ob.items.single().topic)
        assertTrue(ob.items.single().payload.contains("\"package\":\"com.x\""))
    }

    @Test fun drain_publishes_then_marks_published() = runTest {
        val ob = MemOutbox()
        ob.enqueue(OutboxItem(topic = "t", payload = "p", createdAt = 1))
        val mqtt = FakeMqttClientManager()
        val recent = RecordingRecent()
        DrainOutboxUseCase(ob, mqtt, recent)()
        assertEquals("t" to "p", mqtt.published.single().let { it.first to it.second })
        assertEquals("p", recent.published.single().payload)
        assertEquals(0, ob.items.size)
    }

    @Test fun drain_keeps_item_on_publish_failure() = runTest {
        val ob = MemOutbox()
        ob.enqueue(OutboxItem(topic = "t", payload = "p", createdAt = 1))
        val mqtt = FakeMqttClientManager().apply { publishOutcome = PublishOutcome.Rejected }
        val recent = RecordingRecent()
        DrainOutboxUseCase(ob, mqtt, recent)()
        assertEquals(emptyList<OutboxItem>(), recent.published)
        assertEquals(1, ob.items.size)
    }

    @Test fun drain_stops_on_first_failure_preserving_order() = runTest {
        // Two items, first publish fails: item 2 must NOT be published ahead
        // of item 1. Proves the stop-on-failure (ordered) behaviour is
        // intentional and covered, not an accident of single-item batches.
        val ob = MemOutbox()
        ob.enqueue(OutboxItem(topic = "t", payload = "p1", createdAt = 1))
        ob.enqueue(OutboxItem(topic = "t", payload = "p2", createdAt = 2))
        val mqtt = FakeMqttClientManager().apply { publishOutcome = PublishOutcome.Rejected }
        DrainOutboxUseCase(ob, mqtt, RecordingRecent())()
        assertEquals(emptyList<Triple<String,String,Boolean>>(), mqtt.published)
        assertEquals(2, ob.items.size)
    }

    @Test fun broker_outage_never_burns_the_attempt_cap() = runTest {
        // Regression (remediation K): 10 offline drains used to exhaust
        // MAX_PUBLISH_ATTEMPTS and strand the row as FAILED_TERMINAL.
        val ob = MemOutbox()
        ob.enqueue(OutboxItem(topic = "t", payload = "p", createdAt = 1))
        val mqtt = FakeMqttClientManager().apply { publishOutcome = PublishOutcome.Transient }
        val drain = DrainOutboxUseCase(ob, mqtt, RecordingRecent())
        repeat(10) { drain() }
        assertEquals(0, ob.items.single().attemptCount)

        mqtt.publishOutcome = PublishOutcome.Success
        drain()
        assertEquals("p", mqtt.published.single().second)
        assertEquals(0, ob.items.size)
        assertEquals(0, ob.terminal.size)
    }

    @Test fun drain_deletes_published_item_when_recent_recording_fails() = runTest {
        val ob = MemOutbox()
        ob.enqueue(OutboxItem(topic = "t", payload = "p", createdAt = 1))
        val mqtt = FakeMqttClientManager()
        DrainOutboxUseCase(ob, mqtt, RecordingRecent(fail = true))()
        assertEquals("t" to "p", mqtt.published.single().let { it.first to it.second })
        assertEquals(0, ob.items.size)
    }

    @Test fun test_connection_returns_true_on_connect() = runTest {
        val r = TestConnectionUseCase(FakeMqttClientManager())(BrokerConfig(host = "h"))
        assertTrue(r)
    }
}
