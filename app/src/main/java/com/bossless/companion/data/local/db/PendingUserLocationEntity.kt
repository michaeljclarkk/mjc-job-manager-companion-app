package com.bossless.companion.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_user_locations",
    indices = [
        Index(value = ["created_at_epoch_ms"])
    ]
)
data class PendingUserLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val user_id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val speed: Float?,
    val heading: Float?,
    val altitude: Float?,
    val distance_delta_meters: Float,
    val recorded_at: String,
    val created_at_epoch_ms: Long,
    val attempt_count: Int = 0,
    val last_attempt_epoch_ms: Long? = null,
    val last_error: String? = null
)
