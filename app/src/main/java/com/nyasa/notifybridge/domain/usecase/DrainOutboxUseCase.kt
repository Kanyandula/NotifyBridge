package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import javax.inject.Inject

class DrainOutboxUseCase @Inject constructor(
    private val outbox: OutboxRepository,
    private val mqtt: MqttClientManager,
) {
    suspend operator fun invoke(batch: Int = 50) {
        for (item in outbox.nextBatch(batch)) {
            val ok = mqtt.publish(item.topic, item.payload, qos = 1, retained = false)
            // Stop on first failure — preserves delivery order. Skipping ahead
            // would reorder notifications; the remaining items are retried on
            // the next drain (foreground service / 15-min worker).
            if (ok) outbox.markPublished(item.id) else { outbox.recordFailure(item.id); break }
        }
        outbox.pruneExpired(nowMs = System.currentTimeMillis(), ttlMs = 7L * 24 * 60 * 60 * 1000, maxRows = 5_000)
    }
}
