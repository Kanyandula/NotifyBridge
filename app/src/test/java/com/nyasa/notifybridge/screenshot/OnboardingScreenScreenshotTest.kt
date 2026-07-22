package com.nyasa.notifybridge.screenshot

import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.nyasa.notifybridge.ui.onboarding.OnboardingContent
import com.nyasa.notifybridge.ui.onboarding.OnboardingStep
import com.nyasa.notifybridge.ui.onboarding.OnboardingUiState
import org.junit.Test

/** Baselines for each onboarding step (mirrors the existing @Preview steps). */
class OnboardingScreenScreenshotTest : ScreenshotTest() {

    private fun shoot(name: String, step: OnboardingStep) {
        setScreen {
            OnboardingContent(
                state = OnboardingUiState(step),
                onGrantAccess = {},
                onConfigureBroker = {},
                onChooseApps = {},
            )
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/OnboardingScreen_$name.png")
    }

    @Test
    fun grantAccess() = shoot("grant_access", OnboardingStep.GRANT_ACCESS)

    @Test
    fun connectBroker() = shoot("connect_broker", OnboardingStep.CONNECT_BROKER)

    @Test
    fun chooseApps() = shoot("choose_apps", OnboardingStep.CHOOSE_APPS)
}
