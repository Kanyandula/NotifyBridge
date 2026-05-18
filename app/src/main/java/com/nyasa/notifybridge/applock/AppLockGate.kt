package com.nyasa.notifybridge.applock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun AppLockGate(
    manager: AppLockManager,
    locked: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val isLocked by manager.locked.collectAsState()
    if (isLocked) locked() else content()
}
