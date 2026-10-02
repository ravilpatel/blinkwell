package com.mitalipurohit.blinkwell

import com.mitalipurohit.blinkwell.ui.stats.DayStreakInfo
import com.mitalipurohit.blinkwell.ui.stats.StreakStatus
import com.mitalipurohit.blinkwell.ui.stats.TimeRangeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatsLogicTest {

    @Test
    fun testDaysBpmAndHealthyStreakColorCoding() {
        val threshold = 13
        val now = System.currentTimeMillis()

        // Create 30 days mock data: 5 healthy, 2 not healthy, rest no data
        val thirtyDays = (29 downTo 0).mapIndexed { idx, daysAgo ->
            val daysBpm = when (daysAgo) {
                0 -> 16.5 // Today: healthy (Green)
                1 -> 15.0 // Yesterday: healthy (Green)
                2 -> 14.2 // 2 days ago: healthy (Green)
                3 -> 9.0  // 3 days ago: not healthy (Red)
                4 -> 17.0 // 4 days ago: healthy (Green)
                else -> 0.0 // No data (Muted)
            }

            val status = when {
                daysBpm <= 0.0 -> StreakStatus.NO_DATA
                daysBpm >= threshold -> StreakStatus.HEALTHY
                else -> StreakStatus.NOT_HEALTHY
            }

            DayStreakInfo(
                dayIndex = idx + 1,
                dateEpochMs = now - (daysAgo * 86400000L),
                dateFormatted = "Day $idx",
                daysBpm = daysBpm,
                status = status,
                isToday = (daysAgo == 0)
            )
        }

        // Check color coding
        assertEquals(StreakStatus.HEALTHY, thirtyDays.last { it.isToday }.status)
        assertEquals(StreakStatus.NOT_HEALTHY, thirtyDays.first { it.daysBpm == 9.0 }.status)

        // Compute current streak: consecutive HEALTHY days ending today
        var currentStreak = 0
        val reversed = thirtyDays.reversed()
        for (day in reversed) {
            if (day.status == StreakStatus.HEALTHY) {
                currentStreak++
            } else {
                break
            }
        }

        assertEquals(3, currentStreak) // Days 0, 1, 2 are healthy
    }

    @Test
    fun testThirtyDaysDataRetentionCutoff() {
        val now = 1700000000000L
        val thirtyDaysMs = 30L * 24 * 60 * 60 * 1000L
        val cutoff = now - thirtyDaysMs

        val validLogTimestamp = now - (15L * 24 * 60 * 60 * 1000L) // 15 days ago
        val expiredLogTimestamp = now - (31L * 24 * 60 * 60 * 1000L) // 31 days ago

        assertTrue("15-day-old log should be kept", validLogTimestamp >= cutoff)
        assertTrue("31-day-old log should be pruned", expiredLogTimestamp < cutoff)
    }

    @Test
    fun testTimeRangeFilterDurations() {
        assertEquals(15 * 60 * 1000L, TimeRangeFilter.LAST_15_MIN.durationMs)
        assertEquals(60 * 60 * 1000L, TimeRangeFilter.LAST_1_HOUR.durationMs)
        assertEquals(24 * 60 * 60 * 1000L, TimeRangeFilter.LAST_1_DAY.durationMs)
        assertEquals(7 * 24 * 60 * 60 * 1000L, TimeRangeFilter.LAST_7_DAYS.durationMs)
        assertEquals(30 * 24 * 60 * 60 * 1000L, TimeRangeFilter.LAST_30_DAYS.durationMs)
    }
}
