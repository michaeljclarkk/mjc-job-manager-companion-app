package com.bossless.companion.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "time_entries")
data class TimeEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "job_id") val jobId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "start_time") val startTime: String,
    @ColumnInfo(name = "finish_time") val finishTime: String?,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Int,
    @ColumnInfo(name = "note") val note: String?,
    @ColumnInfo(name = "synced") val synced: Boolean = true
)
