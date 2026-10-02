package com.mitalipurohit.blinkwell

import android.app.Application
import com.mitalipurohit.blinkwell.data.local.BlinkDatabase
import com.mitalipurohit.blinkwell.data.preferences.PreferenceManager
import com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker
import com.mitalipurohit.blinkwell.data.repository.BlinkRepository
import com.mitalipurohit.blinkwell.data.repository.SettingsRepository
import com.mitalipurohit.blinkwell.detection.BlinkDetector

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

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = BlinkDatabase.getInstance(this)
        preferenceManager = PreferenceManager(this)
        repository = BlinkRepository(database.blinkDao())
        settingsRepository = SettingsRepository(preferenceManager)

        // Schedule periodic sync worker
        BlinkSyncWorker.schedulePeriodicSync(this)
    }
}
