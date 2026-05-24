package com.nyasa.notifybridge.localization

import androidx.compose.runtime.Composable

/**
 * Resolves a [StringContent] to a display string using the active language.
 *
 * Equivalent to `text.resolve(LocalLanguage.current)`. Prefer the extension form
 * [localized] at call sites — it reads more naturally inside `Text(...)`.
 */
@Composable
fun localizeString(text: StringContent): String = text.resolve(LocalLanguage.current)

/** Ergonomic extension: `Dictionary.Status.title.localized()` instead of `localizeString(Dictionary.Status.title)`. */
@Composable
fun StringContent.localized(): String = localizeString(this)
