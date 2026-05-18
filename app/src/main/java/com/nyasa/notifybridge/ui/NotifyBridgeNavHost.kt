package com.nyasa.notifybridge.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nyasa.notifybridge.ui.apps.AppsScreen
import com.nyasa.notifybridge.ui.broker.BrokerScreen
import com.nyasa.notifybridge.ui.onboarding.OnboardingScreen
import com.nyasa.notifybridge.ui.permissions.PermissionsScreen
import com.nyasa.notifybridge.ui.status.StatusScreen

@Composable
fun NotifyBridgeNavHost(startOnboarding: Boolean) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = if (startOnboarding) "onboarding" else "status") {
        composable("onboarding") { OnboardingScreen(nav) }
        composable("status") { StatusScreen(nav) }
        composable("broker") { BrokerScreen(nav) }
        composable("permissions") { PermissionsScreen(nav) }
        composable("apps") { AppsScreen(nav) }
    }
}
