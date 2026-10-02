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
    private var isScreenOn = true
    private var isCameraBound = false
    private var alertCount = 0

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        notificationHelper = NotificationHelper(this)
        blinkDetector = BlinkWellApp.blinkDetector
        cameraExecutor = Executors.newSingleThreadExecutor()

        screenReceiver = ScreenReceiver(
            onScreenOn = { handleScreenOn() },
            onScreenOff = { handleScreenOff() }
        )
        screenReceiver.register(this)

        startForegroundNotification()
        observeAlertsAndSettings()
    }

    private fun startForegroundNotification() {
        val notification = notificationHelper.buildServiceNotification()
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

            currentSessionId = repository.createSession(mode = "background")
            settingsRepo.setActiveSessionId(currentSessionId)

            setupDutyCycleOrContinuous()
            startMinuteLogging()
        }
    }

    private fun setupDutyCycleOrContinuous() {
        dutyCycleJob?.cancel()
        dutyCycleJob = serviceScope.launch {
            val settingsRepo = BlinkWellApp.settingsRepository
            val samplingMode = settingsRepo.samplingMode.first()

            if (samplingMode == "duty_cycle") {
                // Duty cycle: Sample for 45s, unbind for 120s
                while (isActive && isRunning) {
                    if (isScreenOn) {
                        bindCamera()
                        delay(45_000L)
                        unbindCamera()
                    }
                    delay(120_000L)
                }
            } else {
                // Continuous mode while screen is on
                if (isScreenOn) {
                    bindCamera()
                }
            }
        }
    }

    private fun bindCamera() {
        if (isCameraBound || !isScreenOn) return

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
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

    private fun handleScreenOn() {
        isScreenOn = true
        setupDutyCycleOrContinuous()
    }

    private fun handleScreenOff() {
        isScreenOn = false
        // Hard requirement: Never analyze with screen off - unbind immediately
        unbindCamera()
    }

    private fun startMinuteLogging() {
        minuteLoggingJob?.cancel()
        minuteLoggingJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                delay(60_000L)
                val sid = currentSessionId
                if (sid != null && isScreenOn) {
                    val currentBpm = blinkDetector.metrics.value.currentBpm
                    BlinkWellApp.repository.logMinuteBpm(sid, currentBpm)
                }
            }
        }
    }

    private fun observeAlertsAndSettings() {
        serviceScope.launch {
            blinkDetector.alertEvents.collect { message ->
                val alertsEnabled = BlinkWellApp.settingsRepository.alertsEnabled.first()
                if (alertsEnabled) {
                    alertCount++
                    notificationHelper.showAlertNotification()
                }
            }
        }
    }

    private fun stopMonitoring() {
        isRunning = false
        dutyCycleJob?.cancel()
        minuteLoggingJob?.cancel()
        unbindCamera()
        blinkAnalyzer?.release()
        blinkAnalyzer = null

        val sid = currentSessionId
        if (sid != null) {
            serviceScope.launch(Dispatchers.IO) {
                val metrics = blinkDetector.metrics.value
                BlinkWellApp.repository.endSession(sid, alertCount)
                BlinkWellApp.settingsRepository.setActiveSessionId(null)
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
