package com.mitalipurohit.blinkwell

import android.app.Application
import com.mitalipurohit.blinkwell.data.local.BlinkDatabase
import com.mitalipurohit.blinkwell.data.preferences.PreferenceManager
import com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker
import com.mitalipurohit.blinkwell.data.repository.BlinkRepository
import com.mitalipurohit.blinkwell.data.repository.SettingsRepository
import com.mitalipurohit.blinkwell.detection.BlinkDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class BlinkWellApp : Application() {

    companion object {
        lateinit var instance: BlinkWellApp
            private set

        lateinit var database: BlinkDatabase
            private set

        lateinit var preferenceManager: PreferenceManager
            private set

        lateinit var repository: BlinkRepository
            private set

        lateinit var settingsRepository: SettingsRepository
            private set

        val blinkDetector: BlinkDetector by lazy {
            BlinkDetector()
        }
    }

    private val _stopMonitoringTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val stopMonitoringTrigger: SharedFlow<Unit> = _stopMonitoringTrigger.asSharedFlow()

    fun requestStopMonitoring() {
        _stopMonitoringTrigger.tryEmit(Unit)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = BlinkDatabase.getInstance(this)
        preferenceManager = PreferenceManager(this)
        repository = BlinkRepository(database.blinkDao())
        settingsRepository = SettingsRepository(preferenceManager)

        // Schedule periodic sync worker
        BlinkSyncWorker.schedulePeriodicSync(this)

        // Trigger immediate catch-up sync on app launch
        BlinkSyncWorker.triggerOneTimeSync(this)

        // Requirement 3: Enforce 30-day max storage limit on startup
        CoroutineScope(Dispatchers.IO).launch {
            repository.pruneDataOlderThan30Days()
        }
    }
}

