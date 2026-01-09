package com.bossless.companion.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDocumentDao {
    @Query("SELECT * FROM job_documents WHERE job_id = :jobId ORDER BY created_at DESC")
    fun getDocumentsForJob(jobId: String): Flow<List<JobDocumentEntity>>

    @Query("SELECT * FROM job_documents WHERE synced = 0")
    suspend fun getUnsyncedDocuments(): List<JobDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: JobDocumentEntity)

    @Query("DELETE FROM job_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)
    
    @Query("UPDATE job_documents SET synced = 1, file_url = :remoteUrl WHERE id = :id")
    suspend fun markAsSynced(id: String, remoteUrl: String)
}
