package com.mitalipurohit.blinkwell.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitalipurohit.blinkwell.BlinkWellApp
import com.mitalipurohit.blinkwell.data.repository.BlinkRepository
import com.mitalipurohit.blinkwell.data.repository.SettingsRepository
import com.mitalipurohit.blinkwell.detection.BlinkDetector
import com.mitalipurohit.blinkwell.detection.BlinkMetrics
import com.mitalipurohit.blinkwell.service.BlinkMonitorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class HomeUiState(
    val isMonitoring: Boolean = false,
    val currentMode: String = "app_only",
    val metrics: BlinkMetrics = BlinkMetrics(),
    val targetBpmRange: String = "15–20 BPM"
)

class HomeViewModel(
    private val blinkRepository: BlinkRepository = BlinkWellApp.repository,
    private val settingsRepository: SettingsRepository = BlinkWellApp.settingsRepository,
    val blinkDetector: BlinkDetector = BlinkWellApp.blinkDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val monitoringMode: StateFlow<String> = settingsRepository.monitoringMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "app_only")

    private var inAppSessionId: String? = null
    private var inAppLoggingJob: Job? = null

    init {
        viewModelScope.launch {
            blinkDetector.metrics.collect { metrics ->
                _uiState.value = _uiState.value.copy(metrics = metrics)
            }
        }

        viewModelScope.launch {
            settingsRepository.monitoringMode.collect { mode ->
                _uiState.value = _uiState.value.copy(currentMode = mode)
            }
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
        val mode = _uiState.value.currentMode
        _uiState.value = _uiState.value.copy(isMonitoring = true)

        if (mode == "background") {
            BlinkMonitorService.start(context)
        } else {
            // App-Only Mode: create local session and start in-app logging
            viewModelScope.launch {
                inAppSessionId = blinkRepository.createSession(mode = "app_only")
                settingsRepository.setActiveSessionId(inAppSessionId)
                startInAppMinuteLogging()
            }
        }
    }

    fun stopMonitoring(context: Context) {
        val mode = _uiState.value.currentMode
        _uiState.value = _uiState.value.copy(isMonitoring = false)

        if (mode == "background") {
            BlinkMonitorService.stop(context)
        } else {
            inAppLoggingJob?.cancel()
            val sid = inAppSessionId
            if (sid != null) {
                viewModelScope.launch {
                    blinkRepository.endSession(sid)
                    settingsRepository.setActiveSessionId(null)
                }
            }
            blinkDetector.resetSession()
        }
    }

    private fun startInAppMinuteLogging() {
        inAppLoggingJob?.cancel()
        inAppLoggingJob = viewModelScope.launch {
            while (isActive && _uiState.value.isMonitoring) {
                delay(60_000L)
                val sid = inAppSessionId
                if (sid != null) {
                    val bpm = blinkDetector.metrics.value.currentBpm
                    blinkRepository.logMinuteBpm(sid, bpm)
                }
            }
        }
    }

    override fun onCleared() {
        inAppLoggingJob?.cancel()
        super.onCleared()
    }
}
