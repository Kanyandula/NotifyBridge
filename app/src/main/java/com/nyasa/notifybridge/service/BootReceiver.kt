package com.nyasa.notifybridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "outbox-drain",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<OutboxDrainWorker>(15, TimeUnit.MINUTES).build())
        // BOOT_COMPLETED receivers are exempt from the Android 12+ (API 31+)
        // background-start restrictions, so startForegroundService is allowed
        // here. Do not "simplify" this away. The service calls
        // startForeground() in onCreate within the required window.
        context.startForegroundService(
            Intent(context, MqttForegroundService::class.java))
    }
}
