package com.nyasa.notifybridge.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme

@Composable
fun OnboardingScreen(nav: NavHostController) {
    val vm: OnboardingViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.activeStep) {
        if (state.activeStep == OnboardingStep.DONE) {
            nav.navigate("status") {
                popUpTo("onboarding") { inclusive = true }
            }
        }
    }

    OnboardingContent(
        state = state,
        onGrantAccess = {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        },
        onConfigureBroker = { nav.navigate("broker") },
        onChooseApps = { nav.navigate("apps") },
    )
}

@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    onGrantAccess: () -> Unit,
    onConfigureBroker: () -> Unit,
    onChooseApps: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "NotifyBridge",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status chip
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = "SETUP IN PROGRESS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Heading
        Text(
            text = "Guided Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Notifications → MQTT, local only",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Step 1 — Grant notification access
        val step1Active = state.activeStep == OnboardingStep.GRANT_ACCESS
        StepCard(
            number = 1,
            title = "Grant notification access",
            description = "Required to read notifications. This opens the Android system settings menu.",
            buttonLabel = "Grant",
            buttonIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
            isActive = step1Active,
            isEnabled = step1Active,
            onClick = onGrantAccess,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 2 — Connect broker
        val step2Active = state.activeStep == OnboardingStep.CONNECT_BROKER
        StepCard(
            number = 2,
            title = "Connect your broker",
            description = "Point NotifyBridge at your Mosquitto / Home Assistant MQTT broker.",
            buttonLabel = "Configure",
            buttonIcon = null,
            isActive = step2Active,
            isEnabled = step2Active,
            onClick = onConfigureBroker,
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Step 3 — Choose apps
        val step3Active = state.activeStep == OnboardingStep.CHOOSE_APPS
        StepCard(
            number = 3,
            title = "Choose which apps to forward",
            description = "Nothing is forwarded until you pick apps. Your choice, your privacy.",
            buttonLabel = "Choose apps",
            buttonIcon = null,
            isActive = step3Active,
            isEnabled = step3Active,
            onClick = onChooseApps,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Footer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "PRIVACY POLICY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Everything stays on your network. No cloud, no Google, no Firebase.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    description: String,
    buttonLabel: String,
    buttonIcon: (@Composable () -> Unit)?,
    isActive: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val cardAlpha = if (isActive) 1f else 0.4f
    val badgeColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Numbered badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = number.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onClick,
                enabled = isEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                ),
            ) {
                Text(text = buttonLabel)
                if (buttonIcon != null) {
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    buttonIcon()
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Onboarding · Step 1")
@Composable
private fun OnboardingStep1Preview() {
    NotifyBridgeTheme {
        OnboardingContent(
            state = OnboardingUiState(OnboardingStep.GRANT_ACCESS),
            onGrantAccess = {}, onConfigureBroker = {}, onChooseApps = {},
        )
    }
}

@Preview(showBackground = true, name = "Onboarding · Step 2")
@Composable
private fun OnboardingStep2Preview() {
    NotifyBridgeTheme {
        OnboardingContent(
            state = OnboardingUiState(OnboardingStep.CONNECT_BROKER),
            onGrantAccess = {}, onConfigureBroker = {}, onChooseApps = {},
        )
    }
}

@Preview(showBackground = true, name = "Onboarding · Step 3")
@Composable
private fun OnboardingStep3Preview() {
    NotifyBridgeTheme {
        OnboardingContent(
            state = OnboardingUiState(OnboardingStep.CHOOSE_APPS),
            onGrantAccess = {}, onConfigureBroker = {}, onChooseApps = {},
        )
    }
}
