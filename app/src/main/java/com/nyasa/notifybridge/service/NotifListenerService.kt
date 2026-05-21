package com.nyasa.notifybridge.service

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
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
import javax.inject.Inject

class NotifPipeline(
    private val allowList: () -> Set<String>,
    private val selfPackage: String,
    private val dedup: NotificationDeduplicator,
    private val onAccepted: (CapturedNotification) -> Unit,
) {
    /**
     * Cheap field-only drops, callable before any expensive mapping. The
     * listener uses this to skip PackageManager IPC + mapper work for
     * notifications we're going to throw away anyway. [handle] re-applies
     * the same checks so [NotifPipeline] stays correct in isolation (tests
     * and any future direct callers).
     */
    fun shouldFilterEarly(
        packageName: String,
        category: String?,
        isGroupSummary: Boolean,
    ): Boolean {
        // Group summaries duplicate per-conversation posts. Transport
        // notifications are noisy, but an explicit allowlist choice should
        // win so media apps such as SiriusXM can be bridged deliberately.
        val allowedPackages = allowList()
        // Reasons stay structural — package names and titles never go to
        // logcat (would leak the user's app inventory and notification
        // content; conflicts with the "fully local, no telemetry" stance).
        val reason = when {
            packageName == selfPackage -> "self"
            isGroupSummary -> "group-summary"
            packageName !in allowedPackages ->
                if (category == TRANSPORT_CATEGORY) {
                    "transport-not-in-allowlist (allowSize=${allowedPackages.size})"
                } else {
                    "not-in-allowlist (allowSize=${allowedPackages.size})"
                }
            else -> null
        }
        if (reason != null) {
            log("filter DROP reason=$reason")
            return true
        }
        log("filter PASS")
        return false
    }

    fun handle(n: CapturedNotification, nowMs: Long) {
        if (shouldFilterEarly(n.packageName, n.category, n.isGroupSummary)) return
        if (!dedup.shouldForward(n, nowMs)) {
            log("dedup DROP")
            return
        }
        log("ACCEPT")
        onAccepted(n)
    }

    private fun log(message: String) {
        // Debug-level so adb logcat at default INFO threshold stays quiet and
        // the per-notification firehose doesn't pollute production logs.
        // runCatching guards against NoClassDefFoundError when the pipeline
        // runs under pure-JVM tests (no android.util.Log on the classpath).
        runCatching { Log.d(LOG_TAG, message) }
    }

    private companion object {
        // Notification.CATEGORY_TRANSPORT — duplicated as a literal to keep
        // NotifPipeline pure-JVM testable without the Android framework jar.
        const val TRANSPORT_CATEGORY = "transport"
        const val LOG_TAG = "NotifPipeline"
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
        // Cheap-drop first using fields pulled directly from sbn: skips the
        // PackageManager IPC and the full mapper for the high-rate noise
        // classes (transport / group-summary / non-allowlisted / self).
        val notif = sbn.notification
        val isGroupSummary = (notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        // Debug-only: cachedAllow.size keeps the allowlist out of logcat;
        // package name elided so a chatty device doesn't leak app inventory.
        Log.d(
            "NotifListenerService",
            "onNotificationPosted cat=${notif.category} " +
                "summary=$isGroupSummary cachedAllowSize=${cachedAllow.size}",
        )
        if (pipeline.shouldFilterEarly(sbn.packageName, notif.category, isGroupSummary)) return
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val captured = runCatching { mapper.map(sbn, label) }.getOrNull() ?: return
        runCatching { pipeline.handle(captured, System.currentTimeMillis()) }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
