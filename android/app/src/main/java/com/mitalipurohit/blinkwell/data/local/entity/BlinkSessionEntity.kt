package com.mitalipurohit.blinkwell.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "blink_sessions")
data class BlinkSessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val avgBpm: Double = 0.0,
    val minBpm: Double = 0.0,
    val alertCount: Int = 0,
    val mode: String = "app_only", // "background" or "app_only"
    val isSynced: Boolean = false
)
