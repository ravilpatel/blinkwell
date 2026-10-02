package com.mitalipurohit.blinkwell.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitalipurohit.blinkwell.BlinkWellApp
import com.mitalipurohit.blinkwell.data.repository.BlinkRepository
import com.mitalipurohit.blinkwell.data.repository.SettingsRepository
import com.mitalipurohit.blinkwell.service.BlinkMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val bpmThreshold: Int = 13,
    val monitoringMode: String = "app_only",
    val samplingMode: String = "duty_cycle",
    val alertsEnabled: Boolean = true,
    val researchConsent: Boolean = true,
    val selectedLanguage: String = "system"
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository = BlinkWellApp.settingsRepository,
    private val blinkRepository: BlinkRepository = BlinkWellApp.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.bpmThreshold.collect { threshold ->
                _uiState.value = _uiState.value.copy(bpmThreshold = threshold)
                BlinkWellApp.blinkDetector.setThreshold(threshold)
            }
        }
        viewModelScope.launch {
            settingsRepository.monitoringMode.collect { mode ->
                _uiState.value = _uiState.value.copy(monitoringMode = mode)
            }
        }
        viewModelScope.launch {
            settingsRepository.samplingMode.collect { mode ->
                _uiState.value = _uiState.value.copy(samplingMode = mode)
            }
        }
        viewModelScope.launch {
            settingsRepository.alertsEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(alertsEnabled = enabled)
            }
        }
        viewModelScope.launch {
            settingsRepository.researchConsent.collect { consent ->
                _uiState.value = _uiState.value.copy(researchConsent = consent)
            }
        }
        viewModelScope.launch {
            settingsRepository.selectedLanguage.collect { lang ->
                _uiState.value = _uiState.value.copy(selectedLanguage = lang)
            }
        }
    }

    fun setThreshold(threshold: Int) {
        viewModelScope.launch {
            settingsRepository.setBpmThreshold(threshold)
        }
    }

    fun setMonitoringMode(context: Context, mode: String) {
        viewModelScope.launch {
            settingsRepository.setMonitoringMode(mode)
            if (mode == "app_only" && BlinkMonitorService.isRunning) {
                // Switching from background to app-only immediately removes foreground service
                BlinkMonitorService.stop(context)
            }
        }
    }

    fun setSamplingMode(mode: String) {
        viewModelScope.launch {
            settingsRepository.setSamplingMode(mode)
        }
    }

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAlertsEnabled(enabled)
        }
    }

    fun setResearchConsent(consent: Boolean) {
        viewModelScope.launch {
            settingsRepository.setResearchConsent(consent)
            if (consent) {
                try {
                    com.mitalipurohit.blinkwell.data.remote.SupabaseClientProvider.ensureAnonymousAuth()
                } catch (ignored: Exception) {
                }
                com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker.triggerOneTimeSync(BlinkWellApp.instance)
            }
        }
    }

    fun triggerManualSync() {
        com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker.triggerOneTimeSync(BlinkWellApp.instance)
    }

    fun setSelectedLanguage(lang: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedLanguage(lang)
        }
    }

    fun deleteAllLocalData(onCompleted: () -> Unit) {
        viewModelScope.launch {
            blinkRepository.deleteAllData()
            onCompleted()
        }
    }
}
