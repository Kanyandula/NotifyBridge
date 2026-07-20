package com.nyasa.notifybridge.screenshot

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.nyasa.notifybridge.localization.AssetJsonLanguage
import com.nyasa.notifybridge.localization.LocalLanguage
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.TimeZone

/**
 * Base for Roborazzi (JVM/Robolectric) screenshot tests.
 *
 * Determinism is pinned here, not per-test:
 *  - locale + timezone are fixed so number/time formatting is stable
 *    (StatusScreen formats via the default Locale/TimeZone);
 *  - a real English [AssetJsonLanguage] is provided so composables render
 *    actual copy — the default [LocalLanguage] is `KeyEchoLanguage`, which
 *    would render dictionary keys instead of text.
 *
 * App icons load via PackageManager, which resolves to null under Robolectric
 * (no packages installed), so the icon fallback renders deterministically —
 * no injection needed.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5)
abstract class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun pinEnvironment() {
        Locale.setDefault(Locale.US)
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Dublin"))
    }

    protected fun setScreen(body: @Composable () -> Unit) {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val english = AssetJsonLanguage(tag = "en", context = ctx)
        compose.setContent {
            CompositionLocalProvider(LocalLanguage provides english) {
                NotifyBridgeTheme { body() }
            }
        }
    }
}
