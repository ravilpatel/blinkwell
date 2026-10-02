package com.mitalipurohit.blinkwell.detection

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque

class BlinkDetector(
    private var alertThresholdBpm: Int = 10,
    private val alertCooldownMs: Long = 10 * 60 * 1000L // 10 minutes
) {

    companion object {
        const val EYE_CLOSED_THRESHOLD = 0.40f
        const val EYE_OPEN_THRESHOLD = 0.60f
        const val MIN_BLINK_DURATION_MS = 60L
        const val MAX_BLINK_DURATION_MS = 600L
        const val ROLLING_WINDOW_MS = 60 * 1000L // 60 seconds
        const val ALERT_SUSTAINED_DURATION_MS = 2 * 60 * 1000L // 2 consecutive minutes
    }

    private val _metrics = MutableStateFlow(BlinkMetrics())
    val metrics: StateFlow<BlinkMetrics> = _metrics.asStateFlow()

    private val _blinkEvents = MutableSharedFlow<Long>(extraBufferCapacity = 10)
    val blinkEvents: SharedFlow<Long> = _blinkEvents.asSharedFlow()

    private val _alertEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val alertEvents: SharedFlow<String> = _alertEvents.asSharedFlow()

    private var eyeState: EyeState = EyeState.OPEN
    private var eyeClosedTimestamp: Long = 0L
    private val blinkTimestamps = ConcurrentLinkedDeque<Long>()

    private var totalBlinksCount = 0
    private var lastAlertTimestamp: Long = 0L
    private var lowBpmStartTimestamp: Long? = null
    private var lastFaceSeenTimestamp: Long = 0L

    fun setThreshold(thresholdBpm: Int) {
        this.alertThresholdBpm = thresholdBpm
    }

    @Synchronized
    fun onFrameProcessed(
        faceDetected: Boolean,
        leftEyeProb: Float?,
        rightEyeProb: Float?,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (!faceDetected || leftEyeProb == null || rightEyeProb == null) {
            // Pause counting if no face is present rather than registering false zeros
            _metrics.value = _metrics.value.copy(
                isFaceDetected = false
            )
            return
        }

        lastFaceSeenTimestamp = timestamp
        val eyeOpenScore = (leftEyeProb + rightEyeProb) / 2.0f

        // State Machine for Eye Blink Detection
        when (eyeState) {
            EyeState.OPEN, EyeState.OPENING -> {
                if (eyeOpenScore < EYE_CLOSED_THRESHOLD) {
                    eyeState = EyeState.CLOSED
                    eyeClosedTimestamp = timestamp
                }
            }
            EyeState.CLOSED, EyeState.CLOSING -> {
                if (eyeOpenScore > EYE_OPEN_THRESHOLD) {
                    val duration = timestamp - eyeClosedTimestamp
                    if (duration in MIN_BLINK_DURATION_MS..MAX_BLINK_DURATION_MS) {
                        registerBlink(timestamp)
                    }
                    eyeState = EyeState.OPEN
                }
            }
        }

        // Clean up timestamps outside the rolling 60-second window
        val windowStart = timestamp - ROLLING_WINDOW_MS
        while (!blinkTimestamps.isEmpty() && (blinkTimestamps.peekFirst() ?: Long.MAX_VALUE) < windowStart) {
            blinkTimestamps.pollFirst()
        }

        // Calculate rolling BPM
        val currentBpm = blinkTimestamps.size.toDouble()

        // Check Low-BPM Alert Condition
        checkAlertCondition(currentBpm, timestamp)

        _metrics.value = BlinkMetrics(
            currentBpm = currentBpm,
            totalBlinksInSession = totalBlinksCount,
            isFaceDetected = true,
            lastEyeOpenScore = eyeOpenScore,
            isSamplingActive = true
        )
    }

    private fun registerBlink(timestamp: Long) {
        blinkTimestamps.addLast(timestamp)
        totalBlinksCount++
        _blinkEvents.tryEmit(timestamp)
    }

    private fun checkAlertCondition(currentBpm: Double, timestamp: Long) {
        if (currentBpm < alertThresholdBpm) {
            if (lowBpmStartTimestamp == null) {
                lowBpmStartTimestamp = timestamp
            } else {
                val sustainedDuration = timestamp - (lowBpmStartTimestamp ?: timestamp)
                val cooldownPassed = (timestamp - lastAlertTimestamp) >= alertCooldownMs

                if (sustainedDuration >= ALERT_SUSTAINED_DURATION_MS && cooldownPassed) {
                    lastAlertTimestamp = timestamp
                    lowBpmStartTimestamp = null
                    _alertEvents.tryEmit("Low blink rate detected ($currentBpm BPM)")
                }
            }
        } else {
            lowBpmStartTimestamp = null
        }
    }

    fun resetSession() {
        blinkTimestamps.clear()
        totalBlinksCount = 0
        lowBpmStartTimestamp = null
        eyeState = EyeState.OPEN
        _metrics.value = BlinkMetrics()
    }
}
