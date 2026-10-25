package com.nyasa.notifybridge.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        OutboxDrainWorker.schedule(context)
        // BOOT_COMPLETED receivers are exempt from the Android 12+ (API 31+)
        // background-start restrictions, so startForegroundService is allowed
        // here. Do not "simplify" this away. The service calls
        // startForeground() in onCreate within the required window.
        context.startForegroundService(
            Intent(context, MqttForegroundService::class.java))
    }
}
