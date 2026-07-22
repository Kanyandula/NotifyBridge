package com.nyasa.notifybridge.screenshot

import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.TlsMode
import com.nyasa.notifybridge.ui.broker.BrokerContent
import org.junit.Test

/** Baselines for the broker config form — empty and filled+TLS+connected. */
class BrokerScreenScreenshotTest : ScreenshotTest() {

    private fun shoot(name: String, config: BrokerConfig, testResult: String?) {
        setScreen {
            BrokerContent(
                config = config,
                testResult = testResult,
                saving = false,
                onHostChange = {},
                onPortChange = {},
                onDeviceNameChange = {},
                onUsernameChange = {},
                onPasswordChange = {},
                onTlsModeChange = {},
                onPickCertFile = {},
                onTest = {},
                onSave = {},
                onBack = {},
                onNavStatus = {},
                onNavApps = {},
                onNavPermissions = {},
            )
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/BrokerScreen_$name.png")
    }

    @Test
    fun empty() = shoot("empty", BrokerConfig(), testResult = null)

    @Test
    fun filledTlsConnected() = shoot(
        "filled_tls_connected",
        BrokerConfig(host = "192.168.1.10", port = 1883, deviceName = "phone", tlsMode = TlsMode.SYSTEM_CA),
        testResult = "Connected",
    )
}
