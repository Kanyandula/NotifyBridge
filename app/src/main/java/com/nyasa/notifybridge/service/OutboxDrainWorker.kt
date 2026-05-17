package com.nyasa.notifybridge.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.domain.usecase.DrainOutboxUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

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
        mqtt.connect(cfg)
        drain()
        return Result.success()
    }
}
