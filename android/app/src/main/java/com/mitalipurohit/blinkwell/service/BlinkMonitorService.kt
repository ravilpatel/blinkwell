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

        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, BlinkMonitorService::class.java).apply {
                action = ACTION_START
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
    private var dutyCycleJob: Job? = null
    private var minuteLoggingJob: Job? = null
    private var gracePeriodJob: Job? = null
    private var isScreenUnlockedAndActive = true
    private var isCameraBound = false
    private var alertCount = 0

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
        val initialMetrics = blinkDetector.metrics.value
        val notification = notificationHelper.buildStatusNotification(
            category = initialMetrics.statusCategory,
            bpm = initialMetrics.currentBpm
        )
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
                startMonitoring()
            }
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        serviceScope.launch {
            val repository = BlinkWellApp.repository
            val settingsRepo = BlinkWellApp.settingsRepository
            val threshold = settingsRepo.bpmThreshold.first()
            blinkDetector.setThreshold(threshold)

            // Prune any data older than 30 days locally
            repository.pruneDataOlderThan30Days()

            currentSessionId = repository.createSession(mode = "background")
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
        if (isScreenUnlockedAndActive) {
            bindCamera()
            blinkDetector.setSamplingActive(true)
        }
        startMinuteLogging()
        startGracePeriodTicker()
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

        dutyCycleJob = serviceScope.launch {
            var isLastReadingLow = false

            // Adaptive Duty-Cycled Scanning:
            // Standard: Scan for 20s, convert to BPM.
            // If normal: wait usual period (120s / 2 minutes).
            // If low: send alert notification -> wait 10s -> scan 20s again -> convert to BPM.
            // If recovered to normal: wait usual period (120s). If still not normal: scan after 10s again.
            while (isActive && isRunning) {
                if (isScreenUnlockedAndActive) {
                    val scanDurationMs = 20_000L
                    val scanDurationSeconds = 20.0

                    // 1. Bind Camera and Start 20s burst scan
                    bindCamera()
                    blinkDetector.startBurstScan()

                    // 2. Sample for 20 seconds (sticky notification updates in real time via metrics collector)
                    delay(scanDurationMs)

                    if (isScreenUnlockedAndActive && isRunning) {
                        // 3. Complete the 20-second scan and convert to BPM
                        val burstResult = blinkDetector.finishBurstScan(scanDurationSeconds = scanDurationSeconds)
                        unbindCamera()

                        val threshold = BlinkWellApp.settingsRepository.bpmThreshold.first()

                        if (burstResult.faceDetected) {
                            val bpm = burstResult.bpm

                            // Log completed burst reading to database
                            currentSessionId?.let { sid ->
                                BlinkWellApp.repository.logMinuteBpm(sid, bpm)
                                com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker.triggerOneTimeSync(this@BlinkMonitorService)
                            }

                            // Update ongoing status notification with the new BPM
                            val category = if (burstResult.isLowRate) BlinkStatusCategory.LOW_RATE else BlinkStatusCategory.NORMAL
                            notificationHelper.updateStatusNotification(
                                category = category,
                                bpm = bpm,
                                thresholdBpm = threshold,
                                isWarmedUp = true,
                                force = true
                            )

                            // If blink rate is lower: immediately send notification, then scan after 10s for 20s
                            if (burstResult.isLowRate) {
                                isLastReadingLow = true
                                val alertsEnabled = BlinkWellApp.settingsRepository.alertsEnabled.first()
                                if (alertsEnabled) {
                                    alertCount++
                                    notificationHelper.showGreenToRedAlertNotification(bpm, threshold)
                                }

                                // Immediately after notification is sent, pause 10s before the next 20s scan
                                delay(10_000L)
                            } else {
                                // If normal: perform next scan after usual period (120 seconds)
                                isLastReadingLow = false
                                delay(120_000L)
                            }
                        } else {
                            // Face not detected during the scan
                            notificationHelper.updateStatusNotification(
                                category = BlinkStatusCategory.FACE_NOT_DETECTED,
                                bpm = 0.0,
                                thresholdBpm = threshold,
                                isWarmedUp = true,
                                force = true
                            )
                            val pauseMs = if (isLastReadingLow) 10_000L else 120_000L
                            delay(pauseMs)
                        }
                    } else {
                        // Cancelled mid-burst (e.g. screen off)
                        blinkDetector.cancelBurst()
                        unbindCamera()
                    }
                } else {
                    delay(2000L)
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
                    .setTargetResolution(Size(320, 240))
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
                val samplingMode = BlinkWellApp.settingsRepository.samplingMode.first()
                if (samplingMode == "continuous") {
                    startContinuousMonitoring()
                } else {
                    setupDutyCycleBurst()
                }
            }
        }
    }

    private fun handleScreenOff() {
        isScreenUnlockedAndActive = false
        // Hard requirement: Never analyze with screen off or locked - unbind immediately
        dutyCycleJob?.cancel()
        minuteLoggingJob?.cancel()
        gracePeriodJob?.cancel()
        blinkDetector.cancelBurst()
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
        // Collect metrics and update dynamic sticky notification in real time whenever camera is ON or continuous mode
        serviceScope.launch {
            blinkDetector.metrics.collect { metrics ->
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

        // Collect continuous mode Green-to-Red movements for immediate visible alert
        serviceScope.launch {
            blinkDetector.greenToRedTransitions.collect { bpm ->
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

        // Collect continuous mode sustained low-blink alert events
        serviceScope.launch {
            blinkDetector.alertEvents.collect {
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

    private fun stopMonitoring() {
        isRunning = false
        dutyCycleJob?.cancel()
        minuteLoggingJob?.cancel()
        gracePeriodJob?.cancel()
        blinkDetector.cancelBurst()
        unbindCamera()
        blinkAnalyzer?.release()
        blinkAnalyzer = null
        notificationHelper.cancelStatusNotification()

        val sid = currentSessionId
        if (sid != null) {
            serviceScope.launch(Dispatchers.IO) {
                BlinkWellApp.repository.endSession(sid, alertCount)
                BlinkWellApp.settingsRepository.setActiveSessionId(null)
                com.mitalipurohit.blinkwell.data.remote.sync.BlinkSyncWorker.triggerOneTimeSync(this@BlinkMonitorService)
            }
        }
        blinkDetector.resetSession()
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
