package com.nyasa.notifybridge.domain.notif

import com.nyasa.notifybridge.domain.model.CapturedNotification

class NotificationDeduplicator(private val debounceMs: Long = 500L) {
    private data class Seen(val contentHash: Int, val atMs: Long)
    private val last = HashMap<String, Seen>()

    fun shouldForward(n: CapturedNotification, nowMs: Long): Boolean {
        val hash = (n.title to n.body).hashCode()
        val prev = last[n.dedupeKey]
        val contentUnchanged = prev != null && prev.contentHash == hash
        if (contentUnchanged && (n.isOngoing || n.category == TRANSPORT_CATEGORY)) return false
        if (contentUnchanged && nowMs - prev!!.atMs < debounceMs) return false
        last[n.dedupeKey] = Seen(hash, nowMs)
        return true
    }

    private companion object {
        const val TRANSPORT_CATEGORY = "transport"
    }
}
