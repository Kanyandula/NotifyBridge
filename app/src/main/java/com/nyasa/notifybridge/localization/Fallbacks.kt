package com.nyasa.notifybridge.localization

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Defense-in-depth fallback strings for the runtime resolver.
 *
 * Build-time parity gates already catch the structural cases; these helpers
 * exist so a slip-up at runtime (e.g. non-Compose code passing a stale key, or
 * a future remote-loaded JSON) degrades gracefully rather than crashing.
 *
 * Policy (from the plan):
 * - locale file missing → fall back to EN
 * - key missing in active locale → fall back to EN value
 * - key missing in EN too → return the literal raw key
 * - placeholder absent from args → substitute the placeholder name verbatim
 * - malformed ICU plural → return the `other` branch; warn once
 */
internal object Fallbacks {

    private const val TAG = "Localization"

    // Shared across UI and background threads (e.g. MqttForegroundService resolving
    // notification-channel strings off the main thread), so the set must be concurrent.
    private val warned: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun warnOnce(message: String) {
        if (warned.add(message)) Log.w(TAG, message)
    }

    fun keyNotFound(dictionary: String, key: String): String {
        warnOnce("Missing key `$dictionary.$key` in active locale and EN — returning raw key")
        return "$dictionary.$key"
    }

    fun localeNotFound(tag: String) {
        warnOnce("Locale `$tag` JSON not bundled — falling back to EN")
    }

    fun placeholderMissing(name: String): String {
        warnOnce("Placeholder `{$name}` not supplied at runtime — emitting name verbatim")
        return "{$name}"
    }
}
