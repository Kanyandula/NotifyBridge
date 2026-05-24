package com.nyasa.notifybridge.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.nyasa.notifybridge.domain.repo.LocalizationRepository

/**
 * Provides the active [Language] from [LocalizationRepository] to [content].
 *
 * Wrap once near the root of `setContent`. Re-collects when the repository's
 * `languageFlow` emits a new value (e.g. after an in-app language picker
 * selection or a system per-app locale change).
 */
@Composable
fun Localized(
    repository: LocalizationRepository,
    content: @Composable () -> Unit,
) {
    val language by repository.languageFlow.collectAsState()
    CompositionLocalProvider(LocalLanguage provides language) {
        content()
    }
}
