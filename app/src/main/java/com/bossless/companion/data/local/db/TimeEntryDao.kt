package com.bossless.companion.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEntryDao {
    @Query("SELECT * FROM time_entries WHERE job_id = :jobId ORDER BY start_time DESC")
    fun getTimeEntriesForJob(jobId: String): Flow<List<TimeEntryEntity>>

    @Query("SELECT * FROM time_entries WHERE user_id = :userId AND finish_time IS NULL LIMIT 1")
    suspend fun getActiveTimeEntryForUser(userId: String): TimeEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeEntry(entry: TimeEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimeEntries(entries: List<TimeEntryEntity>)

    @Delete
    suspend fun deleteTimeEntry(entry: TimeEntryEntity)

    @Query("DELETE FROM time_entries WHERE id = :entryId")
    suspend fun deleteTimeEntryById(entryId: String)

    @Query("SELECT * FROM time_entries WHERE synced = 0")
    suspend fun getUnsyncedEntries(): List<TimeEntryEntity>

    @Query("UPDATE time_entries SET synced = 1 WHERE id = :entryId")
    suspend fun markEntrySynced(entryId: String)
}
