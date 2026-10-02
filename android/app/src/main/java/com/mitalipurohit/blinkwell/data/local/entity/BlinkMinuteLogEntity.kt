package com.mitalipurohit.blinkwell.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blink_minute_log",
    foreignKeys = [
        ForeignKey(
            entity = BlinkSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class BlinkMinuteLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val minuteTimestamp: Long,
    val bpm: Double,
    val isSynced: Boolean = false
)
