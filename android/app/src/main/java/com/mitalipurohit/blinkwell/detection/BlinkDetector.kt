package com.mitalipurohit.blinkwell.detection

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque

class BlinkDetector(
    private var alertThresholdBpm: Int = 13,
    private val alertCooldownMs: Long = 10 * 60 * 1000L // 10 minutes
) {

    companion object {
        const val EYE_CLOSED_THRESHOLD = 0.40f
        const val EYE_OPEN_THRESHOLD = 0.60f
        const val MIN_BLINK_DURATION_MS = 60L
        const val MAX_BLINK_DURATION_MS = 600L
        const val WARMUP_PERIOD_MS = 30_000L // 30 seconds initial warmup
        const val ROLLING_WINDOW_MS = 60_000L // 60 seconds rolling window
        const val ALERT_SUSTAINED_DURATION_MS = 2 * 60 * 1000L // 2 consecutive minutes
        const val FACE_LOST_GRACE_PERIOD_MS = 10_000L // 10 seconds grace period before Yellow
    }

    private val _metrics = MutableStateFlow(BlinkMetrics())
    val metrics: StateFlow<BlinkMetrics> = _metrics.asStateFlow()

    private val _blinkEvents = MutableSharedFlow<Long>(extraBufferCapacity = 10)
    val blinkEvents: SharedFlow<Long> = _blinkEvents.asSharedFlow()

    private val _alertEvents = MutableSharedFlow<String>(extraBufferCapacity = 5)
    val alertEvents: SharedFlow<String> = _alertEvents.asSharedFlow()

    private val _greenToRedTransitions = MutableSharedFlow<Double>(extraBufferCapacity = 5)
    val greenToRedTransitions: SharedFlow<Double> = _greenToRedTransitions.asSharedFlow()

    private var eyeState: EyeState = EyeState.OPEN
    private var eyeClosedTimestamp: Long = 0L
    private val blinkTimestamps = ConcurrentLinkedDeque<Long>()

    private var sessionStartTimestamp: Long = 0L
    private var totalBlinksCount = 0
    private var lastAlertTimestamp: Long = 0L
    private var lowBpmStartTimestamp: Long? = null
    private var lastFaceSeenTimestamp: Long = 0L
    private var isSamplingActive: Boolean = true
    private var previousCategory: BlinkStatusCategory? = null

    fun setThreshold(thresholdBpm: Int) {
        this.alertThresholdBpm = thresholdBpm
    }

    fun setSamplingActive(active: Boolean) {
        this.isSamplingActive = active
        if (active) {
            lastFaceSeenTimestamp = System.currentTimeMillis()
        }
        _metrics.value = _metrics.value.copy(isSamplingActive = active)
    }

    @Synchronized
    fun onFrameProcessed(
        faceDetected: Boolean,
        leftEyeProb: Float?,
        rightEyeProb: Float?,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (sessionStartTimestamp == 0L) {
            sessionStartTimestamp = timestamp
        }

        val hasFaceAndEyes = faceDetected && leftEyeProb != null && rightEyeProb != null

        if (hasFaceAndEyes) {
            lastFaceSeenTimestamp = timestamp
        }

        val elapsedSinceStart = timestamp - sessionStartTimestamp
        val isWarmedUp = elapsedSinceStart >= WARMUP_PERIOD_MS

        // Clean up timestamps outside the rolling 60-second window
        val windowStart = if (elapsedSinceStart >= ROLLING_WINDOW_MS) {
            timestamp - ROLLING_WINDOW_MS
        } else {
            sessionStartTimestamp
        }

        while (!blinkTimestamps.isEmpty() && (blinkTimestamps.peekFirst() ?: Long.MAX_VALUE) < windowStart) {
            blinkTimestamps.pollFirst()
        }

        // Calculate rate: normalized to 60s if between 30s and 60s, or direct rolling size once >= 60s
        val currentBpm: Double = when {
            elapsedSinceStart < WARMUP_PERIOD_MS -> {
                blinkTimestamps.size.toDouble()
            }
            elapsedSinceStart < ROLLING_WINDOW_MS -> {
                val effectiveSeconds = (elapsedSinceStart / 1000.0).coerceAtLeast(1.0)
                (blinkTimestamps.size * 60.0) / effectiveSeconds
            }
            else -> {
                blinkTimestamps.size.toDouble()
            }
        }

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

            // Check Low-BPM Alert Condition (once warmed up)
            if (isWarmedUp) {
                checkAlertCondition(currentBpm, timestamp)
            }
        }

        // Determine status category
        val isGracePeriodActive = lastFaceSeenTimestamp > 0L && (timestamp - lastFaceSeenTimestamp <= FACE_LOST_GRACE_PERIOD_MS)
        val isFacePresentOrInGrace = hasFaceAndEyes || isGracePeriodActive

        val statusCategory = when {
            !isFacePresentOrInGrace -> BlinkStatusCategory.FACE_NOT_DETECTED
            currentBpm < alertThresholdBpm -> BlinkStatusCategory.LOW_RATE
            else -> BlinkStatusCategory.NORMAL
        }

        // Check Green -> Red transition to fire immediate visible notification
        if (isWarmedUp) {
            if (previousCategory == BlinkStatusCategory.NORMAL && statusCategory == BlinkStatusCategory.LOW_RATE) {
                _greenToRedTransitions.tryEmit(currentBpm)
            }
            previousCategory = statusCategory
        }

        _metrics.value = BlinkMetrics(
            currentBpm = currentBpm,
            totalBlinksInSession = totalBlinksCount,
            isFaceDetected = hasFaceAndEyes,
            lastEyeOpenScore = if (hasFaceAndEyes) ((leftEyeProb!! + rightEyeProb!!) / 2.0f) else _metrics.value.lastEyeOpenScore,
            isSamplingActive = isSamplingActive,
            isWarmedUp = isWarmedUp,
            warmupSecondsElapsed = (elapsedSinceStart / 1000L).coerceAtMost(30L),
            statusCategory = statusCategory
        )
    }

    /**
     * Periodic check to update warmup state, normalized BPM, and handle 10s grace period.
     * When camera is paused in Duty-Cycle Burst mode (isSamplingActive == false),
     * it does NOT switch to Yellow and remembers the last rate measured.
     */
    @Synchronized
    fun checkGracePeriod(timestamp: Long = System.currentTimeMillis()) {
        if (sessionStartTimestamp == 0L) return

        val elapsedSinceStart = timestamp - sessionStartTimestamp
        val isWarmedUp = elapsedSinceStart >= WARMUP_PERIOD_MS

        // If camera is paused for duty cycle burst, remember last measured rate and category
        if (!isSamplingActive) {
            if (_metrics.value.isWarmedUp != isWarmedUp) {
                _metrics.value = _metrics.value.copy(
                    isWarmedUp = isWarmedUp,
                    warmupSecondsElapsed = (elapsedSinceStart / 1000L).coerceAtMost(30L)
                )
            }
            return
        }

        val windowStart = if (elapsedSinceStart >= ROLLING_WINDOW_MS) {
            timestamp - ROLLING_WINDOW_MS
        } else {
            sessionStartTimestamp
        }

        while (!blinkTimestamps.isEmpty() && (blinkTimestamps.peekFirst() ?: Long.MAX_VALUE) < windowStart) {
            blinkTimestamps.pollFirst()
        }

        val currentBpm: Double = when {
            elapsedSinceStart < WARMUP_PERIOD_MS -> {
                blinkTimestamps.size.toDouble()
            }
            elapsedSinceStart < ROLLING_WINDOW_MS -> {
                val effectiveSeconds = (elapsedSinceStart / 1000.0).coerceAtLeast(1.0)
                (blinkTimestamps.size * 60.0) / effectiveSeconds
            }
            else -> {
                blinkTimestamps.size.toDouble()
            }
        }

        val isGracePeriodActive = lastFaceSeenTimestamp > 0L && (timestamp - lastFaceSeenTimestamp <= FACE_LOST_GRACE_PERIOD_MS)

        val statusCategory = when {
            !isGracePeriodActive -> BlinkStatusCategory.FACE_NOT_DETECTED
            currentBpm < alertThresholdBpm -> BlinkStatusCategory.LOW_RATE
            else -> BlinkStatusCategory.NORMAL
        }

        if (isWarmedUp) {
            if (previousCategory == BlinkStatusCategory.NORMAL && statusCategory == BlinkStatusCategory.LOW_RATE) {
                _greenToRedTransitions.tryEmit(currentBpm)
            }
            previousCategory = statusCategory
        }

        _metrics.value = _metrics.value.copy(
            currentBpm = currentBpm,
            isFaceDetected = isGracePeriodActive,
            isWarmedUp = isWarmedUp,
            warmupSecondsElapsed = (elapsedSinceStart / 1000L).coerceAtMost(30L),
            statusCategory = statusCategory
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
                    _alertEvents.tryEmit("Low blink rate detected (${currentBpm.toInt()} BPM)")
                }
            }
        } else {
            lowBpmStartTimestamp = null
        }
    }

    fun resetSession() {
        blinkTimestamps.clear()
        sessionStartTimestamp = 0L
        totalBlinksCount = 0
        lowBpmStartTimestamp = null
        lastFaceSeenTimestamp = 0L
        eyeState = EyeState.OPEN
        isSamplingActive = true
        previousCategory = null
        _metrics.value = BlinkMetrics(statusCategory = BlinkStatusCategory.NORMAL)
    }
}
