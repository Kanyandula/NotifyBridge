package com.nyasa.notifybridge.applock

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLockManagerTest {
    @Test fun starts_locked_when_enabled() = runTest {
        val m = AppLockManager(enabled = { true }, idleMs = { 1000 })
        m.locked.test { assertEquals(true, awaitItem()) }
    }
    @Test fun unlock_then_relock_after_timeout() = runTest {
        val m = AppLockManager(enabled = { true }, idleMs = { 1000 })
        m.onAuthenticated()
        m.locked.test { assertEquals(false, awaitItem()) }
        m.onBackgrounded(atMs = 0)
        m.onForegrounded(atMs = 1500)            // exceeded idle window
        m.locked.test { assertEquals(true, awaitItem()) }
    }
    @Test fun within_timeout_stays_unlocked() = runTest {
        val m = AppLockManager(enabled = { true }, idleMs = { 1000 })
        m.onAuthenticated()
        m.onBackgrounded(atMs = 0)
        m.onForegrounded(atMs = 500)
        m.locked.test { assertEquals(false, awaitItem()) }
    }
    @Test fun disabled_never_locks() = runTest {
        val m = AppLockManager(enabled = { false }, idleMs = { 1 })
        m.locked.test { assertEquals(false, awaitItem()) }
    }
}
