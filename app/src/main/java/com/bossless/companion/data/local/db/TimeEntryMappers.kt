package com.bossless.companion.data.local.db

import com.bossless.companion.data.models.TimeEntry

fun TimeEntry.toEntity(synced: Boolean = true): TimeEntryEntity = TimeEntryEntity(
    id = this.id,
    jobId = this.job_id,
    userId = this.user_id,
    startTime = this.start_time,
    finishTime = this.finish_time,
    durationSeconds = this.duration_seconds,
    note = this.start_note ?: this.finish_note,
    synced = synced
)

fun TimeEntryEntity.toModel(): TimeEntry = TimeEntry(
    id = this.id,
    job_id = this.jobId,
    user_id = this.userId,
    start_time = this.startTime,
    finish_time = this.finishTime,
    start_note = this.note,
    finish_note = this.note,
    duration_seconds = this.durationSeconds,
    created_at = this.startTime
)
