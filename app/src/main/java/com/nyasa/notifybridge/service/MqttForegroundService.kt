package com.nyasa.notifybridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.nyasa.notifybridge.BuildConfig
import com.nyasa.notifybridge.domain.mqtt.MqttClientManager
import com.nyasa.notifybridge.domain.repo.LocalizationRepository
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.domain.usecase.DrainOutboxUseCase
import com.nyasa.notifybridge.localization.Dictionary
import com.nyasa.notifybridge.localization.LocalizedString
import com.nyasa.notifybridge.localization.foregroundTitle
import com.nyasa.notifybridge.localization.notificationChannelName
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MqttForegroundService : Service() {
    @Inject lateinit var mqtt: MqttClientManager
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var localization: LocalizationRepository
    @Inject lateinit var drain: DrainOutboxUseCase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        scope.launch {
            val cfg = settings.brokerConfig.first()
            if (cfg.host.isNotBlank()) {
                mqtt.connect(cfg)
                drain()
            }
        }
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        scope.launch { drain() }
        // Debug: NOT_STICKY so a system kill cannot resurrect this
        // @AndroidEntryPoint service into the instrumented-test process.
        // Trade-off: no auto-restart in dev — acceptable, we redeploy.
        // Release: STICKY so a real-world kill restarts the bridge.
        // Full rationale in app/src/debug/AndroidManifest.xml.
        return if (BuildConfig.DEBUG) START_NOT_STICKY else START_STICKY
    }

    private fun startAsForeground() {
        val ch = "bridge"
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                ch,
                resolve(Dictionary.Services.notificationChannelName),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val n: Notification = Notification.Builder(this, ch)
            .setContentTitle(resolve(Dictionary.Services.foregroundTitle))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else startForeground(1, n)
    }

    private fun resolve(s: LocalizedString): String = s.resolve(localization.languageFlow.value)

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}
