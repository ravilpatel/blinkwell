package com.mitalipurohit.blinkwell.detection

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque

data class BurstResult(
    val faceDetected: Boolean,
    val bpm: Double,
    val blinkCount: Int,
    val isLowRate: Boolean
)

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

    private val _burstCompletionEvents = MutableSharedFlow<BurstSessionResult>(extraBufferCapacity = 5)
    val burstCompletionEvents: SharedFlow<BurstSessionResult> = _burstCompletionEvents.asSharedFlow()

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

    // 5-minute Burst Mode state
    private var is5MinBurstActive: Boolean = false
    private var burstElapsedSecondsCount: Long = 0L

    // Duty-cycled burst scanning state for Monitoring Mode Duty Cycling
    private var isBurstActive: Boolean = false
    private var burstStartTimestamp: Long = 0L
    private var burstBlinkCount: Int = 0
    private var burstHasFace: Boolean = false
    private var lastBurstCompletedBpm: Double = 0.0

    fun setThreshold(thresholdBpm: Int) {
        this.alertThresholdBpm = thresholdBpm
    }

    fun setSamplingActive(active: Boolean) {
        this.isSamplingActive = active
        if (active) {
            lastFaceSeenTimestamp = System.currentTimeMillis()
            if (sessionStartTimestamp == 0L) {
                sessionStartTimestamp = System.currentTimeMillis()
            }
            _metrics.value = _metrics.value.copy(
                isSamplingActive = true,
                isWarmedUp = false,
                warmupSecondsElapsed = 0L
            )
        } else {
            _metrics.value = _metrics.value.copy(isSamplingActive = false)
        }
    }

    /**
     * Starts the dedicated 5-minute Burst Mode assessment session.
     */
    @Synchronized
    fun start5MinBurst(timestamp: Long = System.currentTimeMillis()) {
        is5MinBurstActive = true
        isBurstActive = false
        burstStartTimestamp = timestamp
        burstBlinkCount = 0
        burstElapsedSecondsCount = 0L
        burstHasFace = false
        lastFaceSeenTimestamp = timestamp
        eyeState = EyeState.OPEN
        isSamplingActive = true
        _metrics.value = BlinkMetrics(
            currentBpm = 0.0,
            totalBlinksInSession = 0,
            isFaceDetected = true,
            isSamplingActive = true,
            isWarmedUp = true,
            warmupSecondsElapsed = 0L,
            statusCategory = BlinkStatusCategory.NORMAL,
            burstElapsedSeconds = 0L,
            burstTotalSeconds = 300L
        )
    }

    /**
     * Updates elapsed seconds in the 5-minute burst assessment.
     */
    @Synchronized
    fun updateBurstElapsedSeconds(elapsedSeconds: Long) {
        burstElapsedSecondsCount = elapsedSeconds
        val effectiveSeconds = elapsedSeconds.coerceAtLeast(1L).toDouble()
        val currentBpm = (burstBlinkCount * 60.0) / effectiveSeconds
        _metrics.value = _metrics.value.copy(
            currentBpm = currentBpm,
            burstElapsedSeconds = elapsedSeconds,
            totalBlinksInSession = burstBlinkCount
        )
    }

    /**
     * Completes the 5-minute burst assessment and emits the final result.
     */
    @Synchronized
    fun finish5MinBurst(durationSeconds: Double = 300.0): BurstSessionResult {
        is5MinBurstActive = false
        isSamplingActive = false

        val finalBpm = (burstBlinkCount * 60.0) / durationSeconds
        val isLowRate = finalBpm < alertThresholdBpm
        val statusCategory = if (isLowRate) BlinkStatusCategory.LOW_RATE else BlinkStatusCategory.NORMAL

        val result = BurstSessionResult(
            finalBpm = finalBpm,
            totalBlinks = burstBlinkCount,
            durationSeconds = durationSeconds.toLong(),
            statusCategory = statusCategory,
            completedTimestamp = System.currentTimeMillis()
        )

        _metrics.value = _metrics.value.copy(
            currentBpm = finalBpm,
            totalBlinksInSession = burstBlinkCount,
            isSamplingActive = false,
            burstElapsedSeconds = durationSeconds.toLong(),
            statusCategory = statusCategory
        )

        _burstCompletionEvents.tryEmit(result)
        return result
    }

    /**
     * Starts a dedicated burst scan in background mode.
     * Resets burst counters and avoids triggering premature 0-BPM alerts.
     */
    @Synchronized
    fun startBurstScan(timestamp: Long = System.currentTimeMillis()) {
        isBurstActive = true
        burstStartTimestamp = timestamp
        burstBlinkCount = 0
        burstHasFace = false
        lastFaceSeenTimestamp = timestamp
        eyeState = EyeState.OPEN
        isSamplingActive = true
        _metrics.value = _metrics.value.copy(
            isSamplingActive = true,
            isFaceDetected = true,
            warmupSecondsElapsed = 0L
        )
    }

    /**
     * Completes the burst scan:
     * - Converts the burst blink count into BPM over the elapsed duration.
     * - Updates metrics and status category.
     * - Returns BurstResult for alert evaluation.
     */
    @Synchronized
    fun finishBurstScan(scanDurationSeconds: Double = 20.0): BurstResult {
        isBurstActive = false
        isSamplingActive = false

        if (!burstHasFace) {
            val statusCategory = BlinkStatusCategory.FACE_NOT_DETECTED
            previousCategory = statusCategory
            _metrics.value = _metrics.value.copy(
                isSamplingActive = false,
                isFaceDetected = false,
                statusCategory = statusCategory
            )
            return BurstResult(
                faceDetected = false,
                bpm = 0.0,
                blinkCount = 0,
                isLowRate = false
            )
        }

        val elapsedSeconds = if (burstStartTimestamp > 0L) {
            ((System.currentTimeMillis() - burstStartTimestamp) / 1000.0).coerceIn(5.0, 60.0)
        } else {
            scanDurationSeconds
        }

        val computedBpm = (burstBlinkCount * 60.0) / elapsedSeconds
        lastBurstCompletedBpm = computedBpm
        val isLowRate = computedBpm < alertThresholdBpm
        val statusCategory = if (isLowRate) BlinkStatusCategory.LOW_RATE else BlinkStatusCategory.NORMAL
        previousCategory = statusCategory

        _metrics.value = _metrics.value.copy(
            currentBpm = computedBpm,
            totalBlinksInSession = totalBlinksCount,
            isFaceDetected = true,
            isSamplingActive = false,
            isWarmedUp = true,
            warmupSecondsElapsed = elapsedSeconds.toLong(),
            statusCategory = statusCategory
        )

        return BurstResult(
            faceDetected = true,
            bpm = computedBpm,
            blinkCount = burstBlinkCount,
            isLowRate = isLowRate
        )
    }

    /**
     * Cancels an in-progress burst scan (e.g. when screen turns off mid-scan).
     */
    @Synchronized
    fun cancelBurst() {
        isBurstActive = false
        isSamplingActive = false
        _metrics.value = _metrics.value.copy(
            isSamplingActive = false
        )
    }

    /**
     * Checks if a face was detected during the active burst scan (e.g. within first 5 seconds).
     */
    @Synchronized
    fun hasSeenFaceInBurst(): Boolean = burstHasFace

    /**
     * Checks if a face was seen recently within the specified window in milliseconds.
     */
    @Synchronized
    fun hasSeenFaceRecently(windowMs: Long = 5000L, now: Long = System.currentTimeMillis()): Boolean {
        return lastFaceSeenTimestamp > 0L && (now - lastFaceSeenTimestamp <= windowMs)
    }

    @Synchronized
    fun getLastFaceSeenTimestamp(): Long = lastFaceSeenTimestamp

    /**
     * Explicitly marks face as lost / not detected and updates metrics.
     */
    @Synchronized
    fun markFaceLost() {
        burstHasFace = false
        previousCategory = BlinkStatusCategory.FACE_NOT_DETECTED
        _metrics.value = _metrics.value.copy(
            isFaceDetected = false,
            statusCategory = BlinkStatusCategory.FACE_NOT_DETECTED
        )
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

        // --- 5-MINUTE BURST MODE HANDLING (Silent, continuous measurement) ---
        if (is5MinBurstActive) {
            if (hasFaceAndEyes) {
                burstHasFace = true
                val eyeOpenScore = (leftEyeProb!! + rightEyeProb!!) / 2.0f

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
                                burstBlinkCount++
                                totalBlinksCount++
                                _blinkEvents.tryEmit(timestamp)
                            }
                            eyeState = EyeState.OPEN
                        }
                    }
                }
            }

            val isGracePeriodActive = lastFaceSeenTimestamp > 0L && (timestamp - lastFaceSeenTimestamp <= FACE_LOST_GRACE_PERIOD_MS)
            val isFacePresentOrInGrace = hasFaceAndEyes || isGracePeriodActive
            val effectiveSeconds = burstElapsedSecondsCount.coerceAtLeast(1L).toDouble()
            val liveBpm = (burstBlinkCount * 60.0) / effectiveSeconds

            _metrics.value = BlinkMetrics(
                currentBpm = liveBpm,
                totalBlinksInSession = burstBlinkCount,
                isFaceDetected = isFacePresentOrInGrace,
                lastEyeOpenScore = if (hasFaceAndEyes) ((leftEyeProb!! + rightEyeProb!!) / 2.0f) else _metrics.value.lastEyeOpenScore,
                isSamplingActive = true,
                isWarmedUp = true,
                warmupSecondsElapsed = burstElapsedSecondsCount,
                statusCategory = if (isFacePresentOrInGrace) BlinkStatusCategory.NORMAL else BlinkStatusCategory.FACE_NOT_DETECTED,
                burstElapsedSeconds = burstElapsedSecondsCount,
                burstTotalSeconds = 300L
            )
            return
        }

        // --- MONITORING DUTY-CYCLE BURST HANDLING ---
        if (isBurstActive) {
            if (hasFaceAndEyes) {
                burstHasFace = true
                val eyeOpenScore = (leftEyeProb!! + rightEyeProb!!) / 2.0f

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
                                burstBlinkCount++
                                totalBlinksCount++
                                _blinkEvents.tryEmit(timestamp)
                            }
                            eyeState = EyeState.OPEN
                        }
                    }
                }
            }

            val isGracePeriodActive = lastFaceSeenTimestamp > 0L && (timestamp - lastFaceSeenTimestamp <= FACE_LOST_GRACE_PERIOD_MS)
            val isFacePresentOrInGrace = hasFaceAndEyes || isGracePeriodActive

            val elapsedSeconds = if (burstStartTimestamp > 0L) {
                ((timestamp - burstStartTimestamp) / 1000.0).coerceAtLeast(1.0)
            } else 1.0

            val liveBpm = (burstBlinkCount * 60.0) / elapsedSeconds

            val statusCategory = when {
                !isFacePresentOrInGrace -> BlinkStatusCategory.FACE_NOT_DETECTED
                elapsedSeconds < 3.0 && lastBurstCompletedBpm > 0.0 -> {
                    if (lastBurstCompletedBpm < alertThresholdBpm) BlinkStatusCategory.LOW_RATE else BlinkStatusCategory.NORMAL
                }
                liveBpm < alertThresholdBpm -> BlinkStatusCategory.LOW_RATE
                else -> BlinkStatusCategory.NORMAL
            }

            _metrics.value = BlinkMetrics(
                currentBpm = liveBpm,
                totalBlinksInSession = totalBlinksCount,
                isFaceDetected = hasFaceAndEyes,
                lastEyeOpenScore = if (hasFaceAndEyes) ((leftEyeProb!! + rightEyeProb!!) / 2.0f) else _metrics.value.lastEyeOpenScore,
                isSamplingActive = true,
                isWarmedUp = true,
                warmupSecondsElapsed = elapsedSeconds.toLong(),
                statusCategory = statusCategory
            )
            return
        }

        // --- CONTINUOUS MODE HANDLING ---
        if (sessionStartTimestamp == 0L) {
            sessionStartTimestamp = timestamp
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
            !isWarmedUp -> BlinkStatusCategory.NORMAL
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
     * In burst mode or when sampling is paused, skips rolling window modification.
     */
    @Synchronized
    fun checkGracePeriod(timestamp: Long = System.currentTimeMillis()) {
        if (is5MinBurstActive || isBurstActive || !isSamplingActive) {
            return
        }

        if (sessionStartTimestamp == 0L) return

        val elapsedSinceStart = timestamp - sessionStartTimestamp
        val isWarmedUp = elapsedSinceStart >= WARMUP_PERIOD_MS

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
            !isWarmedUp -> BlinkStatusCategory.NORMAL
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

    @Synchronized
    fun resetSession() {
        blinkTimestamps.clear()
        sessionStartTimestamp = 0L
        totalBlinksCount = 0
        lowBpmStartTimestamp = null
        lastFaceSeenTimestamp = 0L
        eyeState = EyeState.OPEN
        isSamplingActive = true
        is5MinBurstActive = false
        burstElapsedSecondsCount = 0L
        isBurstActive = false
        burstStartTimestamp = 0L
        burstBlinkCount = 0
        burstHasFace = false
        lastBurstCompletedBpm = 0.0
        previousCategory = null
        _metrics.value = BlinkMetrics(statusCategory = BlinkStatusCategory.NORMAL)
    }
}
