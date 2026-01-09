package com.bossless.companion.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDetailDao {
    @Query("SELECT * FROM job_details WHERE id = :id LIMIT 1")
    fun getJobDetailById(id: String): Flow<JobDetailEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobDetail(jobDetail: JobDetailEntity)

    @Delete
    suspend fun deleteJobDetail(jobDetail: JobDetailEntity)

    @Query("DELETE FROM job_details")
    suspend fun clearJobDetails()
}
