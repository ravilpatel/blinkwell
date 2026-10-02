package com.mitalipurohit.blinkwell.data.repository

import com.mitalipurohit.blinkwell.data.local.dao.BlinkDao
import com.mitalipurohit.blinkwell.data.local.entity.BlinkMinuteLogEntity
import com.mitalipurohit.blinkwell.data.local.entity.BlinkSessionEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.UUID

class BlinkRepository(private val blinkDao: BlinkDao) {

    val allSessions: Flow<List<BlinkSessionEntity>> = blinkDao.getAllSessions()
    val latestSession: Flow<BlinkSessionEntity?> = blinkDao.getLatestSession()
    val recentMinuteLogs: Flow<List<BlinkMinuteLogEntity>> = blinkDao.getRecentMinuteLogs()

    suspend fun createSession(mode: String): String {
        val sessionId = UUID.randomUUID().toString()
        val session = BlinkSessionEntity(
            id = sessionId,
            startTime = System.currentTimeMillis(),
            mode = mode
        )
        blinkDao.insertSession(session)
        return sessionId
    }

    suspend fun logMinuteBpm(sessionId: String, bpm: Double) {
        val log = BlinkMinuteLogEntity(
            sessionId = sessionId,
            minuteTimestamp = System.currentTimeMillis(),
            bpm = bpm
        )
        blinkDao.insertMinuteLog(log)
    }

    suspend fun endSession(sessionId: String, alertCount: Int = 0) {
        val session = blinkDao.getSessionById(sessionId) ?: return
        val endTime = System.currentTimeMillis()
        
        // Calculate average and min BPM for session if logs exist
        val updated = session.copy(
            endTime = endTime,
            alertCount = alertCount
        )
        blinkDao.updateSession(updated)
    }

    suspend fun updateSessionMetrics(sessionId: String, avgBpm: Double, minBpm: Double, alertCount: Int) {
        val session = blinkDao.getSessionById(sessionId) ?: return
        val updated = session.copy(
            avgBpm = avgBpm,
            minBpm = minBpm,
            alertCount = alertCount,
            endTime = System.currentTimeMillis()
        )
        blinkDao.updateSession(updated)
    }

    suspend fun getTodayStats(): Pair<Double, Int> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val avgBpm = blinkDao.getTodayAverageBpm(startOfDay) ?: 0.0
        val alertCount = blinkDao.getTodayAlertCount(startOfDay) ?: 0
        return Pair(avgBpm, alertCount)
    }

    suspend fun getTotalSessionsCount(): Int {
        return blinkDao.getTotalSessionsCount()
    }

    fun getSessionMinuteLogs(sessionId: String): Flow<List<BlinkMinuteLogEntity>> {
        return blinkDao.getMinuteLogsForSession(sessionId)
    }

    suspend fun deleteAllData() {
        blinkDao.deleteAllMinuteLogs()
        blinkDao.deleteAllSessions()
    }
}
