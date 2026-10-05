package com.mitalipurohit.blinkwell

import com.mitalipurohit.blinkwell.detection.BlinkDetector
import com.mitalipurohit.blinkwell.detection.BlinkStatusCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlinkAccuracyTest {

    private lateinit var blinkDetector: BlinkDetector

    @Before
    fun setup() {
        blinkDetector = BlinkDetector(alertThresholdBpm = 13)
        blinkDetector.resetSession()
    }

    @Test
    fun testHoodedEyesAndDownwardGazeDetection() {
        val startTs = 1_000_000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Simulate 15 frames of looking down at the phone where open score hovers around 0.52
        for (i in 1..15) {
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.52f,
                rightEyeProb = 0.52f,
                timestamp = startTs + (i * 100L)
            )
        }

        // Verify baseline adapted downwards towards user's natural open score
        val adaptedBaseline = blinkDetector.getEyeOpenBaseline()
        assertTrue("Baseline should adapt below default 0.75 for hooded/downward eyes: $adaptedBaseline", adaptedBaseline < 0.75f)

        // Blink occurs: eyes close to 0.10, then reopen to 0.52 (within 180ms)
        val blinkTime = startTs + 2000L
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.10f,
            rightEyeProb = 0.10f,
            timestamp = blinkTime
        )
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.52f,
            rightEyeProb = 0.52f,
            timestamp = blinkTime + 180L
        )

        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 20.0)
        assertEquals("Should successfully detect blink for hooded/downward gaze", 1, result.blinkCount)
    }

    @Test
    fun testWinkRejection() {
        val startTs = 1_000_000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Establish normal open eye baseline
        blinkDetector.onFrameProcessed(true, 0.85f, 0.85f, startTs)

        // Unilateral wink: left eye shuts completely, right eye stays wide open
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.05f,
            rightEyeProb = 0.85f,
            timestamp = startTs + 500L
        )
        // Left eye reopens
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.85f,
            rightEyeProb = 0.85f,
            timestamp = startTs + 700L
        )

        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 20.0)
        assertEquals("Wink should NOT be counted as a bilateral blink", 0, result.blinkCount)
    }

    @Test
    fun testSingleEyeFallbackWhenOneEyeHasGlareOrOcclusion() {
        val startTs = 1_000_000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Right eye is obscured by glasses glare or hair (null probability)
        // Left eye blinks normally: closes to 0.10 then reopens to 0.90
        val blinkTime = startTs + 1000L
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.10f,
            rightEyeProb = null,
            timestamp = blinkTime
        )
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.90f,
            rightEyeProb = null,
            timestamp = blinkTime + 200L
        )

        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 20.0)
        assertTrue("Face should be detected despite single eye occlusion", result.faceDetected)
        assertEquals("Should detect blink using visible eye fallback", 1, result.blinkCount)
    }

    @Test
    fun testRefractoryPeriodSuppressesDoubleBlinksAndRebound() {
        val startTs = 1_000_000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // First valid blink at 1000ms, completes at 1180ms
        blinkDetector.onFrameProcessed(true, 0.10f, 0.10f, startTs + 1000L)
        blinkDetector.onFrameProcessed(true, 0.90f, 0.90f, startTs + 1180L)

        // Fast eyelid tremor / rebound artifact at 1240ms (only 60ms after blink completion)
        blinkDetector.onFrameProcessed(true, 0.10f, 0.10f, startTs + 1240L)
        blinkDetector.onFrameProcessed(true, 0.90f, 0.90f, startTs + 1320L)

        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 20.0)
        assertEquals("Refractory period should suppress eyelid rebound oscillation", 1, result.blinkCount)
    }

    @Test
    fun testHeadPoseGating() {
        val startTs = 1_000_000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // User turns head away sharply (yaw = 45 degrees > 35 degrees threshold)
        val blinkTime = startTs + 1000L
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.10f,
            rightEyeProb = 0.10f,
            timestamp = blinkTime,
            headEulerAngleX = 0f,
            headEulerAngleY = 45f
        )
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.90f,
            rightEyeProb = 0.90f,
            timestamp = blinkTime + 200L,
            headEulerAngleX = 0f,
            headEulerAngleY = 45f
        )

        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 20.0)
        assertEquals("Frames with extreme head yaw must be gated out", 0, result.blinkCount)
    }

    @Test
    fun testWideEyesDeepPartialBlink() {
        val startTs = 1_000_000L
        blinkDetector.startBurstScan(timestamp = startTs)

        // Establish wide-open baseline (0.92)
        for (i in 1..15) {
            blinkDetector.onFrameProcessed(
                faceDetected = true,
                leftEyeProb = 0.92f,
                rightEyeProb = 0.92f,
                timestamp = startTs + (i * 100L)
            )
        }

        // Deep partial blink: eyelid descends significantly (prob drops to 0.25, then returns to 0.92)
        val blinkTime = startTs + 2000L
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.25f,
            rightEyeProb = 0.25f,
            timestamp = blinkTime
        )
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.92f,
            rightEyeProb = 0.92f,
            timestamp = blinkTime + 180L
        )

        val result = blinkDetector.finishBurstScan(scanDurationSeconds = 20.0)
        assertEquals("Deep partial blink from wide-eyed baseline should be detected", 1, result.blinkCount)
    }

    @Test
    fun testFaceGracePeriodAndReacquisition() {
        val startTs = 1_000_000L
        blinkDetector.setSamplingActive(true)

        // Face seen at startTs
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.85f,
            rightEyeProb = 0.85f,
            timestamp = startTs
        )
        assertTrue(blinkDetector.metrics.value.isFaceDetected)

        // 4 seconds later without face: should still be in grace period
        blinkDetector.checkGracePeriod(timestamp = startTs + 4_000L)
        assertTrue("Grace period should keep isFaceDetected true within 10s", blinkDetector.metrics.value.isFaceDetected)

        // 12 seconds later without face: grace period expired
        blinkDetector.checkGracePeriod(timestamp = startTs + 12_000L)
        assertFalse("Face should be marked not detected after 12s", blinkDetector.metrics.value.isFaceDetected)
        assertEquals(BlinkStatusCategory.FACE_NOT_DETECTED, blinkDetector.metrics.value.statusCategory)

        // Face returns at 15s
        blinkDetector.onFrameProcessed(
            faceDetected = true,
            leftEyeProb = 0.85f,
            rightEyeProb = 0.85f,
            timestamp = startTs + 15_000L
        )
        assertTrue("Face reacquisition should immediately restore isFaceDetected", blinkDetector.metrics.value.isFaceDetected)
    }
}
