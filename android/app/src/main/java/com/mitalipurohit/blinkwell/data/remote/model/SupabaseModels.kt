package com.mitalipurohit.blinkwell.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileRemote(
    val id: String,
    @SerialName("research_consent")
    val researchConsent: Boolean = false,
    @SerialName("cohort_arm")
    val cohortArm: String = "general",
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class BlinkSessionRemote(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("started_at")
    val startedAt: String,
    @SerialName("ended_at")
    val endedAt: String? = null,
    @SerialName("avg_bpm")
    val avgBpm: Double? = null,
    @SerialName("min_bpm")
    val minBpm: Double? = null,
    @SerialName("alert_count")
    val alertCount: Int = 0,
    @SerialName("monitoring_mode")
    val monitoringMode: String = "app_only"
)

@Serializable
data class BlinkMinuteLogRemote(
    @SerialName("session_id")
    val sessionId: String,
    @SerialName("minute_timestamp")
    val minuteTimestamp: String,
    val bpm: Double
)
