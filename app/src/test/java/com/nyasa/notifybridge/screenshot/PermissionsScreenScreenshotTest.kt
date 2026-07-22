package com.nyasa.notifybridge.screenshot

import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.nyasa.notifybridge.domain.model.AppLockPrefs
import com.nyasa.notifybridge.ui.permissions.PermissionsContent
import org.junit.Test

/**
 * Permissions is the most settings-dense screen, so it also carries the
 * cross-cutting variants: a large-font check (text overflow) and a French
 * check (localisation / long-text truncation).
 */
class PermissionsScreenScreenshotTest : ScreenshotTest() {

    // `granted` toggles the whole permission state, matching the two @Preview
    // states (all-granted vs action-needed) — keeps the helper to a few params.
    private fun shoot(name: String, granted: Boolean, languageTag: String = "en", fontScale: Float = 1f) {
        val appLock = if (granted) {
            AppLockPrefs(enabled = true, idleTimeoutMs = 60_000L, redactBody = true)
        } else {
            AppLockPrefs(enabled = false)
        }
        setScreen(languageTag = languageTag, fontScale = fontScale) {
            PermissionsContent(
                notifGranted = granted,
                batteryExempt = granted,
                appLock = appLock,
                onOpenNotifSettings = {},
                onRequestBatteryExemption = {},
                onLockEnabledChange = {},
                onIdleTimeoutChange = {},
                onRedactBodyChange = {},
                onNavStatus = {},
                onNavApps = {},
                onNavBroker = {},
                onNavLanguage = {},
            )
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/PermissionsScreen_$name.png")
    }

    @Test
    fun allGranted() = shoot("all_granted", granted = true)

    @Test
    fun actionNeeded() = shoot("action_needed", granted = false)

    @Test
    fun allGrantedLargeFont() = shoot("all_granted_largeFont", granted = true, fontScale = 1.5f)

    @Test
    fun allGrantedFrench() = shoot("all_granted_fr", granted = true, languageTag = "fr")
}
