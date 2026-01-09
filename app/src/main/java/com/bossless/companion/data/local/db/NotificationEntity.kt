package com.bossless.companion.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "message") val message: String?,
    @ColumnInfo(name = "reference_id") val referenceId: String?,
    @ColumnInfo(name = "reference_type") val referenceType: String?,
    @ColumnInfo(name = "read") val read: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String
)
