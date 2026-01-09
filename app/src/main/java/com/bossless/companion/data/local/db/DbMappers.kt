package com.bossless.companion.data.local.db

import com.bossless.companion.data.models.Job
import com.bossless.companion.data.models.JobAssignment
import com.bossless.companion.data.models.Notification
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun JobAssignment.toEntity(): JobEntity = JobEntity(
    id = this.id,
    title = this.jobs?.name ?: "",
    status = this.jobs?.status ?: "",
    customer = this.jobs?.third_party_id,
    updatedAt = this.jobs?.updated_at?.toLongOrNull() ?: 0L,
    data = Json.encodeToString(this)
)

fun JobEntity.toModel(): JobAssignment =
    Json.decodeFromString(JobAssignment.serializer(), this.data)

fun Notification.toEntity(): NotificationEntity = NotificationEntity(
    id = this.id,
    userId = this.user_id,
    type = this.type,
    title = this.title,
    message = this.message,
    referenceId = this.reference_id,
    referenceType = this.reference_type,
    read = this.read,
    createdAt = this.created_at
)

fun NotificationEntity.toModel(): Notification = Notification(
    id = this.id,
    user_id = this.userId,
    type = this.type,
    title = this.title,
    message = this.message,
    reference_id = this.referenceId,
    reference_type = this.referenceType,
    read = this.read,
    created_at = this.createdAt
)
