package com.mitalipurohit.blinkwell.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.mitalipurohit.blinkwell.BlinkWellApp
import com.mitalipurohit.blinkwell.detection.BlinkAnalyzer
import com.mitalipurohit.blinkwell.detection.BlinkDetector
import com.mitalipurohit.blinkwell.detection.BlinkStatusCategory
import com.mitalipurohit.blinkwell.util.BatteryOptimizationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BlinkMonitorService : Service(), LifecycleOwner {

    companion object {
        const val ACTION_START = "com.mitalipurohit.blinkwell.action.START"
        const val ACTION_STOP = "com.mitalipurohit.blinkwell.action.STOP"
        const val EXTRA_MODE = "com.mitalipurohit.blinkwell.extra.MODE"

        var isRunning = false
            private set

        fun start(context: Context, mode: String? = null) {
            val intent = Intent(context, BlinkMonitorService::class.java).apply {
                action = ACTION_START
                if (mode != null) {
                    putExtra(EXTRA_MODE, mode)
                }
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BlinkMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var blinkDetector: BlinkDetector
    private var blinkAnalyzer: BlinkAnalyzer? = null
    private lateinit var screenReceiver: ScreenReceiver

    private var currentSessionId: String? = null
    private var currentAppMode: String = "burst" // "burst" or "monitoring"
    private var burstJob: Job? = null
    private var dutyCycleJob: Job? = null
    private var minuteLoggingJob: Job? = null
    private var gracePeriodJob: Job? = null
    private var continuousWatchdogJob: Job? = null
    private var isScreenUnlockedAndActive = true
    private var isCameraBound = false
    private var alertCount = 0
    private var isFirstMonitoringScan = true

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        notificationHelper = NotificationHelper(this)
        blinkDetector = BlinkWellApp.blinkDetector
        cameraExecutor = Executors.newSingleThreadExecutor()

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isInteractive = pm?.isInteractive ?: true
        val isLocked = km?.isKeyguardLocked ?: false
        isScreenUnlockedAndActive = isInteractive && !isLocked

        screenReceiver = ScreenReceiver(
            onUserUnlocked = { handleUserUnlocked() },
            onScreenOff = { handleScreenOff() }
        )
        screenReceiver.register(this)

        startForegroundNotification()
        observeAlertsAndSettings()
    }

    private fun startForegroundNotification() {
        val notification = if (currentAppMode == "burst") {
            notificationHelper.buildBurstProgressNotification(remainingSeconds = 300L)
        } else {
            val initialMetrics = blinkDetector.metrics.value
            notificationHelper.buildStatusNotification(
                category = initialMetrics.statusCategory,
                bpm = initialMetrics.currentBpm
            )
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }
        ServiceCompat.startForeground(this, NotificationHelper.SERVICE_NOTIFICATION_ID, notification, type)
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                lifecycleRegistry.currentState = Lifecycle.State.STARTED
                lifecycleRegistry.currentState = Lifecycle.State.RESUMED
                val mode = intent?.getStringExtra(EXTRA_MODE)
                startMonitoring(mode)
            }
        }
        return START_STICKY
    }

    private fun startMonitoring(explicitMode: String? = null) {
        serviceScope.launch {
            val repository = BlinkWellApp.repository
            val settingsRepo = BlinkWellApp.settingsRepository
            val threshold = settingsRepo.bpmThreshold.first()
            blinkDetector.setThreshold(threshold)

            // Prune any data older than 30 days locally
            repository.pruneDataOlderThan30Days()

            val mode = explicitMode ?: settingsRepo.appMode.first()
            currentAppMode = mode

            if (mode == "burst") {
                start5MinBurstMonitoring()
            } else {
                startMonitoringModeSession()
            }
        }
    }

    private fun start5MinBurstMonitoring() {
        burstJob?.cancel()
        dutyCycleJob?.cancel()
        minuteLoggingJob?.cancel()
        gracePeriodJob?.cancel()
        continuousWatchdogJob?.cancel()

        serviceScope.launch {
            val repository = BlinkWellApp.repository
            val settingsRepo = BlinkWellApp.settingsRepository
            val sid = repository.createSession(mode = "burst")
            currentSessionId = sid
            settingsRepo.setActiveSessionId(sid)

            if (isScreenUnlockedAndActive) {
                bindCamera()
                blinkDetector.start5MinBurst()
            }

            notificationHelper.updateBurstStatusNotification(remainingSeconds = 300L, force = true)

            burstJob = serviceScope.launch {
                var elapsedActiveSeconds = 0L
                var noFaceDurationSeconds = 0L

                while (isActive && isRunning && elapsedActiveSeconds < 300L) {
                    delay(1000L)
                    if (isScreenUnlockedAndActive) {
                        val hasFaceRecently = blinkDetector.hasSeenFaceRecently(5000L)
                        if (hasFaceRecently) {
                            noFaceDurationSeconds = 0L
                            if (!isCameraBound) {
                                bindCamera()
                            }
                            elapsedActiveSeconds++
                            blinkDetector.updateBurstElapsedSeconds(elapsedActiveSeconds)

                            val remainingSeconds = (300L - elapsedActiveSeconds).coerceAtLeast(0L)
                            notificationHelper.updateBurstStatusNotification(remainingSeconds)

                            if (elapsedActiveSeconds % 60L == 0L) {
                                val minuteBpm = blinkDetector.metrics.value.currentBpm
                                repository.logMinuteBpm(sid, minuteBpm)
                                com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker.triggerOneTimeSync(this@BlinkMonitorService)
                            }
                        } else {
                            noFaceDurationSeconds++
                            // Privacy & battery rule: If no face in 5s, unbind camera and pause active countdown
                            if (noFaceDurationSeconds >= 5L && isCameraBound) {
                                unbindCamera()
                                blinkDetector.markFaceLost()
                            } else if (noFaceDurationSeconds % 10L == 0L && !isCameraBound) {
                                // Probe every 10s to see if user returned
                                bindCamera()
                            }
                        }
                    }
                }

                if (isActive && isRunning && elapsedActiveSeconds >= 300L) {
                    val burstResult = blinkDetector.finish5MinBurst(300.0)
                    repository.endSession(sid, 0)
                    settingsRepo.setActiveSessionId(null)
                    settingsRepo.setFirst5MinTestCompleted(true)
                    notificationHelper.cancelStatusNotification()
                    notificationHelper.showBurstCompletionNotification(burstResult.finalBpm, burstResult.totalBlinks)
                    com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker.triggerOneTimeSync(this@BlinkMonitorService)
                    stopMonitoring(isSessionEnded = true)
                    stopSelf()
                }
            }
        }
    }

    private fun startMonitoringModeSession() {
        isFirstMonitoringScan = true
        serviceScope.launch {
            val repository = BlinkWellApp.repository
            val settingsRepo = BlinkWellApp.settingsRepository

            val guardEnabled = settingsRepo.batteryGuardEnabled.first()
            val guardThreshold = settingsRepo.batteryGuardThreshold.first()
            val isGuardActive = BatteryOptimizationHelper.isBatteryGuardTriggered(
                this@BlinkMonitorService,
                guardEnabled,
                guardThreshold
            )

            if (isGuardActive) {
                // Battery Guard: low battery or battery saver mode -> stop monitoring and send notification
                val currentBattery = BatteryOptimizationHelper.getBatteryLevel(this@BlinkMonitorService)
                notificationHelper.showBatteryLowNotification(currentBattery, guardThreshold)
                stopMonitoring()
                stopSelf()
                return@launch
            }

            currentSessionId = repository.createSession(mode = "monitoring")
            settingsRepo.setActiveSessionId(currentSessionId)

            val samplingMode = settingsRepo.samplingMode.first()
            if (samplingMode == "continuous") {
                startContinuousMonitoring()
            } else {
                if (isScreenUnlockedAndActive) {
                    setupDutyCycleBurst()
                }
            }
        }
    }

    private fun startContinuousMonitoring() {
        continuousWatchdogJob?.cancel()
        if (isScreenUnlockedAndActive) {
            bindCamera()
            blinkDetector.setSamplingActive(true)
        }
        startMinuteLogging()
        startGracePeriodTicker()
        startContinuousWatchdog()
    }

    private fun startContinuousWatchdog() {
        continuousWatchdogJob?.cancel()
        continuousWatchdogJob = serviceScope.launch {
            while (isActive && isRunning) {
                delay(5000L)
                if (isScreenUnlockedAndActive) {
                    val settingsRepo = BlinkWellApp.settingsRepository
                    val guardEnabled = settingsRepo.batteryGuardEnabled.first()
                    val guardThreshold = settingsRepo.batteryGuardThreshold.first()

                    if (BatteryOptimizationHelper.isBatteryGuardTriggered(this@BlinkMonitorService, guardEnabled, guardThreshold)) {
                        // Battery Guard triggered: unbind camera and pause monitoring
                        unbindCamera()
                        val threshold = settingsRepo.bpmThreshold.first()
                        notificationHelper.updateStatusNotification(
                            category = BlinkStatusCategory.FACE_NOT_DETECTED,
                            bpm = 0.0,
                            thresholdBpm = threshold,
                            isWarmedUp = true,
                            force = true
                        )
                        continue
                    }

                    val hasFaceRecently = blinkDetector.hasSeenFaceRecently(5000L)
                    if (isCameraBound && !hasFaceRecently) {
                        // Privacy/Battery Rule 2: Screen on but no face seen in 5s -> unbind camera immediately
                        unbindCamera()
                        blinkDetector.markFaceLost()
                        val threshold = settingsRepo.bpmThreshold.first()
                        notificationHelper.updateStatusNotification(
                            category = BlinkStatusCategory.FACE_NOT_DETECTED,
                            bpm = 0.0,
                            thresholdBpm = threshold,
                            isWarmedUp = true,
                            force = true
                        )
                    } else if (!isCameraBound) {
                        // Periodic 5s probe to check if user returned in front of screen
                        bindCamera()
                        delay(5000L)
                        if (!blinkDetector.hasSeenFaceRecently(5000L)) {
                            unbindCamera()
                        }
                    }
                }
            }
        }
    }

    private fun startGracePeriodTicker() {
        gracePeriodJob?.cancel()
        gracePeriodJob = serviceScope.launch {
            while (isActive && isRunning) {
                delay(1000L)
                if (isScreenUnlockedAndActive) {
                    blinkDetector.checkGracePeriod()
                }
            }
        }
    }

    private fun setupDutyCycleBurst() {
        dutyCycleJob?.cancel()
        minuteLoggingJob?.cancel()
        gracePeriodJob?.cancel()
        continuousWatchdogJob?.cancel()

        dutyCycleJob = serviceScope.launch {
            while (isActive && isRunning) {
                if (!isScreenUnlockedAndActive) {
                    unbindCamera()
                    delay(1000L)
                    continue
                }

                val settingsRepo = BlinkWellApp.settingsRepository
                val guardEnabled = settingsRepo.batteryGuardEnabled.first()
                val guardThreshold = settingsRepo.batteryGuardThreshold.first()

                if (BatteryOptimizationHelper.isBatteryGuardTriggered(this@BlinkMonitorService, guardEnabled, guardThreshold)) {
                    val currentBattery = BatteryOptimizationHelper.getBatteryLevel(this@BlinkMonitorService)
                    notificationHelper.showBatteryLowNotification(currentBattery, guardThreshold)
                    unbindCamera()
                    stopMonitoring()
                    stopSelf()
                    return@launch
                }

                // 1st scan is 60s (1 min continuous); subsequent scans are exactly 30s
                val scanDurationSeconds = if (isFirstMonitoringScan) 60L else 30L

                // 1. Bind Camera and Start scan
                bindCamera()
                blinkDetector.startBurstScan()

                var scanInterrupted = false
                var elapsedScanSeconds = 0L

                while (isActive && isRunning && elapsedScanSeconds < scanDurationSeconds) {
                    delay(1000L)
                    if (!isScreenUnlockedAndActive) {
                        scanInterrupted = true
                        blinkDetector.cancelBurst()
                        unbindCamera()
                        break
                    }

                    if (BatteryOptimizationHelper.isBatteryGuardTriggered(this@BlinkMonitorService, guardEnabled, guardThreshold)) {
                        val currentBattery = BatteryOptimizationHelper.getBatteryLevel(this@BlinkMonitorService)
                        notificationHelper.showBatteryLowNotification(currentBattery, guardThreshold)
                        unbindCamera()
                        stopMonitoring()
                        stopSelf()
                        return@launch
                    }

                    elapsedScanSeconds++
                }

                if (scanInterrupted || !isActive || !isRunning) {
                    continue
                }

                // 2. Scan completed for full duration (60s or 30s): finish scan and unbind camera
                val burstResult = blinkDetector.finishBurstScan(scanDurationSeconds = scanDurationSeconds.toDouble())
                unbindCamera()
                isFirstMonitoringScan = false

                val threshold = settingsRepo.bpmThreshold.first()
                val bpm = burstResult.bpm

                currentSessionId?.let { sid ->
                    BlinkWellApp.repository.logMinuteBpm(sid, bpm)
                    com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker.triggerOneTimeSync(this@BlinkMonitorService)
                }

                // Color-coded notification:
                // Green if properly maintained (BPM >= threshold), Red if low (BPM < threshold)
                val category = if (burstResult.faceDetected) {
                    if (burstResult.isLowRate) BlinkStatusCategory.LOW_RATE else BlinkStatusCategory.NORMAL
                } else {
                    BlinkStatusCategory.FACE_NOT_DETECTED
                }

                notificationHelper.updateStatusNotification(
                    category = category,
                    bpm = bpm,
                    thresholdBpm = threshold,
                    isWarmedUp = true,
                    force = true
                )

                if (category == BlinkStatusCategory.LOW_RATE) {
                    val alertsEnabled = settingsRepo.alertsEnabled.first()
                    if (alertsEnabled) {
                        alertCount++
                        notificationHelper.showGreenToRedAlertNotification(bpm, threshold)
                    }
                }

                // 3. Keep cooling of 2 minutes (120 seconds): camera remains closed
                var coolingElapsedSeconds = 0L
                while (isActive && isRunning && coolingElapsedSeconds < 120L) {
                    delay(1000L)
                    if (isScreenUnlockedAndActive) {
                        if (BatteryOptimizationHelper.isBatteryGuardTriggered(this@BlinkMonitorService, guardEnabled, guardThreshold)) {
                            val currentBattery = BatteryOptimizationHelper.getBatteryLevel(this@BlinkMonitorService)
                            notificationHelper.showBatteryLowNotification(currentBattery, guardThreshold)
                            unbindCamera()
                            stopMonitoring()
                            stopSelf()
                            return@launch
                        }
                        coolingElapsedSeconds++
                    }
                }
            }
        }
    }

    private fun bindCamera() {
        if (isCameraBound || !isScreenUnlockedAndActive) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                if (!isScreenUnlockedAndActive) return@addListener

                cameraProvider = cameraProviderFuture.get()
                cameraProvider?.unbindAll()

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                blinkAnalyzer = BlinkAnalyzer(blinkDetector)

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(480, 360))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                cameraExecutor?.let { executor ->
                    blinkAnalyzer?.let { analyzer ->
                        imageAnalysis.setAnalyzer(executor, analyzer)
                    }
                }

                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalysis
                )
                isCameraBound = true
            } catch (e: Exception) {
                isCameraBound = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun unbindCamera() {
        if (isCameraBound) {
            try {
                cameraProvider?.unbindAll()
            } catch (ignored: Exception) {
            }
            isCameraBound = false
        }
    }

    private fun handleUserUnlocked() {
        if (!isScreenUnlockedAndActive) {
            isScreenUnlockedAndActive = true
            serviceScope.launch {
                if (currentAppMode == "burst") {
                    bindCamera()
                } else {
                    val samplingMode = BlinkWellApp.settingsRepository.samplingMode.first()
                    if (samplingMode == "continuous") {
                        startContinuousMonitoring()
                    } else {
                        setupDutyCycleBurst()
                    }
                }
            }
        }
    }

    private fun handleScreenOff() {
        isScreenUnlockedAndActive = false
        // Pause analysis immediately on screen off
        if (currentAppMode != "burst") {
            dutyCycleJob?.cancel()
            minuteLoggingJob?.cancel()
            gracePeriodJob?.cancel()
            continuousWatchdogJob?.cancel()
            blinkDetector.cancelBurst()
        }
        unbindCamera()
    }

    private fun startMinuteLogging() {
        minuteLoggingJob?.cancel()
        minuteLoggingJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                delay(60_000L)
                val sid = currentSessionId
                if (sid != null && isScreenUnlockedAndActive) {
                    val currentBpm = blinkDetector.metrics.value.currentBpm
                    BlinkWellApp.repository.logMinuteBpm(sid, currentBpm)
                    com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker.triggerOneTimeSync(this@BlinkMonitorService)
                }
            }
        }
    }

    private fun observeAlertsAndSettings() {
        // Collect metrics and update dynamic sticky notification
        serviceScope.launch {
            blinkDetector.metrics.collect { metrics ->
                if (currentAppMode == "burst") {
                    val remaining = (300L - metrics.burstElapsedSeconds).coerceAtLeast(0L)
                    notificationHelper.updateBurstStatusNotification(remaining)
                } else {
                    val samplingMode = BlinkWellApp.settingsRepository.samplingMode.first()
                    if (samplingMode == "continuous" || isCameraBound) {
                        val threshold = BlinkWellApp.settingsRepository.bpmThreshold.first()
                        notificationHelper.updateStatusNotification(
                            category = metrics.statusCategory,
                            bpm = metrics.currentBpm,
                            thresholdBpm = threshold,
                            isWarmedUp = true
                        )
                    }
                }
            }
        }

        // Collect continuous mode Green-to-Red movements for immediate visible alert (only in monitoring mode)
        serviceScope.launch {
            blinkDetector.greenToRedTransitions.collect { bpm ->
                if (currentAppMode == "monitoring") {
                    val samplingMode = BlinkWellApp.settingsRepository.samplingMode.first()
                    if (samplingMode == "continuous") {
                        val alertsEnabled = BlinkWellApp.settingsRepository.alertsEnabled.first()
                        if (alertsEnabled) {
                            val threshold = BlinkWellApp.settingsRepository.bpmThreshold.first()
                            alertCount++
                            notificationHelper.showGreenToRedAlertNotification(bpm, threshold)
                        }
                    }
                }
            }
        }

        // Collect continuous mode sustained low-blink alert events (only in monitoring mode)
        serviceScope.launch {
            blinkDetector.alertEvents.collect {
                if (currentAppMode == "monitoring") {
                    val samplingMode = BlinkWellApp.settingsRepository.samplingMode.first()
                    if (samplingMode == "continuous") {
                        val alertsEnabled = BlinkWellApp.settingsRepository.alertsEnabled.first()
                        if (alertsEnabled) {
                            alertCount++
                            notificationHelper.showAlertNotification()
                        }
                    }
                }
            }
        }
    }

    private fun stopMonitoring(isSessionEnded: Boolean = false) {
        isRunning = false
        burstJob?.cancel()
        dutyCycleJob?.cancel()
        minuteLoggingJob?.cancel()
        gracePeriodJob?.cancel()
        continuousWatchdogJob?.cancel()
        blinkDetector.cancelBurst()
        unbindCamera()
        blinkAnalyzer?.release()
        blinkAnalyzer = null
        notificationHelper.cancelStatusNotification()

        val sid = currentSessionId
        if (sid != null && !isSessionEnded) {
            serviceScope.launch(Dispatchers.IO) {
                BlinkWellApp.repository.endSession(sid, alertCount)
                BlinkWellApp.settingsRepository.setActiveSessionId(null)
                com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker.triggerOneTimeSync(this@BlinkMonitorService)
            }
        }
        if (!isSessionEnded) {
            blinkDetector.resetSession()
        }
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        stopMonitoring()
        screenReceiver.unregister(this)
        cameraExecutor?.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
