package com.bossless.companion.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bossless.companion.data.models.JobDocument

@Entity(tableName = "job_documents")
data class JobDocumentEntity(
    @PrimaryKey val id: String,
    val job_id: String,
    val file_name: String,
    val file_url: String?, // Remote URL
    val local_path: String?, // Local file path for pending uploads
    val document_type: String,
    val created_at: String,
    val synced: Boolean = true
)

fun JobDocument.toEntity(synced: Boolean = true, localPath: String? = null) = JobDocumentEntity(
    id = id,
    job_id = job_id,
    file_name = file_name,
    file_url = file_url,
    local_path = localPath,
    document_type = document_type,
    created_at = created_at,
    synced = synced
)

fun JobDocumentEntity.toModel() = JobDocument(
    id = id,
    job_id = job_id,
    file_name = file_name,
    file_url = file_url ?: local_path, // Fallback to local path if URL is missing (for UI display)
    document_type = document_type,
    created_at = created_at
)
