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
import kotlinx.coroutines.launch

data class HomeUiState(
    val isMonitoring: Boolean = false,
    val appMode: String = "burst",
    val metrics: BlinkMetrics = BlinkMetrics(),
    val targetBpmRange: String = "15–20 Blinks/Min",
    val burstResult: BurstSessionResult? = null
)

class HomeViewModel(
    private val blinkRepository: BlinkRepository = BlinkWellApp.repository,
    private val settingsRepository: SettingsRepository = BlinkWellApp.settingsRepository,
    val blinkDetector: BlinkDetector = BlinkWellApp.blinkDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isMonitoring = BlinkMonitorService.isRunning))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
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

    fun toggleMonitoring(context: Context) {
        if (_uiState.value.isMonitoring) {
            stopMonitoring(context)
        } else {
            startMonitoring(context)
        }
    }

    fun startMonitoring(context: Context) {
        _uiState.value = _uiState.value.copy(
            isMonitoring = true,
            burstResult = null
        )
        blinkDetector.resetSession()
        blinkDetector.setSamplingActive(true)
        BlinkMonitorService.start(context, mode = "burst")
    }

    fun stopMonitoring(context: Context) {
        _uiState.value = _uiState.value.copy(isMonitoring = false)
        BlinkMonitorService.stop(context)
    }
}
