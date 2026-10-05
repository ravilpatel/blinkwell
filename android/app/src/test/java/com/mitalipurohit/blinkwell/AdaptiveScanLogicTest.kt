package com.mitalipurohit.blinkwell

import com.mitalipurohit.blinkwell.detection.BlinkDetector
import com.mitalipurohit.blinkwell.detection.BlinkStatusCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdaptiveScanLogicTest {

    private lateinit var blinkDetector: BlinkDetector

    @Before
    fun setup() {
        blinkDetector = BlinkDetector(alertThresholdBpm = 13)
        blinkDetector.resetSession()
    }

    @Test
    fun testBurstScan20SecondsBpmConversion_NormalRate() {
        val startTs = 1000000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Simulate 5 blinks within 20 seconds (15 BPM)
        for (i in 1..5) {
            val blinkTime = startTs + (i * 3500L)
            // Eye closes
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.1f,
                rightEyeProb = 0.1f,
                timestamp = blinkTime
            )
            // Eye opens 200ms later
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.9f,
                rightEyeProb = 0.9f,
                timestamp = blinkTime + 200L
            )
        }

        // Finish 20s scan
        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 20.0)

        assertTrue("Face should be detected", result.faceDetected)
        assertEquals("Should have recorded 5 blinks", 5, result.blinkCount)
        assertEquals("5 blinks in 20s = 15 BPM", 15.0, result.bpm, 0.5)
        assertFalse("15 BPM >= 13 threshold is NOT low rate", result.isLowRate)
        assertEquals(BlinkStatusCategory.NORMAL, blinkDetector.metrics.value.statusCategory)
    }

    @Test
    fun testBurstScan20SecondsBpmConversion_LowRate() {
        val startTs = 1000000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Simulate 2 blinks within 20 seconds (6 BPM)
        for (i in 1..2) {
            val blinkTime = startTs + (i * 8000L)
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
                timestamp = blinkTime + 200L
            )
        }

        // Finish 20s scan
        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 20.0)

        assertTrue("Face should be detected", result.faceDetected)
        assertEquals("Should have recorded 2 blinks", 2, result.blinkCount)
        assertEquals("2 blinks in 20s = 6 BPM", 6.0, result.bpm, 0.5)
        assertTrue("6 BPM < 13 threshold is LOW rate", result.isLowRate)
        assertEquals(BlinkStatusCategory.LOW_RATE, blinkDetector.metrics.value.statusCategory)
    }

    @Test
    fun testAdaptiveScanIntervalDecisionLogic() {
        val threshold = 13

        // Function modeling the adaptive scheduling decision
        fun determineNextScanDelayMs(bpm: Double, isFaceDetected: Boolean): Long {
            return if (!isFaceDetected) {
                120_000L // Standard pause
            } else if (bpm < threshold) {
                10_000L // Low rate -> rescan after 10s for 20s
            } else {
                120_000L // Normal rate -> scan after usual period (2 min)
            }
        }

        // Low rate (e.g. 7 BPM) -> 10 seconds delay
        assertEquals(10_000L, determineNextScanDelayMs(7.0, true))

        // Normal rate (e.g. 16 BPM) -> 120 seconds delay (usual period)
        assertEquals(120_000L, determineNextScanDelayMs(16.0, true))

        // No face detected -> 120 seconds delay
        assertEquals(120_000L, determineNextScanDelayMs(0.0, false))
    }

    @Test
    fun testRealTimeBpmUpdatingDuringBurst() {
        val startTs = 1000000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // 1st blink at 4s
        val blink1 = startTs + 4000L
        blinkDetector.onFrameProcessed(true, 0.1f, 0.1f, blink1)
        blinkDetector.onFrameProcessed(true, 0.9f, 0.9f, blink1 + 200L)

        // At 4.2s with 1 blink, live BPM = (1 * 60) / 4.2 ≈ 14.28 BPM
        val liveBpm1 = blinkDetector.metrics.value.currentBpm
        assertTrue("Live BPM should be > 0 during burst", liveBpm1 > 0.0)
        assertTrue("Sampling active during burst", blinkDetector.metrics.value.isSamplingActive)
    }

    @Test
    fun testNoFaceDetectedInFirst5SecondsAbortsAndMarksLost() {
        val startTs = 1000000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Frames arrive without face for 5 seconds
        for (i in 1..5) {
            val frameTs = startTs + (i * 1000L)
            blinkDetector.onFrameProcessed(
                faceDetected = false,
                leftEyeProb = null,
                rightEyeProb = null,
                timestamp = frameTs
            )
        }

        // At 5s check:
        assertFalse("Face should NOT have been seen in burst", blinkDetector.hasSeenFaceInBurst())
        assertFalse("Face recency check should be false", blinkDetector.hasSeenFaceRecently(5000L, now = startTs + 5000L))

        // Trigger markFaceLost
        blinkDetector.markFaceLost()
        assertEquals(BlinkStatusCategory.FACE_NOT_DETECTED, blinkDetector.metrics.value.statusCategory)
        assertFalse(blinkDetector.metrics.value.isFaceDetected)
    }

    @Test
    fun testFaceDetectedInFirst5SecondsAllowsScanCompletion() {
        val startTs = 1000000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Face appears at 2 seconds
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.85f,
            rightEyeProb = 0.85f,
            timestamp = startTs + 2000L
        )

        // At 5s check:
        assertTrue("Face should have been detected in burst", blinkDetector.hasSeenFaceInBurst())
        assertTrue("Face recency should be true", blinkDetector.hasSeenFaceRecently(5000L, now = startTs + 5000L))
    }
}
