package com.nyasa.notifybridge.ui.onboarding

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nyasa.notifybridge.domain.repo.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class OnboardingStep { GRANT_ACCESS, CONNECT_BROKER, CHOOSE_APPS, DONE }

data class OnboardingUiState(val activeStep: OnboardingStep)

fun onboardingState(notifAccess: Boolean, brokerSet: Boolean, appsChosen: Boolean) =
    OnboardingUiState(when {
        !notifAccess -> OnboardingStep.GRANT_ACCESS
        !brokerSet -> OnboardingStep.CONNECT_BROKER
        !appsChosen -> OnboardingStep.CHOOSE_APPS
        else -> OnboardingStep.DONE
    })

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<OnboardingUiState> = combine(
        settings.brokerConfig,
        settings.allowList,
    ) { brokerConfig, allowList ->
        val notifAccess = NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
        onboardingState(
            notifAccess = notifAccess,
            brokerSet = brokerConfig.host.isNotBlank(),
            appsChosen = allowList.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = onboardingState(false, false, false),
    )
}
