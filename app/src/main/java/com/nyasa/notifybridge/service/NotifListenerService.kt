package com.nyasa.notifybridge.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nyasa.notifybridge.domain.model.CapturedNotification
import com.nyasa.notifybridge.domain.notif.NotificationDeduplicator
import com.nyasa.notifybridge.domain.notif.NotificationMapper
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import com.nyasa.notifybridge.domain.usecase.EnqueueNotificationUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class NotifPipeline(
    private val allowList: () -> Set<String>,
    private val selfPackage: String,
    private val dedup: NotificationDeduplicator,
    private val onAccepted: (CapturedNotification) -> Unit,
) {
    fun handle(n: CapturedNotification, nowMs: Long) {
        if (n.packageName == selfPackage) return
        if (n.packageName !in allowList()) return
        if (!dedup.shouldForward(n, nowMs)) return
        onAccepted(n)
    }
}

@AndroidEntryPoint
class NotifListenerService : NotificationListenerService() {
    @Inject lateinit var mapper: NotificationMapper
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var enqueue: EnqueueNotificationUseCase
    private val dedup = NotificationDeduplicator()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var pipeline: NotifPipeline
    @Volatile private var cachedAllow: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        scope.launch { settings.allowList.collect { cachedAllow = it } }
        pipeline = NotifPipeline(
            allowList = { cachedAllow },
            selfPackage = packageName,
            dedup = dedup,
            onAccepted = { n ->
                scope.launch {
                    val device = settings.brokerConfig.first().deviceName
                    enqueue(n, device)
                    startService(Intent(this@NotifListenerService,
                        MqttForegroundService::class.java))
                }
            })
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val captured = runCatching { mapper.map(sbn, label) }.getOrNull() ?: return
        runCatching { pipeline.handle(captured, System.currentTimeMillis()) }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
