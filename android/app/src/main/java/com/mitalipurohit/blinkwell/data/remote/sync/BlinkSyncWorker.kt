package com.mitalipurohit.blinkwell.data.remote.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mitalipurohit.blinkwell.BlinkWellApp
import com.mitalipurohit.blinkwell.data.remote.SupabaseClientProvider
import com.mitalipurohit.blinkwell.data.remote.model.BlinkMinuteLogRemote
import com.mitalipurohit.blinkwell.data.remote.model.BlinkSessionRemote
import com.mitalipurohit.blinkwell.data.remote.model.ProfileRemote
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class BlinkSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val UNIQUE_PERIODIC_WORK_NAME = "BlinkWellPeriodicSync"
        private const val UNIQUE_ONE_TIME_WORK_NAME = "BlinkWellOneTimeSync"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<BlinkSyncWorker>(
                12, TimeUnit.HOURS,
                1, TimeUnit.HOURS
            )
            .setConstraints(constraints)
            .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }

        fun triggerOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<BlinkSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        }
    }

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun doWork(): Result {
        val settingsRepo = BlinkWellApp.settingsRepository
        val hasConsent = settingsRepo.researchConsent.first()

        // Absolute rule: Never sync any data if user did not opt into research data sharing
        if (!hasConsent) {
            return Result.success()
        }

        return try {
            val userId = SupabaseClientProvider.ensureAnonymousAuth() ?: return Result.retry()
            val database = BlinkWellApp.database
            val dao = database.blinkDao()

            // Ensure profile exists with research_consent = true
            try {
                SupabaseClientProvider.postgrest["profiles"].upsert(
                    ProfileRemote(
                        id = userId,
                        researchConsent = true
                    )
                )
            } catch (ignored: Exception) {
            }

            // Sync Sessions
            val unsyncedSessions = dao.getUnsyncedSessions()
            if (unsyncedSessions.isNotEmpty()) {
                val sessionsRemote = unsyncedSessions.map { session ->
                    BlinkSessionRemote(
                        id = session.id,
                        userId = userId,
                        startedAt = isoDateFormat.format(Date(session.startTime)),
                        endedAt = session.endTime?.let { isoDateFormat.format(Date(it)) },
                        avgBpm = session.avgBpm,
                        minBpm = session.minBpm,
                        alertCount = session.alertCount,
                        monitoringMode = session.mode
                    )
                }

                SupabaseClientProvider.postgrest["blink_sessions"].upsert(sessionsRemote)
                dao.markSessionsSynced(unsyncedSessions.map { it.id })
            }

            // Sync Minute Logs
            val unsyncedLogs = dao.getUnsyncedMinuteLogs()
            if (unsyncedLogs.isNotEmpty()) {
                val logsRemote = unsyncedLogs.map { log ->
                    BlinkMinuteLogRemote(
                        sessionId = log.sessionId,
                        minuteTimestamp = isoDateFormat.format(Date(log.minuteTimestamp)),
                        bpm = log.bpm
                    )
                }

                SupabaseClientProvider.postgrest["blink_minute_log"].insert(logsRemote)
                dao.markLogsSynced(unsyncedLogs.map { it.id })
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
