package com.mitalipurohit.blinkwell.detection

enum class BlinkStatusCategory {
    NORMAL,             // Green: Blinking rate normal
    LOW_RATE,           // Red: Blinking rate lower
    FACE_NOT_DETECTED   // Yellow: Face not detected / poor lighting / out of frame
}

data class BlinkMetrics(
    val currentBpm: Double = 0.0,
    val totalBlinksInSession: Int = 0,
    val isFaceDetected: Boolean = false,
    val lastEyeOpenScore: Float = 1.0f,
    val isSamplingActive: Boolean = true,
    val isWarmedUp: Boolean = false,
    val warmupSecondsElapsed: Long = 0L,
    val alertTriggered: Boolean = false,
    val alertMessage: String? = null,
    val statusCategory: BlinkStatusCategory = BlinkStatusCategory.NORMAL,
    val burstElapsedSeconds: Long = 0L,
    val burstTotalSeconds: Long = 300L,
    val eyeOpenBaseline: Float = 0.75f
)

data class BurstSessionResult(
    val finalBpm: Double,
    val totalBlinks: Int,
    val durationSeconds: Long = 300L,
    val statusCategory: BlinkStatusCategory = BlinkStatusCategory.NORMAL,
    val completedTimestamp: Long = System.currentTimeMillis()
)

enum class EyeState {
    OPEN,
    CLOSING,
    CLOSED,
    OPENING
}
