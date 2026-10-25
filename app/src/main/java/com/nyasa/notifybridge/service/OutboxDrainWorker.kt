package com.nyasa.notifybridge.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.mqtt.awaitConnected
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.domain.usecase.DrainOutboxUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class OutboxDrainWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val mqtt: MqttClientManager,
    private val drain: DrainOutboxUseCase,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val cfg = settings.brokerConfig.first()
        if (cfg.host.isBlank()) return Result.success()
        // Never connect(): that would tear down a live client or reset one
        // that's mid-way through HiveMQ's reconnect backoff.
        mqtt.ensureConnected(cfg)
        mqtt.awaitConnected() // no DrainOnConnect collector if the service is dead
        drain()
        return Result.success()
    }

    companion object {
        /**
         * Periodic safety net behind the reconnect and enqueue drains. Called
         * from boot and from the foreground service, so a fresh install gets
         * it without a reboot. UPDATE (not KEEP) so existing installs pick up
         * the network constraint; it doesn't interrupt a running worker.
         */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "outbox-drain",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<OutboxDrainWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                    .build(),
            )
        }
    }
}
