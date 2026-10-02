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
import com.mitalipurohit.blinkwell.service.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val notificationHelper = NotificationHelper(BlinkWellApp.instance)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val monitoringMode: StateFlow<String> = settingsRepository.monitoringMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "app_only")

    private var inAppSessionId: String? = null
    private var inAppLoggingJob: Job? = null
    private var inAppGracePeriodJob: Job? = null

    init {
        // Collect metrics and update sticky status notification
        viewModelScope.launch {
            blinkDetector.metrics.collect { metrics ->
                _uiState.value = _uiState.value.copy(metrics = metrics)
                if (_uiState.value.isMonitoring && _uiState.value.currentMode == "app_only") {
                    val threshold = settingsRepository.bpmThreshold.first()
                    notificationHelper.updateStatusNotification(
                        category = metrics.statusCategory,
                        bpm = metrics.currentBpm,
                        thresholdBpm = threshold
                    )
                }
            }
        }

        // Collect monitoring mode changes
        viewModelScope.launch {
            settingsRepository.monitoringMode.collect { mode ->
                _uiState.value = _uiState.value.copy(currentMode = mode)
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

        // Observe alert events in app-only mode
        viewModelScope.launch {
            blinkDetector.alertEvents.collect {
                if (_uiState.value.isMonitoring && _uiState.value.currentMode == "app_only") {
                    val alertsEnabled = settingsRepository.alertsEnabled.first()
                    if (alertsEnabled) {
                        notificationHelper.showAlertNotification()
                    }
                }
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
            // App-Only Mode: create local session, start logging & show sticky notification
            viewModelScope.launch {
                val threshold = settingsRepository.bpmThreshold.first()
                blinkDetector.setThreshold(threshold)

                inAppSessionId = blinkRepository.createSession(mode = "app_only")
                settingsRepository.setActiveSessionId(inAppSessionId)

                startInAppMinuteLogging()
                startInAppGracePeriodTicker()

                val initialMetrics = blinkDetector.metrics.value
                notificationHelper.updateStatusNotification(
                    category = initialMetrics.statusCategory,
                    bpm = initialMetrics.currentBpm,
                    thresholdBpm = threshold,
                    force = true
                )
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
            inAppGracePeriodJob?.cancel()
            notificationHelper.cancelStatusNotification()

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

    private fun startInAppGracePeriodTicker() {
        inAppGracePeriodJob?.cancel()
        inAppGracePeriodJob = viewModelScope.launch {
            while (isActive && _uiState.value.isMonitoring) {
                delay(1000L)
                blinkDetector.checkGracePeriod()
            }
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
        inAppGracePeriodJob?.cancel()
        notificationHelper.cancelStatusNotification()
        super.onCleared()
    }
}
