package com.nyasa.notifybridge.domain.usecase

import com.nyasa.notifybridge.domain.MAX_PUBLISH_ATTEMPTS
import com.nyasa.notifybridge.domain.model.OutboxItem
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.mqtt.PublishOutcome
import com.nyasa.notifybridge.domain.repo.OutboxRepository
import com.nyasa.notifybridge.domain.repo.RecentNotificationsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrainOutboxUseCase @Inject constructor(
    private val outbox: OutboxRepository,
    private val mqtt: MqttClientManager,
    private val recent: RecentNotificationsRepository,
) {
    // Single-flight guard. Three triggers can fire `invoke` concurrently —
    // MqttForegroundService.onStartCommand (every enqueue signal),
    // DrainOnConnectUseCase (every (re)connection, automatic reconnects
    // included) and OutboxDrainWorker.doWork (periodic safety net). Without
    // serialization they `nextBatch` overlapping rows and double-publish before
    // `markPublished` deletes them, producing duplicate QoS1 deliveries. The
    // @Singleton scope ensures the same mutex is shared across all injectors.
    private val mutex = Mutex()

    suspend operator fun invoke(batch: Int = 50) = mutex.withLock {
        // Drain until empty or no progress. Terminates: it only repeats after
        // a full batch where every row was delivered, and each delivery
        // deletes its row (markPublished), so the PENDING set shrinks.
        do {
            val items = outbox.nextBatch(batch)
            // all{} walks FIFO and stops at the first row not delivered —
            // preserves delivery order. Skipping ahead would reorder
            // notifications; the rest are retried on the next drain trigger.
            val fullBatchDelivered =
                items.isNotEmpty() && items.all { publishOne(it) } && items.size == batch
        } while (fullBatchDelivered)
        runCatching {
            outbox.pruneExpired(
                nowMs = System.currentTimeMillis(),
                ttlMs = OUTBOX_TTL_MS,
                maxRows = OUTBOX_MAX_ROWS,
            )
        }
    }

    /** Publishes one row; true means delivered (and deleted from the outbox). */
    private suspend fun publishOne(item: OutboxItem): Boolean =
        when (mqtt.publish(item.topic, item.payload, qos = 1, retained = false)) {
            PublishOutcome.Success -> {
                // recordPublished is best-effort: a Room hiccup here must not
                // block the outbox from advancing. Failures are swallowed
                // here (kept pure-JVM in domain); the repo impl can log at
                // the data layer if/when a logger abstraction lands.
                runCatching { recent.recordPublished(item) }
                outbox.markPublished(item.id)
                true
            }
            // Broker unreachable — not this row's fault. Leave attemptCount
            // alone so an outage can't burn through the cap (remediation K).
            PublishOutcome.Transient -> false
            PublishOutcome.Rejected -> {
                // Spec §3.2: bump the attempt count and, once the row hits
                // MAX_PUBLISH_ATTEMPTS, transition it to FAILED_TERMINAL so the
                // next drain skips it (nextBatch is PENDING-only) instead of
                // re-failing on the same poison head forever.
                outbox.recordFailureOrFailTerminal(item.id, MAX_PUBLISH_ATTEMPTS)
                false
            }
        }

    companion object {
        /** Outbox rows older than this are pruned (7 days). */
        const val OUTBOX_TTL_MS = 7L * 24 * 60 * 60 * 1000

        /** Hard cap on retained outbox rows; oldest beyond this are trimmed. */
        const val OUTBOX_MAX_ROWS = 5_000
    }
}
