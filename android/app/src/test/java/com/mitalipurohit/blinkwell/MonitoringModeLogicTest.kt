package com.mitalipurohit.blinkwell

import com.mitalipurohit.blinkwell.detection.BlinkDetector
import com.mitalipurohit.blinkwell.detection.BlinkStatusCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests validating the Monitoring Mode protocol:
 * 1. Initial scan: 1 minute (60s) continuous scan.
 * 2. BPM classification: Normal (Green notification) vs Low Rate (Red notification).
 * 3. Cooldown: 2 minutes (120s) cooling interval.
 * 4. Subsequent scans: Exactly 30 seconds only.
 */
class MonitoringModeLogicTest {

    private lateinit var blinkDetector: BlinkDetector

    @Before
    fun setup() {
        blinkDetector = BlinkDetector(alertThresholdBpm = 13)
        blinkDetector.resetSession()
    }

    @Test
    fun testMonitoringModeInitialScan_1Minute_HealthyRate() {
        val startTs = 1_000_000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Simulate 16 blinks across 60 seconds (16 BPM >= 13 threshold)
        for (i in 1..16) {
            val blinkTime = startTs + (i * 3500L)
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.1f,
                rightEyeProb = 0.1f,
                timestamp = blinkTime
            )
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.9f,
                rightEyeProb = 0.9f,
                timestamp = blinkTime + 180L
            )
        }

        // Finish 1-minute (60 seconds) initial scan
        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 60.0)

        assertTrue("Face should be detected", result.faceDetected)
        assertEquals("Should have recorded 16 blinks in 1 minute", 16, result.blinkCount)
        assertEquals("16 blinks in 60s = 16.0 BPM", 16.0, result.bpm, 0.5)
        assertFalse("16 BPM >= 13 is NOT low rate", result.isLowRate)
        assertEquals("Status category should be NORMAL (Green notification)", BlinkStatusCategory.NORMAL, blinkDetector.metrics.value.statusCategory)
    }

    @Test
    fun testMonitoringModeInitialScan_1Minute_LowRate() {
        val startTs = 1_000_000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Simulate 6 blinks across 60 seconds (6 BPM < 13 threshold)
        for (i in 1..6) {
            val blinkTime = startTs + (i * 9000L)
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.1f,
                rightEyeProb = 0.1f,
                timestamp = blinkTime
            )
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.9f,
                rightEyeProb = 0.9f,
                timestamp = blinkTime + 180L
            )
        }

        // Finish 1-minute (60 seconds) initial scan
        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 60.0)

        assertTrue("Face should be detected", result.faceDetected)
        assertEquals("Should have recorded 6 blinks in 1 minute", 6, result.blinkCount)
        assertEquals("6 blinks in 60s = 6.0 BPM", 6.0, result.bpm, 0.5)
        assertTrue("6 BPM < 13 is low rate", result.isLowRate)
        assertEquals("Status category should be LOW_RATE (Red notification)", BlinkStatusCategory.LOW_RATE, blinkDetector.metrics.value.statusCategory)
    }

    @Test
    fun testMonitoringModeSubsequentScan_30Seconds_HealthyRate() {
        val startTs = 2_000_000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Simulate 8 blinks across 30 seconds (8 * 2 = 16 BPM >= 13 threshold)
        for (i in 1..8) {
            val blinkTime = startTs + (i * 3500L)
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.1f,
                rightEyeProb = 0.1f,
                timestamp = blinkTime
            )
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.9f,
                rightEyeProb = 0.9f,
                timestamp = blinkTime + 180L
            )
        }

        // Finish 30-second subsequent scan
        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 30.0)

        assertTrue("Face should be detected", result.faceDetected)
        assertEquals("Should have recorded 8 blinks in 30 seconds", 8, result.blinkCount)
        assertEquals("8 blinks in 30s = 16.0 BPM", 16.0, result.bpm, 0.5)
        assertFalse("16 BPM >= 13 is NOT low rate", result.isLowRate)
        assertEquals("Status category should be NORMAL (Green notification)", BlinkStatusCategory.NORMAL, blinkDetector.metrics.value.statusCategory)
    }

    @Test
    fun testMonitoringModeSubsequentScan_30Seconds_LowRate() {
        val startTs = 2_000_000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Simulate 3 blinks across 30 seconds (3 * 2 = 6 BPM < 13 threshold)
        for (i in 1..3) {
            val blinkTime = startTs + (i * 9000L)
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.1f,
                rightEyeProb = 0.1f,
                timestamp = blinkTime
            )
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.9f,
                rightEyeProb = 0.9f,
                timestamp = blinkTime + 180L
            )
        }

        // Finish 30-second subsequent scan
        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 30.0)

        assertTrue("Face should be detected", result.faceDetected)
        assertEquals("Should have recorded 3 blinks in 30 seconds", 3, result.blinkCount)
        assertEquals("3 blinks in 30s = 6.0 BPM", 6.0, result.bpm, 0.5)
        assertTrue("6 BPM < 13 is low rate", result.isLowRate)
        assertEquals("Status category should be LOW_RATE (Red notification)", BlinkStatusCategory.LOW_RATE, blinkDetector.metrics.value.statusCategory)
    }

    @Test
    fun testDutyCycleProtocolDurations() {
        val initialScanDurationSeconds = 60L
        val coolingDurationSeconds = 120L
        val subsequentScanDurationSeconds = 30L

        assertEquals("Initial scan duration must be 1 minute (60 seconds)", 60L, initialScanDurationSeconds)
        assertEquals("Cooling period must be 2 minutes (120 seconds)", 120L, coolingDurationSeconds)
        assertEquals("Subsequent scan duration must be exactly 30 seconds", 30L, subsequentScanDurationSeconds)
    }
}

