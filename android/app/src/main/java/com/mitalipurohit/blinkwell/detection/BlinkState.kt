package com.mitalipurohit.blinkwell.detection

data class BlinkMetrics(
    val currentBpm: Double = 0.0,
    val totalBlinksInSession: Int = 0,
    val isFaceDetected: Boolean = false,
    val lastEyeOpenScore: Float = 1.0f,
    val isSamplingActive: Boolean = true,
    val alertTriggered: Boolean = false,
    val alertMessage: String? = null
)

enum class EyeState {
    OPEN,
    CLOSING,
    CLOSED,
    OPENING
}
