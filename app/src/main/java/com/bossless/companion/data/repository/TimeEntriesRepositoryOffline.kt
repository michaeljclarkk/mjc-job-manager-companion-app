package com.bossless.companion.data.repository

import com.bossless.companion.data.local.db.TimeEntryDao
import com.bossless.companion.data.local.db.toEntity
import com.bossless.companion.data.local.db.toModel
import com.bossless.companion.data.models.TimeEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeEntriesRepositoryOffline @Inject constructor(
    private val timeEntryDao: TimeEntryDao
) {
    fun getTimeEntriesForJobFlow(jobId: String): Flow<List<TimeEntry>> =
        timeEntryDao.getTimeEntriesForJob(jobId).map { list -> list.map { it.toModel() } }

    suspend fun getActiveTimeEntryForUser(userId: String): TimeEntry? {
        return timeEntryDao.getActiveTimeEntryForUser(userId)?.toModel()
    }

    suspend fun insertTimeEntry(entry: TimeEntry, synced: Boolean = false) {
        timeEntryDao.insertTimeEntry(entry.toEntity(synced))
    }

    suspend fun deleteTimeEntryById(entryId: String) {
        timeEntryDao.deleteTimeEntryById(entryId)
    }

    suspend fun markEntrySynced(entryId: String) {
        timeEntryDao.markEntrySynced(entryId)
    }

    suspend fun getUnsyncedEntries(): List<TimeEntry> =
        timeEntryDao.getUnsyncedEntries().map { it.toModel() }
}
