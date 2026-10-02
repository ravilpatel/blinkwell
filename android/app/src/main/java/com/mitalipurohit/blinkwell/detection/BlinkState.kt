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
    val alertTriggered: Boolean = false,
    val alertMessage: String? = null,
    val statusCategory: BlinkStatusCategory = BlinkStatusCategory.FACE_NOT_DETECTED
)

enum class EyeState {
    OPEN,
    CLOSING,
    CLOSED,
    OPENING
}
