package com.nyasa.notifybridge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val Scheme = darkColorScheme(
    primary = Teal, background = BgBase, surface = Surface, error = ErrorRed)

@Composable
fun NotifyBridgeTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = Scheme, content = content)
