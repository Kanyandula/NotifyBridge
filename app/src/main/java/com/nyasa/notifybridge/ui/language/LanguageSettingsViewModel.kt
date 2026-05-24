package com.nyasa.notifybridge.ui.language

import androidx.lifecycle.ViewModel
import com.nyasa.notifybridge.domain.repo.LocalizationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class LanguageSettingsViewModel @Inject constructor(
    private val localization: LocalizationRepository,
) : ViewModel() {
    val selectedLanguageTag: StateFlow<String?> = localization.selectedLanguageTag

    fun selectLanguage(tag: String?) {
        localization.setApplicationLanguage(tag)
    }
}
