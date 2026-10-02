package com.mitalipurohit.blinkwell.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mitalipurohit.blinkwell.data.local.entity.BlinkMinuteLogEntity
import com.mitalipurohit.blinkwell.data.local.entity.BlinkSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlinkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BlinkSessionEntity)

    @Update
    suspend fun updateSession(session: BlinkSessionEntity)

    @Query("SELECT * FROM blink_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): BlinkSessionEntity?

    @Query("SELECT * FROM blink_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<BlinkSessionEntity>>

    @Query("SELECT * FROM blink_sessions ORDER BY startTime DESC LIMIT 1")
    fun getLatestSession(): Flow<BlinkSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMinuteLog(log: BlinkMinuteLogEntity)

    @Query("SELECT * FROM blink_minute_log WHERE sessionId = :sessionId ORDER BY minuteTimestamp ASC")
    fun getMinuteLogsForSession(sessionId: String): Flow<List<BlinkMinuteLogEntity>>

    @Query("SELECT * FROM blink_minute_log ORDER BY minuteTimestamp DESC LIMIT :limit")
    fun getRecentMinuteLogs(limit: Int = 60): Flow<List<BlinkMinuteLogEntity>>

    @Query("SELECT * FROM blink_sessions WHERE isSynced = 0")
    suspend fun getUnsyncedSessions(): List<BlinkSessionEntity>

    @Query("SELECT * FROM blink_minute_log WHERE isSynced = 0")
    suspend fun getUnsyncedMinuteLogs(): List<BlinkMinuteLogEntity>

    @Query("UPDATE blink_sessions SET isSynced = 1 WHERE id IN (:sessionIds)")
    suspend fun markSessionsSynced(sessionIds: List<String>)

    @Query("UPDATE blink_minute_log SET isSynced = 1 WHERE id IN (:logIds)")
    suspend fun markLogsSynced(logIds: List<Long>)

    @Query("SELECT COUNT(*) FROM blink_sessions")
    suspend fun getTotalSessionsCount(): Int

    @Query("SELECT AVG(avgBpm) FROM blink_sessions WHERE startTime >= :startOfDayTimestamp")
    suspend fun getTodayAverageBpm(startOfDayTimestamp: Long): Double?

    @Query("SELECT SUM(alertCount) FROM blink_sessions WHERE startTime >= :startOfDayTimestamp")
    suspend fun getTodayAlertCount(startOfDayTimestamp: Long): Int?

    @Query("DELETE FROM blink_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM blink_minute_log")
    suspend fun deleteAllMinuteLogs()
}
