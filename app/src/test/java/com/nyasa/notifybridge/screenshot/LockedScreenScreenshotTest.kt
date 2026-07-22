package com.nyasa.notifybridge.screenshot

import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.nyasa.notifybridge.ui.locked.LockedScreen
import org.junit.Test

/** Baseline for the app-lock unlock prompt. */
class LockedScreenScreenshotTest : ScreenshotTest() {

    @Test
    fun unlockPrompt() {
        setScreen { LockedScreen(onUnlock = {}) }
        compose.onRoot().captureRoboImage("src/test/screenshots/LockedScreen_prompt.png")
    }
}
