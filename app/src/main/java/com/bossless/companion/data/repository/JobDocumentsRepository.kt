package com.bossless.companion.data.repository

import android.content.Context
import android.net.Uri
import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.local.db.JobDocumentDao
import com.bossless.companion.data.local.db.toEntity
import com.bossless.companion.data.local.db.toModel
import com.bossless.companion.data.models.CreateJobDocumentRequest
import com.bossless.companion.data.models.JobDocument
import com.bossless.companion.utils.ErrorReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.util.Locale

@Singleton
class JobDocumentsRepository @Inject constructor(
    private val apiService: ApiService,
    private val jobDocumentDao: JobDocumentDao,
    private val securePrefs: SecurePrefs,
    @ApplicationContext private val context: Context
) {
    /**
     * Check if a storage upload error is a duplicate file error.
     * Supabase Storage returns HTTP 400 with a JSON body containing {"statusCode":"409","error":"Duplicate"}
     */
    private fun isDuplicateFileError(response: Response<*>): Boolean {
        if (response.code() == 409) return true
        if (response.code() == 400) {
            try {
                val errorBody = response.errorBody()?.string() ?: return false
                val json = JSONObject(errorBody)
                val statusCode = json.optString("statusCode", "")
                val error = json.optString("error", "")
                return statusCode == "409" || error.equals("Duplicate", ignoreCase = true)
            } catch (e: Exception) {
                return false
            }
        }
        return false
    }

    fun getDocumentsForJob(jobId: String): Flow<List<JobDocument>> =
        jobDocumentDao.getDocumentsForJob(jobId).map { list -> list.map { it.toModel() } }

    suspend fun uploadDocument(jobId: String, uri: Uri, isCamera: Boolean): Result<Unit> {
        val userId = securePrefs.getUserId() ?: return Result.failure(Exception("User not logged in"))
        
        // 1. Copy file to internal storage
        val file = copyUriToInternalStorage(uri, isCamera) ?: return Result.failure(Exception("Failed to process file"))
        val fileName = file.name
        val documentId = UUID.randomUUID().toString()
        
        // Determine mime type and document type
        val mimeType = getMimeType(file)
        val docType = getDocumentType(mimeType)

        // 2. Create local record (synced = false)
        val localDocument = JobDocument(
            id = documentId,
            job_id = jobId,
            file_name = fileName,
            file_url = null, // No remote URL yet
            document_type = docType,
            created_at = Instant.now().toString()
        )
        jobDocumentDao.insertDocument(localDocument.toEntity(synced = false, localPath = file.absolutePath))

        // 3. Attempt Upload
        return try {
            val storagePath = "$userId/$fileName"
            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", fileName, requestFile)
            
            val uploadResponse = apiService.uploadJobDocument(storagePath, body)
            
            // Proceed if upload successful OR if file already exists (duplicate)
            // Note: Supabase Storage returns HTTP 400 with {"statusCode":"409","error":"Duplicate"}
            if (uploadResponse.isSuccessful || isDuplicateFileError(uploadResponse)) {
                val createRequest = CreateJobDocumentRequest(
                    job_id = jobId,
                    file_name = fileName,
                    file_url = storagePath,
                    document_type = docType
                )
                
                val createResponse = apiService.createJobDocument(request = createRequest)
                if (createResponse.isSuccessful && !createResponse.body().isNullOrEmpty()) {
                    val remoteDoc = createResponse.body()!!.first()
                    // 5. Update local record to synced
                    jobDocumentDao.deleteDocumentById(documentId) // Remove temp
                    jobDocumentDao.insertDocument(remoteDoc.toEntity(synced = true))
                    Result.success(Unit)
                } else {
                    val error = Exception("Failed to create document record: ${createResponse.code()}")
                    ErrorReporter.logAndReportError(
                        context = "JobDocumentsRepository.uploadDocument",
                        error = error,
                        userId = userId,
                        additionalInfo = mapOf("jobId" to jobId, "fileName" to fileName)
                    )
                    Result.failure(error)
                }
            } else {
                val error = Exception("Failed to upload file: ${uploadResponse.code()}")
                ErrorReporter.logAndReportError(
                    context = "JobDocumentsRepository.uploadDocument",
                    error = error,
                    userId = userId,
                    additionalInfo = mapOf("jobId" to jobId, "fileName" to fileName)
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            // Keep local record as unsynced
            ErrorReporter.logAndReportError(
                context = "JobDocumentsRepository.uploadDocument",
                error = e,
                userId = userId,
                additionalInfo = mapOf("jobId" to jobId, "fileName" to fileName)
            )
            Result.failure(e)
        }
    }

    suspend fun syncPendingUploads() {
        val pending = jobDocumentDao.getUnsyncedDocuments()
        val userId = securePrefs.getUserId() ?: return
        
        pending.forEach { doc ->
            if (doc.local_path != null) {
                try {
                    val file = File(doc.local_path)
                    if (file.exists()) {
                        val storagePath = "$userId/${doc.file_name}"
                        val mimeType = getMimeType(file)
                        val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("file", doc.file_name, requestFile)
                        
                        val uploadResponse = apiService.uploadJobDocument(storagePath, body)
                        // Proceed if upload successful OR if file already exists (duplicate)
                        if (uploadResponse.isSuccessful || isDuplicateFileError(uploadResponse)) {
                            val createRequest = CreateJobDocumentRequest(
                                job_id = doc.job_id,
                                file_name = doc.file_name,
                                file_url = storagePath,
                                document_type = doc.document_type
                            )
                            val createResponse = apiService.createJobDocument(request = createRequest)
                            if (createResponse.isSuccessful && !createResponse.body().isNullOrEmpty()) {
                                val remoteDoc = createResponse.body()!!.first()
                                jobDocumentDao.deleteDocumentById(doc.id)
                                jobDocumentDao.insertDocument(remoteDoc.toEntity(synced = true))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Log sync failures but continue to next document
                    ErrorReporter.logAndReportError(
                        context = "JobDocumentsRepository.syncPendingUploads",
                        error = e,
                        userId = userId,
                        additionalInfo = mapOf("docId" to doc.id, "fileName" to doc.file_name)
                    )
                }
            }
        }
    }

    private fun copyUriToInternalStorage(uri: Uri, isCamera: Boolean): File? {
        return try {
            val fileName = if (isCamera) {
                "upload_${System.currentTimeMillis()}.jpg"
            } else {
                getFileName(uri) ?: "upload_${System.currentTimeMillis()}.bin"
            }
            
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.filesDir, fileName)
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    private fun getMimeType(file: File): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(file.name)
        return if (extension != null) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.getDefault())) ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }
    }

    private fun getDocumentType(mimeType: String): String {
        return when {
            mimeType.startsWith("image/") -> "image"
            mimeType.startsWith("video/") -> "video"
            mimeType == "application/pdf" -> "pdf"
            mimeType.contains("word") || mimeType.contains("document") -> "document"
            mimeType.contains("excel") || mimeType.contains("sheet") || mimeType.contains("csv") -> "spreadsheet"
            else -> "other"
        }
    }
}
