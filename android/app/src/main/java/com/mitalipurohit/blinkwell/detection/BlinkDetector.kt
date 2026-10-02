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
        const val FACE_LOST_GRACE_PERIOD_MS = 10_000L // 10 seconds grace period before Yellow
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
        val hasFaceAndEyes = faceDetected && leftEyeProb != null && rightEyeProb != null

        if (hasFaceAndEyes) {
            lastFaceSeenTimestamp = timestamp
        }

        // Clean up timestamps outside the rolling 60-second window
        val windowStart = timestamp - ROLLING_WINDOW_MS
        while (!blinkTimestamps.isEmpty() && (blinkTimestamps.peekFirst() ?: Long.MAX_VALUE) < windowStart) {
            blinkTimestamps.pollFirst()
        }

        // Calculate rolling BPM
        val currentBpm = blinkTimestamps.size.toDouble()

        if (hasFaceAndEyes) {
            val eyeOpenScore = (leftEyeProb!! + rightEyeProb!!) / 2.0f

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

            // Check Low-BPM Alert Condition
            checkAlertCondition(currentBpm, timestamp)
        }

        // Determine status category respecting 10-second grace period
        val isGracePeriodActive = lastFaceSeenTimestamp > 0L && (timestamp - lastFaceSeenTimestamp <= FACE_LOST_GRACE_PERIOD_MS)
        val isFacePresentOrInGrace = hasFaceAndEyes || isGracePeriodActive

        val statusCategory = when {
            !isFacePresentOrInGrace -> BlinkStatusCategory.FACE_NOT_DETECTED
            currentBpm < alertThresholdBpm -> BlinkStatusCategory.LOW_RATE
            else -> BlinkStatusCategory.NORMAL
        }

        _metrics.value = BlinkMetrics(
            currentBpm = currentBpm,
            totalBlinksInSession = totalBlinksCount,
            isFaceDetected = hasFaceAndEyes,
            lastEyeOpenScore = if (hasFaceAndEyes) ((leftEyeProb!! + rightEyeProb!!) / 2.0f) else _metrics.value.lastEyeOpenScore,
            isSamplingActive = true,
            statusCategory = statusCategory
        )
    }

    /**
     * Periodic check to transition to FACE_NOT_DETECTED (Yellow) after 10-second grace period
     * even if camera frames stop arriving or face is not in frame.
     */
    @Synchronized
    fun checkGracePeriod(timestamp: Long = System.currentTimeMillis()) {
        val windowStart = timestamp - ROLLING_WINDOW_MS
        while (!blinkTimestamps.isEmpty() && (blinkTimestamps.peekFirst() ?: Long.MAX_VALUE) < windowStart) {
            blinkTimestamps.pollFirst()
        }
        val currentBpm = blinkTimestamps.size.toDouble()

        val isGracePeriodActive = lastFaceSeenTimestamp > 0L && (timestamp - lastFaceSeenTimestamp <= FACE_LOST_GRACE_PERIOD_MS)

        if (!isGracePeriodActive && _metrics.value.statusCategory != BlinkStatusCategory.FACE_NOT_DETECTED) {
            _metrics.value = _metrics.value.copy(
                currentBpm = currentBpm,
                isFaceDetected = false,
                statusCategory = BlinkStatusCategory.FACE_NOT_DETECTED
            )
        } else if (isGracePeriodActive) {
            val statusCategory = if (currentBpm < alertThresholdBpm) {
                BlinkStatusCategory.LOW_RATE
            } else {
                BlinkStatusCategory.NORMAL
            }
            if (_metrics.value.statusCategory != statusCategory || _metrics.value.currentBpm != currentBpm) {
                _metrics.value = _metrics.value.copy(
                    currentBpm = currentBpm,
                    statusCategory = statusCategory
                )
            }
        }
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
        lastFaceSeenTimestamp = 0L
        eyeState = EyeState.OPEN
        _metrics.value = BlinkMetrics(statusCategory = BlinkStatusCategory.FACE_NOT_DETECTED)
    }
}
