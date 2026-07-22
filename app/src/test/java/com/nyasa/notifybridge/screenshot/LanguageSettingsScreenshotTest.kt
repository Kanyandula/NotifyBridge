package com.nyasa.notifybridge.screenshot

import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.nyasa.notifybridge.ui.language.LanguageSettingsContent
import org.junit.Test

/** Baselines for the language picker — system default and a selected locale. */
class LanguageSettingsScreenshotTest : ScreenshotTest() {

    private fun shoot(name: String, currentTag: String?) {
        setScreen {
            LanguageSettingsContent(currentTag = currentTag, onPick = {}, onBack = {})
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/LanguageSettings_$name.png")
    }

    @Test
    fun systemDefault() = shoot("system_default", currentTag = null)

    @Test
    fun frenchSelected() = shoot("french_selected", currentTag = "fr")
}
