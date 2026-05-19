package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrainOutboxUseCase @Inject constructor(
    private val outbox: OutboxRepository,
    private val mqtt: MqttClientManager,
) {
    // Single-flight guard. Three triggers can fire `invoke` concurrently —
    // OutboxDrainWorker.doWork (periodic), MqttForegroundService.onCreate, and
    // MqttForegroundService.onStartCommand (every enqueue signal). Without
    // serialization they `nextBatch` overlapping rows and double-publish before
    // `markPublished` deletes them, producing duplicate QoS1 deliveries. The
    // @Singleton scope ensures the same mutex is shared across all injectors.
    private val mutex = Mutex()

    suspend operator fun invoke(batch: Int = 50) = mutex.withLock {
        for (item in outbox.nextBatch(batch)) {
            val ok = mqtt.publish(item.topic, item.payload, qos = 1, retained = false)
            // Stop on first failure — preserves delivery order. Skipping ahead
            // would reorder notifications; the remaining items are retried on
            // the next drain (foreground service / 15-min worker).
            if (ok) outbox.markPublished(item.id) else { outbox.recordFailure(item.id); break }
        }
        runCatching {
            outbox.pruneExpired(
                nowMs = System.currentTimeMillis(),
                ttlMs = OUTBOX_TTL_MS,
                maxRows = OUTBOX_MAX_ROWS,
            )
        }
    }

    companion object {
        /** Outbox rows older than this are pruned (7 days). */
        const val OUTBOX_TTL_MS = 7L * 24 * 60 * 60 * 1000

        /** Hard cap on retained outbox rows; oldest beyond this are trimmed. */
        const val OUTBOX_MAX_ROWS = 5_000
    }
}
