package com.bossless.companion.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingUserLocationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PendingUserLocationEntity): Long

    @Query("SELECT * FROM pending_user_locations ORDER BY created_at_epoch_ms ASC LIMIT :limit")
    suspend fun getOldest(limit: Int): List<PendingUserLocationEntity>

    @Query("DELETE FROM pending_user_locations WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query(
        "UPDATE pending_user_locations " +
            "SET attempt_count = attempt_count + 1, " +
            "last_attempt_epoch_ms = :attemptedAtEpochMs, " +
            "last_error = :lastError " +
            "WHERE id = :id"
    )
    suspend fun markAttempt(id: Long, attemptedAtEpochMs: Long, lastError: String?)

    @Query("SELECT COUNT(*) FROM pending_user_locations")
    suspend fun count(): Int

    @Query(
        "DELETE FROM pending_user_locations " +
            "WHERE id NOT IN (" +
            "SELECT id FROM pending_user_locations ORDER BY created_at_epoch_ms DESC LIMIT :maxRows" +
            ")"
    )
    suspend fun trimToMaxRows(maxRows: Int)
}
