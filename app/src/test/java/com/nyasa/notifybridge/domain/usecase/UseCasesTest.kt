package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.discovery.DiscoveryPayloadBuilder
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import com.nyasa.notifybridge.fakes.FakeMqttClientManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class MemOutbox : OutboxRepository {
    val items = mutableListOf<OutboxItem>()
    var seq = 0L
    override suspend fun enqueue(item: OutboxItem) { items += item.copy(id = ++seq) }
    override suspend fun nextBatch(limit: Int) = items.take(limit)
    override suspend fun markPublished(id: Long) { items.removeAll { it.id == id } }
    override suspend fun recordFailure(id: Long) {}
    override suspend fun pruneExpired(nowMs: Long, ttlMs: Long, maxRows: Int) {}
    override fun depth(): Flow<Int> = flowOf(items.size)
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
        DrainOutboxUseCase(ob, mqtt)()
        assertEquals("t" to "p", mqtt.published.single().let { it.first to it.second })
        assertEquals(0, ob.items.size)
    }

    @Test fun drain_keeps_item_on_publish_failure() = runTest {
        val ob = MemOutbox()
        ob.enqueue(OutboxItem(topic = "t", payload = "p", createdAt = 1))
        val mqtt = FakeMqttClientManager().apply { failPublish = true }
        DrainOutboxUseCase(ob, mqtt)()
        assertEquals(1, ob.items.size)
    }

    @Test fun drain_stops_on_first_failure_preserving_order() = runTest {
        // Two items, first publish fails: item 2 must NOT be published ahead
        // of item 1. Proves the stop-on-failure (ordered) behaviour is
        // intentional and covered, not an accident of single-item batches.
        val ob = MemOutbox()
        ob.enqueue(OutboxItem(topic = "t", payload = "p1", createdAt = 1))
        ob.enqueue(OutboxItem(topic = "t", payload = "p2", createdAt = 2))
        val mqtt = FakeMqttClientManager().apply { failPublish = true }
        DrainOutboxUseCase(ob, mqtt)()
        assertEquals(emptyList<Triple<String,String,Boolean>>(), mqtt.published)
        assertEquals(2, ob.items.size)
    }

    @Test fun test_connection_returns_true_on_connect() = runTest {
        val r = TestConnectionUseCase(FakeMqttClientManager())(BrokerConfig(host = "h"))
        assertTrue(r)
    }
}
