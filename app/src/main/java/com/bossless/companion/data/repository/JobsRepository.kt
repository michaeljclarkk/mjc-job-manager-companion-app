package com.bossless.companion.data.repository

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.local.db.JobDao
import com.bossless.companion.data.local.db.toEntity
import com.bossless.companion.data.local.db.toModel
import com.bossless.companion.data.models.CreateJobUpdateRequest
import com.bossless.companion.data.models.JobAssignment
import com.bossless.companion.data.models.JobUpdate
import com.bossless.companion.data.models.ThirdParty
import com.bossless.companion.utils.ErrorReporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobsRepository @Inject constructor(
    private val apiService: ApiService,
    private val securePrefs: SecurePrefs,
    private val jobDao: JobDao
) {
    fun getAssignedJobsFlow(): Flow<List<JobAssignment>> =
        jobDao.getAllJobs().map { list -> list.map { it.toModel() } }

    suspend fun getAssignedJobs(): Result<List<JobAssignment>> {
        val userId = securePrefs.getUserId() ?: return Result.failure(Exception("User not logged in"))
        return try {
            val response = apiService.getAssignedJobs(userId = "eq.$userId")
            if (response.isSuccessful) {
                val jobs = response.body() ?: emptyList()
                // Update cache
                jobDao.clearJobs()
                jobDao.insertJobs(jobs.map { it.toEntity() })
                Result.success(jobs)
            } else {
                val error = Exception("Failed to fetch jobs: ${response.code()}")
                ErrorReporter.logAndEmailError(
                    context = "JobsRepository.getAssignedJobs",
                    error = error,
                    userId = userId,
                    additionalInfo = mapOf("responseCode" to response.code().toString())
                )
                // On failure, return cached jobs
                val cached = jobDao.getAllJobs().map { it.map { e -> e.toModel() } }
                Result.failure(error)
            }
        } catch (e: Exception) {
            ErrorReporter.logAndEmailError(
                context = "JobsRepository.getAssignedJobs",
                error = e,
                userId = userId
            )
            // On exception, return cached jobs
            val cached = jobDao.getAllJobs().map { it.map { e -> e.toModel() } }
            Result.failure(e)
        }
    }

    suspend fun getThirdParty(id: String): Result<ThirdParty?> {
        return try {
            val response = apiService.getThirdParty("eq.$id")
            if (response.isSuccessful) {
                Result.success(response.body()?.firstOrNull())
            } else {
                Result.failure(Exception("Failed to fetch customer: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getJobUpdates(jobId: String): Result<List<JobUpdate>> {
        return try {
            val response = apiService.getJobUpdates("eq.$jobId")
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch updates: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createJobUpdate(jobId: String, contentHtml: String): Result<JobUpdate> {
        val userId = securePrefs.getUserId() ?: return Result.failure(Exception("User not logged in"))

        val plainText = stripHtmlToPlainText(contentHtml)
        if (plainText.isBlank()) {
            return Result.failure(Exception("Update content cannot be empty"))
        }
        
        return try {
            val request = CreateJobUpdateRequest(
                job_id = jobId,
                author_id = userId,
                content = buildJobUpdateContentJson(contentHtml = contentHtml, plainText = plainText),
                content_html = contentHtml
            )
            if (com.bossless.companion.BuildConfig.DEBUG) {
                android.util.Log.d("JobsRepository", "Creating job update")
            }
            val response = apiService.createJobUpdate(request = request)
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                if (com.bossless.companion.BuildConfig.DEBUG) {
                    android.util.Log.d("JobsRepository", "Job update created successfully")
                }
                Result.success(response.body()!!.first())
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                val error = Exception("Failed to create update: ${response.code()} - $errorBody")
                android.util.Log.e("JobsRepository", "Failed to create job update: ${response.code()} - $errorBody")
                ErrorReporter.logAndEmailError(
                    context = "JobsRepository.createJobUpdate",
                    error = error,
                    userId = userId,
                    additionalInfo = mapOf(
                        "jobId" to jobId,
                        "responseCode" to response.code().toString(),
                        "errorBody" to errorBody
                    )
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            android.util.Log.e("JobsRepository", "Exception creating job update", e)
            ErrorReporter.logAndEmailError(
                context = "JobsRepository.createJobUpdate",
                error = e,
                userId = userId,
                additionalInfo = mapOf("jobId" to jobId)
            )
            Result.failure(e)
        }
    }

    private fun buildJobUpdateContentJson(contentHtml: String, plainText: String): JsonObject {
        return buildJsonObject {
            put("html", JsonPrimitive(contentHtml))
            put("text", JsonPrimitive(plainText))
        }
    }

    private fun stripHtmlToPlainText(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
