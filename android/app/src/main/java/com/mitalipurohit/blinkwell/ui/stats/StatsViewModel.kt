package com.mitalipurohit.blinkwell.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mitalipurohit.blinkwell.BlinkWellApp
import com.mitalipurohit.blinkwell.data.local.entity.BlinkMinuteLogEntity
import com.mitalipurohit.blinkwell.data.local.entity.BlinkSessionEntity
import com.mitalipurohit.blinkwell.data.repository.BlinkRepository
import com.mitalipurohit.blinkwell.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StatsUiState(
    val todayDaysBpm: Double = 0.0,
    val todayAlerts: Int = 0,
    val totalSessions: Int = 0,
    val selectedTimeRange: TimeRangeFilter = TimeRangeFilter.LAST_1_DAY,
    val graphData: GraphUiData = GraphUiData(),
    val streakSummary: StreakSummary = StreakSummary(),
    val recentSessions: List<BlinkSessionEntity> = emptyList(),
    val thresholdBpm: Int = 13,
    val isLoading: Boolean = true
)

class StatsViewModel(
    private val repository: BlinkRepository = BlinkWellApp.repository,
    private val settingsRepository: SettingsRepository = BlinkWellApp.settingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private var allMinuteLogs30Days: List<BlinkMinuteLogEntity> = emptyList()
    private var allSessions30Days: List<BlinkSessionEntity> = emptyList()

    init {
        loadStats()
    }

    fun setTimeRange(range: TimeRangeFilter) {
        _uiState.value = _uiState.value.copy(selectedTimeRange = range)
        recomputeGraphAndStreak()
    }

    fun loadStats() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Requirement 3: Enforce 30-day max storage limit
            repository.pruneDataOlderThan30Days()

            val threshold = settingsRepository.bpmThreshold.first()
            val (todayAvg, alertCount) = repository.getTodayStats()
            val totalSessions = repository.getTotalSessionsCount()

            _uiState.value = _uiState.value.copy(
                thresholdBpm = threshold,
                todayDaysBpm = todayAvg,
                todayAlerts = alertCount,
                totalSessions = totalSessions
            )

            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000L)

            combine(
                repository.getMinuteLogsSince(thirtyDaysAgo),
                repository.getSessionsSince(thirtyDaysAgo)
            ) { logs, sessions ->
                allMinuteLogs30Days = logs
                allSessions30Days = sessions
                recomputeGraphAndStreak()
            }.collect {}
        }
    }

    private fun recomputeGraphAndStreak() {
        val now = System.currentTimeMillis()
        val threshold = _uiState.value.thresholdBpm
        val selectedRange = _uiState.value.selectedTimeRange

        // 1. Compute 30-day streak data and Daily Averages ("Days BPM")
        val streakSummary = compute30DayStreak(now, threshold, allMinuteLogs30Days, allSessions30Days)

        // 2. Compute Graph Data based on selected time range
        val graphData = computeGraphData(selectedRange, now, threshold, allMinuteLogs30Days, allSessions30Days, streakSummary.thirtyDaysStreakList)

        // 3. Compute today's Days BPM
        val todayInfo = streakSummary.thirtyDaysStreakList.lastOrNull { it.isToday }
        val todayDaysBpm = todayInfo?.daysBpm ?: _uiState.value.todayDaysBpm

        _uiState.value = _uiState.value.copy(
            graphData = graphData,
            streakSummary = streakSummary,
            todayDaysBpm = todayDaysBpm,
            recentSessions = allSessions30Days.takeLast(10).reversed(),
            isLoading = false
        )
    }

    private fun computeGraphData(
        range: TimeRangeFilter,
        now: Long,
        threshold: Int,
        logs: List<BlinkMinuteLogEntity>,
        sessions: List<BlinkSessionEntity>,
        thirtyDaysList: List<DayStreakInfo>
    ): GraphUiData {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        return when (range) {
            TimeRangeFilter.LAST_1_HOUR -> {
                val cutoff = now - (60 * 60 * 1000L)
                val relevantLogs = logs.filter { it.minuteTimestamp >= cutoff }.sortedBy { it.minuteTimestamp }
                val points = relevantLogs.map { log ->
                    GraphPoint(
                        timestamp = log.minuteTimestamp,
                        xLabel = timeFormat.format(Date(log.minuteTimestamp)),
                        bpm = log.bpm,
                        isDaysBpm = false
                    )
                }
                val avg = if (points.isNotEmpty()) points.map { it.bpm }.average() else 0.0
                val min = if (points.isNotEmpty()) points.minOf { it.bpm } else 0.0
                val max = if (points.isNotEmpty()) points.maxOf { it.bpm } else 0.0

                val xAnnotations = listOf("-60m", "-45m", "-30m", "-15m", "Now")
                val yAnnotations = calculateYAnnotations(max.coerceAtLeast(30.0))

                GraphUiData(
                    timeRange = range,
                    points = points,
                    averageBpm = avg,
                    minBpm = min,
                    maxBpm = max,
                    isDaysBpm = false,
                    xNumericAnnotations = xAnnotations,
                    yNumericAnnotations = yAnnotations,
                    thresholdBpm = threshold
                )
            }

            TimeRangeFilter.LAST_1_DAY -> {
                val cutoff = now - (24 * 60 * 60 * 1000L)
                val relevantLogs = logs.filter { it.minuteTimestamp >= cutoff }.sortedBy { it.minuteTimestamp }

                // Group logs into hourly buckets for smooth 24-hour presentation
                val points = if (relevantLogs.isNotEmpty()) {
                    val bucketedPoints = (23 downTo 0).map { hoursAgo ->
                        val bucketStart = now - ((hoursAgo + 1) * 3600 * 1000L)
                        val bucketEnd = now - (hoursAgo * 3600 * 1000L)
                        val inBucket = relevantLogs.filter { it.minuteTimestamp in bucketStart until bucketEnd }
                        val bucketAvg = if (inBucket.isNotEmpty()) inBucket.map { it.bpm }.average() else null
                        val label = if (hoursAgo == 0) "Now" else "-${hoursAgo}h"
                        bucketAvg?.let {
                            GraphPoint(
                                timestamp = bucketEnd,
                                xLabel = label,
                                bpm = it,
                                isDaysBpm = true,
                                secondaryLabel = timeFormat.format(Date(bucketEnd))
                            )
                        }
                    }.filterNotNull()

                    if (bucketedPoints.isEmpty()) {
                        relevantLogs.map { log ->
                            GraphPoint(
                                timestamp = log.minuteTimestamp,
                                xLabel = timeFormat.format(Date(log.minuteTimestamp)),
                                bpm = log.bpm,
                                isDaysBpm = true
                            )
                        }
                    } else {
                        bucketedPoints
                    }
                } else {
                    emptyList()
                }

                val avg = if (points.isNotEmpty()) points.map { it.bpm }.average() else 0.0
                val min = if (points.isNotEmpty()) points.minOf { it.bpm } else 0.0
                val max = if (points.isNotEmpty()) points.maxOf { it.bpm } else 0.0

                val xAnnotations = listOf("-24h", "-18h", "-12h", "-6h", "Now")
                val yAnnotations = calculateYAnnotations(max.coerceAtLeast(30.0))

                GraphUiData(
                    timeRange = range,
                    points = points,
                    averageBpm = avg,
                    minBpm = min,
                    maxBpm = max,
                    isDaysBpm = true, // Days BPM
                    xNumericAnnotations = xAnnotations,
                    yNumericAnnotations = yAnnotations,
                    thresholdBpm = threshold
                )
            }

            TimeRangeFilter.LAST_7_DAYS -> {
                // Last 7 days daily averages (Days BPM)
                val last7Days = thirtyDaysList.takeLast(7)
                val points = last7Days.map { day ->
                    GraphPoint(
                        timestamp = day.dateEpochMs,
                        xLabel = day.dateFormatted,
                        bpm = day.daysBpm,
                        isDaysBpm = true,
                        secondaryLabel = "Day ${day.dayIndex}"
                    )
                }

                val activePoints = points.filter { it.bpm > 0 }
                val avg = if (activePoints.isNotEmpty()) activePoints.map { it.bpm }.average() else 0.0
                val min = if (activePoints.isNotEmpty()) activePoints.minOf { it.bpm } else 0.0
                val max = if (activePoints.isNotEmpty()) activePoints.maxOf { it.bpm } else 0.0

                val xAnnotations = points.map { it.xLabel }
                val yAnnotations = calculateYAnnotations(max.coerceAtLeast(30.0))

                GraphUiData(
                    timeRange = range,
                    points = points,
                    averageBpm = avg,
                    minBpm = min,
                    maxBpm = max,
                    isDaysBpm = true,
                    xNumericAnnotations = xAnnotations,
                    yNumericAnnotations = yAnnotations,
                    thresholdBpm = threshold
                )
            }

            TimeRangeFilter.LAST_30_DAYS -> {
                // Last 30 days daily averages (Days BPM)
                val points = thirtyDaysList.map { day ->
                    GraphPoint(
                        timestamp = day.dateEpochMs,
                        xLabel = "D${day.dayIndex}",
                        bpm = day.daysBpm,
                        isDaysBpm = true,
                        secondaryLabel = day.dateFormatted
                    )
                }

                val activePoints = points.filter { it.bpm > 0 }
                val avg = if (activePoints.isNotEmpty()) activePoints.map { it.bpm }.average() else 0.0
                val min = if (activePoints.isNotEmpty()) activePoints.minOf { it.bpm } else 0.0
                val max = if (activePoints.isNotEmpty()) activePoints.maxOf { it.bpm } else 0.0

                val xAnnotations = listOf("D1", "D5", "D10", "D15", "D20", "D25", "D30")
                val yAnnotations = calculateYAnnotations(max.coerceAtLeast(30.0))

                GraphUiData(
                    timeRange = range,
                    points = points,
                    averageBpm = avg,
                    minBpm = min,
                    maxBpm = max,
                    isDaysBpm = true,
                    xNumericAnnotations = xAnnotations,
                    yNumericAnnotations = yAnnotations,
                    thresholdBpm = threshold
                )
            }
        }
    }

    private fun calculateYAnnotations(maxVal: Double): List<Int> {
        val top = ((maxVal / 10).toInt() + 1) * 10
        val coercedTop = top.coerceIn(30, 60)
        val step = coercedTop / 4
        return listOf(0, step, step * 2, step * 3, coercedTop)
    }

    /**
     * Requirement 3: 30-Day Streak Viewer with Green (Healthy) and Red (Not-Healthy) color coding.
     * Days average of BPM is calculated as Days BPM.
     */
    private fun compute30DayStreak(
        now: Long,
        threshold: Int,
        logs: List<BlinkMinuteLogEntity>,
        sessions: List<BlinkSessionEntity>
    ): StreakSummary {
        val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        val thirtyDaysList = (29 downTo 0).mapIndexed { idx, daysAgo ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, -daysAgo)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = cal.timeInMillis
            val endOfDay = startOfDay + (24 * 60 * 60 * 1000L) - 1L
            val isToday = (daysAgo == 0)

            // Find logs for this day
            val dayLogs = logs.filter { it.minuteTimestamp in startOfDay..endOfDay }
            val daySessions = sessions.filter { it.startTime in startOfDay..endOfDay && it.avgBpm > 0 }

            val daysBpm = when {
                dayLogs.isNotEmpty() -> dayLogs.map { it.bpm }.average()
                daySessions.isNotEmpty() -> daySessions.map { it.avgBpm }.average()
                else -> 0.0
            }

            val status = when {
                daysBpm <= 0.0 -> StreakStatus.NO_DATA
                daysBpm >= threshold.toDouble() -> StreakStatus.HEALTHY // Green
                else -> StreakStatus.NOT_HEALTHY // Red
            }

            DayStreakInfo(
                dayIndex = idx + 1, // 1..30
                dateEpochMs = startOfDay,
                dateFormatted = dateFormat.format(Date(startOfDay)),
                daysBpm = daysBpm,
                status = status,
                isToday = isToday
            )
        }

        // Compute current streak: consecutive HEALTHY days ending today or yesterday
        var currentStreak = 0
        val reversed = thirtyDaysList.reversed()
        for (i in reversed.indices) {
            val day = reversed[i]
            if (i == 0 && day.status == StreakStatus.NO_DATA) {
                // Today has no data yet, continue to yesterday
                continue
            }
            if (day.status == StreakStatus.HEALTHY) {
                currentStreak++
            } else {
                break
            }
        }

        // Compute best streak: max consecutive HEALTHY days in the 30-day window
        var bestStreak = 0
        var runningStreak = 0
        for (day in thirtyDaysList) {
            if (day.status == StreakStatus.HEALTHY) {
                runningStreak++
                if (runningStreak > bestStreak) {
                    bestStreak = runningStreak
                }
            } else {
                runningStreak = 0
            }
        }

        val totalHealthy = thirtyDaysList.count { it.status == StreakStatus.HEALTHY }
        val totalNotHealthy = thirtyDaysList.count { it.status == StreakStatus.NOT_HEALTHY }
        val totalMonitored = totalHealthy + totalNotHealthy

        return StreakSummary(
            currentHealthyStreak = currentStreak,
            bestHealthyStreak = bestStreak,
            totalHealthyDays = totalHealthy,
            totalNotHealthyDays = totalNotHealthy,
            totalMonitoredDays = totalMonitored,
            thresholdBpm = threshold,
            thirtyDaysStreakList = thirtyDaysList
        )
    }
}
