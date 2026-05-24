package com.nyasa.notifybridge.ui.locked

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nyasa.notifybridge.localization.Dictionary
import com.nyasa.notifybridge.localization.appName
import com.nyasa.notifybridge.localization.button
import com.nyasa.notifybridge.localization.localized
import com.nyasa.notifybridge.localization.subtitle
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme

@Composable
fun LockedScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Dictionary.Common.appName.localized())
        Spacer(Modifier.height(8.dp))
        Text(Dictionary.Locked.subtitle.localized())
        Spacer(Modifier.height(24.dp))
        Button(onClick = onUnlock) { Text(Dictionary.Locked.button.localized()) }
    }
}

@Preview(showBackground = true, name = "Locked · Unlock prompt")
@Composable
private fun LockedScreenPreview() {
    NotifyBridgeTheme {
        LockedScreen(onUnlock = {})
    }
}
