package com.nyasa.notifybridge.ui.language

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.nyasa.notifybridge.domain.repo.LocalizationRepository
import com.nyasa.notifybridge.localization.Dictionary
import com.nyasa.notifybridge.localization.back
import com.nyasa.notifybridge.localization.localized
import com.nyasa.notifybridge.localization.subtitle
import com.nyasa.notifybridge.localization.systemDefault
import com.nyasa.notifybridge.localization.title
import com.nyasa.notifybridge.ui.theme.NotifyBridgeTheme
import com.nyasa.notifybridge.ui.theme.Teal
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

@Composable
fun LanguageSettingsScreen(nav: NavHostController) {
    val vm: LanguageSettingsViewModel = hiltViewModel()
    val currentTag by vm.selectedLanguageTag.collectAsState()

    LanguageSettingsContent(
        currentTag = currentTag,
        onPick = vm::selectLanguage,
        onBack = { nav.popBackStack() },
    )
}

private data class LanguageOption(
    val tag: String?,
    val nativeName: String,
)

@Composable
private fun LanguageSettingsContent(
    currentTag: String?,
    onPick: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val options = listOf(
        LanguageOption(tag = null, nativeName = Dictionary.Language.systemDefault.localized()),
        LanguageOption(tag = "en", nativeName = "English"),
        LanguageOption(tag = "fr", nativeName = "Français"),
        LanguageOption(tag = "es", nativeName = "Español"),
        LanguageOption(tag = "pt", nativeName = "Português"),
    )

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
                    text = Dictionary.Language.title.localized(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = Dictionary.Language.subtitle.localized(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            options.forEach { option ->
                LanguageRow(
                    option = option,
                    isSelected = option.tag == currentTag,
                    onClick = { onPick(option.tag) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(
    option: LanguageOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Teal.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = option.nativeName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (option.tag != null) {
                    Text(
                        text = option.tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Teal,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Language · System default")
@Composable
private fun LanguageDefaultPreview() {
    NotifyBridgeTheme {
        LanguageSettingsContent(currentTag = null, onPick = {}, onBack = {})
    }
}

@Preview(showBackground = true, name = "Language · French selected")
@Composable
private fun LanguageFrenchPreview() {
    NotifyBridgeTheme {
        LanguageSettingsContent(currentTag = "fr", onPick = {}, onBack = {})
    }
}
