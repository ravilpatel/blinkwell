package com.mitalipurohit.blinkwell.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitalipurohit.blinkwell.BlinkWellApp
import com.mitalipurohit.blinkwell.data.local.entity.BlinkMinuteLogEntity
import com.mitalipurohit.blinkwell.data.local.entity.BlinkSessionEntity
import com.mitalipurohit.blinkwell.data.repository.BlinkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class StatsUiState(
    val todayAvgBpm: Double = 0.0,
    val todayAlerts: Int = 0,
    val totalSessions: Int = 0,
    val recentLogs: List<BlinkMinuteLogEntity> = emptyList(),
    val recentSessions: List<BlinkSessionEntity> = emptyList(),
    val isLoading: Boolean = true
)

class StatsViewModel(
    private val repository: BlinkRepository = BlinkWellApp.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val (avgBpm, alertCount) = repository.getTodayStats()
            val totalSessions = repository.getTotalSessionsCount()

            launch {
                repository.recentMinuteLogs.collect { logs ->
                    _uiState.value = _uiState.value.copy(
                        recentLogs = logs.reversed(), // chronological order for chart
                        todayAvgBpm = avgBpm,
                        todayAlerts = alertCount,
                        totalSessions = totalSessions,
                        isLoading = false
                    )
                }
            }

            launch {
                repository.allSessions.collect { sessions ->
                    _uiState.value = _uiState.value.copy(
                        recentSessions = sessions.take(10)
                    )
                }
            }
        }
    }
}
