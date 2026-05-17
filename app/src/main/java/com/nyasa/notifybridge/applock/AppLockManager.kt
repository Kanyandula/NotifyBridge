package com.nyasa.notifybridge.applock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AppLockManager(
    private val enabled: () -> Boolean,
    private val idleMs: () -> Long,
) {
    private val _locked = MutableStateFlow(enabled())
    val locked: StateFlow<Boolean> = _locked
    private var backgroundedAt: Long? = null

    fun onAuthenticated() { _locked.value = false }
    fun onBackgrounded(atMs: Long) { backgroundedAt = atMs }
    fun onForegrounded(atMs: Long) {
        if (!enabled()) { _locked.value = false; return }
        val bg = backgroundedAt ?: return
        if (atMs - bg >= idleMs()) _locked.value = true
    }
}
