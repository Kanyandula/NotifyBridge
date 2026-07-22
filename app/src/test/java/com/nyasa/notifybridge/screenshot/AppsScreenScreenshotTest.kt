package com.nyasa.notifybridge.screenshot

import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.nyasa.notifybridge.ui.apps.AppRow
import com.nyasa.notifybridge.ui.apps.AppsContent
import org.junit.Test

/** Baselines for the app allow-list — populated and empty (mirrors @Preview). */
class AppsScreenScreenshotTest : ScreenshotTest() {

    private val rows = listOf(
        AppRow("Signal", "org.thoughtcrime.securesms", true),
        AppRow("Gmail", "com.google.android.gm", false),
        AppRow("Slack", "com.Slack", true),
        AppRow("WhatsApp", "com.whatsapp", false),
    )

    private fun shoot(name: String, rows: List<AppRow>) {
        setScreen {
            AppsContent(
                rows = rows,
                query = "",
                icons = emptyMap(),
                onQueryChange = {},
                onToggle = { _, _ -> },
                onNavStatus = {},
                onNavBroker = {},
                onNavPermissions = {},
            )
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/AppsScreen_$name.png")
    }

    @Test
    fun populated() = shoot("populated", rows)

    @Test
    fun empty() = shoot("empty", emptyList())
}
