package com.bossless.companion.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "job_details")
data class JobDetailEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "data") val data: String, // JSON blob for extensibility
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
