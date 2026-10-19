package com.mitalipurohit.blinkwell.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitalipurohit.blinkwell.BlinkWellApp
import com.mitalipurohit.blinkwell.data.repository.BlinkRepository
import com.mitalipurohit.blinkwell.data.repository.SettingsRepository
import com.mitalipurohit.blinkwell.detection.BlinkDetector
import com.mitalipurohit.blinkwell.detection.BlinkMetrics
import com.mitalipurohit.blinkwell.detection.BurstSessionResult
import com.mitalipurohit.blinkwell.service.BlinkMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val isMonitoring: Boolean = false,
    val appMode: String = "burst", // "burst" or "monitoring"
    val metrics: BlinkMetrics = BlinkMetrics(),
    val targetBpmRange: String = "15–20 Blinks/Min",
    val burstResult: BurstSessionResult? = null,
    val showMonitoringSuggestionDialog: Boolean = false
)

class HomeViewModel(
    private val blinkRepository: BlinkRepository = BlinkWellApp.repository,
    private val settingsRepository: SettingsRepository = BlinkWellApp.settingsRepository,
    val blinkDetector: BlinkDetector = BlinkWellApp.blinkDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isMonitoring = BlinkMonitorService.isRunning))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Collect current app mode from settings in real-time
        viewModelScope.launch {
            settingsRepository.appMode.collect { mode ->
                _uiState.value = _uiState.value.copy(appMode = mode)
            }
        }

        // Check if user completed their first 5-min test and prompt for Monitoring Mode on launch
        viewModelScope.launch {
            val completedFirstTest = settingsRepository.hasCompletedFirst5MinTest.first()
            val currentMode = settingsRepository.appMode.first()
            val dismissed = settingsRepository.isMonitoringPromptDismissed.first()

            if (completedFirstTest && currentMode == "burst" && !dismissed) {
                _uiState.value = _uiState.value.copy(showMonitoringSuggestionDialog = true)
            }
        }

        // Collect metrics and update UI state in real-time
        viewModelScope.launch {
            blinkDetector.metrics.collect { metrics ->
                val isServiceActive = BlinkMonitorService.isRunning
                _uiState.value = _uiState.value.copy(
                    metrics = metrics,
                    isMonitoring = isServiceActive
                )
            }
        }

        // Collect completed burst results
        viewModelScope.launch {
            blinkDetector.burstCompletionEvents.collect { result ->
                _uiState.value = _uiState.value.copy(
                    burstResult = result,
                    isMonitoring = false
                )
                // Milestone: first 5-min test has been completed
                settingsRepository.setFirst5MinTestCompleted(true)
            }
        }

        // Listen for stop request from notification action
        viewModelScope.launch {
            BlinkWellApp.instance.stopMonitoringTrigger.collect {
                if (_uiState.value.isMonitoring) {
                    stopMonitoring(BlinkWellApp.instance)
                }
            }
        }
    }

    fun dismissBurstResult() {
        _uiState.value = _uiState.value.copy(burstResult = null)
    }

    fun dismissMonitoringSuggestion(neverAskAgain: Boolean = false) {
        _uiState.value = _uiState.value.copy(showMonitoringSuggestionDialog = false)
        if (neverAskAgain) {
            viewModelScope.launch {
                settingsRepository.setMonitoringPromptDismissed(true)
            }
        }
    }

    fun enableMonitoringMode() {
        _uiState.value = _uiState.value.copy(
            showMonitoringSuggestionDialog = false,
            appMode = "monitoring"
        )
        viewModelScope.launch {
            settingsRepository.setAppMode("monitoring")
            settingsRepository.setMonitoringPromptDismissed(true)
        }
    }

    fun toggleMonitoring(context: Context) {
        if (_uiState.value.isMonitoring) {
            stopMonitoring(context)
        } else {
            startMonitoring(context)
        }
    }

    fun startMonitoring(context: Context) {
        val mode = _uiState.value.appMode
        _uiState.value = _uiState.value.copy(
            isMonitoring = true,
            burstResult = null
        )
        blinkDetector.resetSession()
        blinkDetector.setSamplingActive(true)
        BlinkMonitorService.start(context, mode = mode)
    }

    fun stopMonitoring(context: Context) {
        _uiState.value = _uiState.value.copy(isMonitoring = false)
        BlinkMonitorService.stop(context)
    }
}
