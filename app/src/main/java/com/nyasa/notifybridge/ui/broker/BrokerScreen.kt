package com.nyasa.notifybridge.ui.broker

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.nyasa.notifybridge.domain.model.BrokerConfig
import com.nyasa.notifybridge.domain.model.TlsMode
import com.nyasa.notifybridge.localization.Dictionary
import com.nyasa.notifybridge.localization.back
import com.nyasa.notifybridge.localization.certificateHeading
import com.nyasa.notifybridge.localization.comingSoon
import com.nyasa.notifybridge.localization.fieldDevice
import com.nyasa.notifybridge.localization.fieldDevicePlaceholder
import com.nyasa.notifybridge.localization.fieldDeviceSupporting
import com.nyasa.notifybridge.localization.fieldHost
import com.nyasa.notifybridge.localization.fieldHostPlaceholder
import com.nyasa.notifybridge.localization.fieldPassword
import com.nyasa.notifybridge.localization.fieldPort
import com.nyasa.notifybridge.localization.fieldPortPlaceholder
import com.nyasa.notifybridge.localization.fieldUsername
import com.nyasa.notifybridge.localization.localized
import com.nyasa.notifybridge.localization.navAccess
import com.nyasa.notifybridge.localization.navApps
import com.nyasa.notifybridge.localization.navBroker
import com.nyasa.notifybridge.localization.navStatus
import com.nyasa.notifybridge.localization.passwordHide
import com.nyasa.notifybridge.localization.passwordShow
import com.nyasa.notifybridge.localization.pinnedCertNote
import com.nyasa.notifybridge.localization.saveButton
import com.nyasa.notifybridge.localization.saveButtonSaving
import com.nyasa.notifybridge.localization.sectionAuthentication
import com.nyasa.notifybridge.localization.sectionConnection
import com.nyasa.notifybridge.localization.sectionTls
import com.nyasa.notifybridge.localization.selectCertFile
import com.nyasa.notifybridge.localization.testButton
import com.nyasa.notifybridge.localization.testResultConnected
import com.nyasa.notifybridge.localization.tlsPinnedButton
import com.nyasa.notifybridge.localization.tlsSystemCaButton
import com.nyasa.notifybridge.localization.title
import com.nyasa.notifybridge.localization.useTls
import com.nyasa.notifybridge.service.MqttForegroundService
import com.nyasa.notifybridge.ui.theme.Amber
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
import com.nyasa.notifybridge.ui.theme.Teal

@Composable
fun BrokerScreen(nav: NavHostController) {
    val vm: BrokerViewModel = hiltViewModel()
    val config by vm.config.collectAsState()
    val testResult by vm.testResult.collectAsState()
    val saving by vm.saving.collectAsState()
    val saveSuccess by vm.saveSuccess.collectAsState()

    val context = LocalContext.current
    val view = LocalView.current

    // FLAG_SECURE: prevent screen capture
    DisposableEffect(Unit) {
        val activity = view.context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            context.startForegroundService(
                Intent(context, MqttForegroundService::class.java)
            )
            vm.consumeSaveSuccess()
            nav.popBackStack()
        }
    }

    // ── File picker (for pinnedCertPem — present but effectively unreachable
    //    while PINNED is disabled; code kept for Task 25 activation) ──────────
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val pem = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readText()
            vm.updatePinnedCertPem(pem)
        }
    }

    BrokerContent(
        config = config,
        testResult = testResult,
        saving = saving,
        onHostChange = vm::updateHost,
        onPortChange = vm::updatePort,
        onDeviceNameChange = vm::updateDeviceName,
        onUsernameChange = vm::updateUsername,
        onPasswordChange = vm::updatePassword,
        onTlsModeChange = vm::updateTlsMode,
        onPickCertFile = {
            filePicker.launch(arrayOf("application/x-pem-file", "text/plain"))
        },
        onTest = vm::test,
        onSave = vm::save,
        onBack = { nav.popBackStack() },
        onNavStatus = { nav.navigate("status") },
        onNavApps = { nav.navigate("apps") },
        onNavPermissions = { nav.navigate("permissions") },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrokerContent(
    config: BrokerConfig,
    testResult: String?,
    saving: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTlsModeChange: (TlsMode) -> Unit,
    onPickCertFile: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onNavStatus: () -> Unit,
    onNavApps: () -> Unit,
    onNavPermissions: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val tlsEnabled = config.tlsMode != TlsMode.OFF

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = Dictionary.Common.back.localized(),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = Dictionary.Broker.title.localized(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.Router,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp),
                )
            }
        },
        bottomBar = {
            Column {
                if (testResult != null) {
                    val isConnected = testResult == "Connected"
                    val resultColor = if (isConnected) Teal else MaterialTheme.colorScheme.error
                    val resultDisplay = if (isConnected)
                        Dictionary.Broker.testResultConnected.localized()
                    else
                        testResult
                    Text(
                        text = resultDisplay,
                        color = resultColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onTest,
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(Dictionary.Broker.testButton.localized())
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = isValid(config) && !saving,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            if (saving) Dictionary.Broker.saveButtonSaving.localized()
                            else Dictionary.Broker.saveButton.localized()
                        )
                    }
                }

                BrokerBottomNav(
                    onStatus = onNavStatus,
                    onApps = onNavApps,
                    onBroker = { /* already here */ },
                    onAccess = onNavPermissions,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            FormSection(title = Dictionary.Broker.sectionConnection.localized()) {
                MonoTextField(
                    label = Dictionary.Broker.fieldHost.localized(),
                    value = config.host,
                    onValueChange = onHostChange,
                    placeholder = Dictionary.Broker.fieldHostPlaceholder.localized(),
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(8.dp))
                MonoTextField(
                    label = Dictionary.Broker.fieldPort.localized(),
                    value = if (config.port == 0) "" else config.port.toString(),
                    onValueChange = onPortChange,
                    placeholder = Dictionary.Broker.fieldPortPlaceholder.localized(),
                    keyboardType = KeyboardType.Number,
                )
                Spacer(Modifier.height(8.dp))
                MonoTextField(
                    label = Dictionary.Broker.fieldDevice.localized(),
                    value = config.deviceName,
                    onValueChange = onDeviceNameChange,
                    placeholder = Dictionary.Broker.fieldDevicePlaceholder.localized(),
                    supportingText = Dictionary.Broker.fieldDeviceSupporting.localized(),
                )
            }

            FormSection(title = Dictionary.Broker.sectionAuthentication.localized()) {
                MonoTextField(
                    label = Dictionary.Broker.fieldUsername.localized(),
                    value = config.username ?: "",
                    onValueChange = onUsernameChange,
                    leadingIcon = {
                        Icon(Icons.Filled.Lock, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp))
                    },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = config.password ?: "",
                    onValueChange = onPasswordChange,
                    label = { Text(Dictionary.Broker.fieldPassword.localized(), fontFamily = FontFamily.Monospace) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible)
                                    Icons.Filled.Visibility
                                else
                                    Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible)
                                    Dictionary.Broker.passwordHide.localized()
                                else
                                    Dictionary.Broker.passwordShow.localized(),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Lock, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            FormSection(title = Dictionary.Broker.sectionTls.localized()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Dictionary.Broker.useTls.localized(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = tlsEnabled,
                        onCheckedChange = { on ->
                            onTlsModeChange(if (on) TlsMode.SYSTEM_CA else TlsMode.OFF)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Teal,
                            checkedTrackColor = Teal.copy(alpha = 0.4f),
                        ),
                    )
                }

                if (tlsEnabled) {
                    Spacer(Modifier.height(12.dp))

                    // Segmented control: System CA | Pinned (disabled, Coming soon)
                    Text(
                        text = Dictionary.Broker.certificateHeading.localized(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // System CA — selectable
                        val systemCaSelected = config.tlsMode == TlsMode.SYSTEM_CA
                        Button(
                            onClick = { onTlsModeChange(TlsMode.SYSTEM_CA) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (systemCaSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surface,
                                contentColor = if (systemCaSelected)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                Dictionary.Broker.tlsSystemCaButton.localized(),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }

                        // Pinned — disabled, Coming soon
                        Box(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { /* intentionally no-op: PINNED disabled until Task 25 */ },
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor =
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    disabledContentColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                ),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        Dictionary.Broker.tlsPinnedButton.localized(),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                    Text(
                                        Dictionary.Broker.comingSoon.localized(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }

                    // certError inline — present for PINNED path (currently unreachable via UI)
                    val cerr = certError(config.tlsMode, config.pinnedCertPem)
                    if (cerr != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = cerr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        // File picker button — only shown when PINNED active (unreachable now)
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onPickCertFile) {
                            Text(Dictionary.Broker.selectCertFile.localized())
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Amber TLS caution note
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Amber.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = Amber,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = Dictionary.Broker.pinnedCertNote.localized(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Amber,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MonoTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = FontFamily.Monospace) },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)) }
        } else null,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = if (supportingText != null) {
            {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        } else null,
        leadingIcon = leadingIcon,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun BrokerBottomNav(
    onStatus: () -> Unit,
    onApps: () -> Unit,
    onBroker: () -> Unit,
    onAccess: () -> Unit,
) {
    val statusLabel = Dictionary.Common.navStatus.localized()
    val appsLabel = Dictionary.Common.navApps.localized()
    val brokerLabel = Dictionary.Common.navBroker.localized()
    val accessLabel = Dictionary.Common.navAccess.localized()
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = false,
            onClick = onStatus,
            icon = { Icon(Icons.Filled.Dashboard, contentDescription = statusLabel) },
            label = { Text(statusLabel) },
            colors = navItemColors(),
        )
        NavigationBarItem(
            selected = false,
            onClick = onApps,
            icon = { Icon(Icons.Filled.Apps, contentDescription = appsLabel) },
            label = { Text(appsLabel) },
            colors = navItemColors(),
        )
        NavigationBarItem(
            selected = true,
            onClick = onBroker,
            icon = { Icon(Icons.Filled.Router, contentDescription = brokerLabel) },
            label = { Text(brokerLabel) },
            colors = navItemColors(),
        )
        NavigationBarItem(
            selected = false,
            onClick = onAccess,
            icon = { Icon(Icons.Filled.Security, contentDescription = accessLabel) },
            label = { Text(accessLabel) },
            colors = navItemColors(),
        )
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Teal,
    selectedTextColor = Teal,
    indicatorColor = Teal.copy(alpha = 0.15f),
    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
)

@Preview(showBackground = true, name = "Broker · Empty")
@Composable
private fun BrokerEmptyPreview() {
    NotifyBridgeTheme {
        BrokerContent(
            config = BrokerConfig(),
            testResult = null,
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
}

@Preview(showBackground = true, name = "Broker · Filled + TLS + connected")
@Composable
private fun BrokerFilledPreview() {
    NotifyBridgeTheme {
        BrokerContent(
            config = BrokerConfig(
                host = "192.168.1.10",
                port = 1883,
                deviceName = "phone",
                tlsMode = TlsMode.SYSTEM_CA,
            ),
            testResult = "Connected",
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
}
