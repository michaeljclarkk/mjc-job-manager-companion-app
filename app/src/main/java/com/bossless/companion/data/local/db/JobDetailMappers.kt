package com.bossless.companion.data.local.db

import com.bossless.companion.data.models.Job
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

fun Job.toEntity(): JobDetailEntity = JobDetailEntity(
    id = this.id,
    data = Json.encodeToString(this),
    updatedAt = this.updated_at.toLongOrNull() ?: 0L
)

fun JobDetailEntity.toModel(): Job =
    Json.decodeFromString(Job.serializer(), this.data)
