package com.mitalipurohit.blinkwell

import com.mitalipurohit.blinkwell.detection.BlinkDetector
import com.mitalipurohit.blinkwell.detection.BlinkStatusCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BurstMode5MinTest {

    private lateinit var blinkDetector: BlinkDetector

    @Before
    fun setup() {
        blinkDetector = BlinkDetector(alertThresholdBpm = 13)
        blinkDetector.resetSession()
    }

    @Test
    fun test5MinBurstAccumulationAndConversion_HealthyRate() = runTest {
        val startTs = 1_000_000L
        blinkDetector.start5MinBurst(timestamp = startTs)

        val alertEventsList = mutableListOf<String>()
        val transitionsList = mutableListOf<Double>()
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)

        backgroundScope.launch(testDispatcher) {
            blinkDetector.alertEvents.toList(alertEventsList)
        }
        backgroundScope.launch(testDispatcher) {
            blinkDetector.greenToRedTransitions.toList(transitionsList)
        }

        // Simulate 75 blinks distributed over the 5 minutes (300 seconds -> 15 BPM)
        for (i in 1..75) {
            val blinkTime = startTs + (i * 3900L)
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

        // Finish 5-minute (300 seconds) burst
        val result = blinkDetector.finish5MinBurst(durationSeconds = 300.0)

        assertEquals("Should have recorded 75 blinks", 75, result.totalBlinks)
        assertEquals("75 blinks in 300s = 15 BPM", 15.0, result.finalBpm, 0.1)
        assertEquals("15 BPM >= 13 is NORMAL", BlinkStatusCategory.NORMAL, result.statusCategory)

        // Verify zero mid-session alert notifications were pushed
        assertTrue("No sustained alert notifications should be emitted during burst mode", alertEventsList.isEmpty())
        assertTrue("No green-to-red alert transitions should be emitted during burst mode", transitionsList.isEmpty())
    }

    @Test
    fun test5MinBurstAccumulationAndConversion_LowRate() {
        val startTs = 1_000_000L
        blinkDetector.start5MinBurst(timestamp = startTs)

        // Simulate 35 blinks over 300 seconds (7 BPM)
        for (i in 1..35) {
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
                timestamp = blinkTime + 180L
            )
        }

        val result = blinkDetector.finish5MinBurst(durationSeconds = 300.0)

        assertEquals("Should have recorded 35 blinks", 35, result.totalBlinks)
        assertEquals("35 blinks in 300s = 7 BPM", 7.0, result.finalBpm, 0.1)
        assertEquals("7 BPM < 13 is LOW_RATE", BlinkStatusCategory.LOW_RATE, result.statusCategory)
    }

    @Test
    fun testBurstElapsedSecondsUpdatesMetrics() {
        val startTs = 1_000_000L
        blinkDetector.start5MinBurst(timestamp = startTs)

        blinkDetector.updateBurstElapsedSeconds(120L)
        val metrics = blinkDetector.metrics.value

        assertEquals(120L, metrics.burstElapsedSeconds)
        assertEquals(300L, metrics.burstTotalSeconds)
    }
}
