package com.mitalipurohit.blinkwell.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitalipurohit.blinkwell.data.remote.SupabaseClientProvider
import com.mitalipurohit.blinkwell.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val currentStep: Int = 0,
    val monitorConsent: Boolean = true,
    val researchConsent: Boolean = true,
    val cohortArm: String = "software_engineer", // "software_engineer" | "student" | "general"
    val selectedMode: String? = null, // "background" or "app_only"
    val isCompleting: Boolean = false
)

class OnboardingViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun nextStep() {
        _uiState.value = _uiState.value.copy(
            currentStep = _uiState.value.currentStep + 1
        )
    }

    fun previousStep() {
        if (_uiState.value.currentStep > 0) {
            _uiState.value = _uiState.value.copy(
                currentStep = _uiState.value.currentStep - 1
            )
        }
    }

    fun setMonitorConsent(consented: Boolean) {
        _uiState.value = _uiState.value.copy(monitorConsent = consented)
    }

    fun setResearchConsent(consented: Boolean) {
        _uiState.value = _uiState.value.copy(researchConsent = consented)
    }

    fun setCohortArm(arm: String) {
        _uiState.value = _uiState.value.copy(cohortArm = arm)
    }

    fun setSelectedMode(mode: String) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
    }

    fun completeOnboarding(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCompleting = true)

            val mode = _uiState.value.selectedMode ?: "app_only"
            val research = _uiState.value.researchConsent
            val cohort = _uiState.value.cohortArm

            settingsRepository.setMonitoringMode(mode)
            settingsRepository.setResearchConsent(research)
            settingsRepository.setCohortArm(cohort)
            settingsRepository.setOnboardingCompleted(true)

            // Silently authenticate anonymously if research consent is enabled
            if (research) {
                try {
                    SupabaseClientProvider.ensureAnonymousAuth()
                } catch (ignored: Exception) {
                }
                com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker.triggerOneTimeSync(com.mitalipurohit.blinkwell.BlinkWellApp.instance)
            }

            _uiState.value = _uiState.value.copy(isCompleting = false)
            onSuccess()
        }
    }
}
