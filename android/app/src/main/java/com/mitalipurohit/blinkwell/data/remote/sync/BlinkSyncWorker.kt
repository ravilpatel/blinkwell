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

import android.util.Log

class BlinkSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BlinkSyncWorker"
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
            Log.d(TAG, "Triggering immediate one-time sync request...")
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
        Log.d(TAG, "Starting sync worker execution...")
        val settingsRepo = BlinkWellApp.settingsRepository
        val hasConsent = settingsRepo.researchConsent.first()

        // Absolute rule: Never sync any data if user did not opt into research data sharing
        if (!hasConsent) {
            Log.d(TAG, "Sync aborted: User has not enabled research consent.")
            return Result.success()
        }

        if (!SupabaseClientProvider.isConfigured()) {
            Log.w(TAG, "Sync skipped: Supabase credentials are not configured in local.properties.")
            return Result.failure()
        }

        return try {
            val userId = SupabaseClientProvider.ensureAnonymousAuth()
            if (userId == null) {
                Log.e(TAG, "Sync failed: Could not establish authenticated user session with Supabase. Check that Anonymous Sign-ins are enabled.")
                return Result.retry()
            }

            val database = BlinkWellApp.database
            val dao = database.blinkDao()

            // 1. Ensure profile exists with research_consent = true
            try {
                Log.d(TAG, "Upserting profile record for user: $userId")
                SupabaseClientProvider.postgrest["profiles"].upsert(
                    ProfileRemote(
                        id = userId,
                        researchConsent = true
                    )
                )
                Log.d(TAG, "Profile successfully synced.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upsert user profile in Supabase: ${e.message}", e)
                throw e
            }

            // 2. Sync Sessions
            val unsyncedSessions = dao.getUnsyncedSessions()
            if (unsyncedSessions.isNotEmpty()) {
                Log.i(TAG, "Syncing ${unsyncedSessions.size} unsynced sessions to Supabase...")
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
                Log.i(TAG, "Successfully synced ${unsyncedSessions.size} sessions.")
            } else {
                Log.d(TAG, "No unsynced sessions to upload.")
            }

            // 3. Sync Minute Logs
            val unsyncedLogs = dao.getUnsyncedMinuteLogs()
            if (unsyncedLogs.isNotEmpty()) {
                Log.i(TAG, "Syncing ${unsyncedLogs.size} unsynced minute logs to Supabase...")
                val logsRemote = unsyncedLogs.map { log ->
                    BlinkMinuteLogRemote(
                        sessionId = log.sessionId,
                        minuteTimestamp = isoDateFormat.format(Date(log.minuteTimestamp)),
                        bpm = log.bpm
                    )
                }

                SupabaseClientProvider.postgrest["blink_minute_log"].insert(logsRemote)
                dao.markLogsSynced(unsyncedLogs.map { it.id })
                Log.i(TAG, "Successfully synced ${unsyncedLogs.size} minute logs.")
            } else {
                Log.d(TAG, "No unsynced minute logs to upload.")
            }

            Log.i(TAG, "Supabase data sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Data sync failed with error: ${e.message}", e)
            Result.retry()
        }
    }
}
