package com.mitalipurohit.blinkwell.ui.stats

enum class TimeRangeFilter(val displayName: String, val durationMs: Long) {
    LAST_1_HOUR("1 Hour", 60 * 60 * 1000L),
    LAST_1_DAY("1 Day", 24 * 60 * 60 * 1000L),
    LAST_7_DAYS("7 Days", 7 * 24 * 60 * 60 * 1000L),
    LAST_30_DAYS("30 Days", 30 * 24 * 60 * 60 * 1000L)
}

data class GraphPoint(
    val timestamp: Long,
    val xLabel: String,
    val bpm: Double,
    val isDaysBpm: Boolean = false,
    val secondaryLabel: String = ""
)

enum class StreakStatus {
    HEALTHY,      // Green (Days BPM >= threshold)
    NOT_HEALTHY,  // Red (Days BPM < threshold)
    NO_DATA       // Muted (no monitoring records for day)
}

data class DayStreakInfo(
    val dayIndex: Int,           // 1 to 30
    val dateEpochMs: Long,
    val dateFormatted: String,   // e.g. "Oct 2"
    val daysBpm: Double,         // Day's average BPM ("Days BPM")
    val status: StreakStatus,
    val isToday: Boolean = false
)

data class StreakSummary(
    val currentHealthyStreak: Int = 0,
    val bestHealthyStreak: Int = 0,
    val totalHealthyDays: Int = 0,
    val totalNotHealthyDays: Int = 0,
    val totalMonitoredDays: Int = 0,
    val thresholdBpm: Int = 13,
    val thirtyDaysStreakList: List<DayStreakInfo> = emptyList()
)

data class GraphUiData(
    val timeRange: TimeRangeFilter = TimeRangeFilter.LAST_1_DAY,
    val points: List<GraphPoint> = emptyList(),
    val averageBpm: Double = 0.0,
    val minBpm: Double = 0.0,
    val maxBpm: Double = 0.0,
    val isDaysBpm: Boolean = false,
    val xNumericAnnotations: List<String> = emptyList(),
    val yNumericAnnotations: List<Int> = listOf(0, 5, 10, 15, 20, 25, 30),
    val thresholdBpm: Int = 13
)
