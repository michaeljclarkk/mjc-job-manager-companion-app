package com.bossless.companion.data.repository

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.models.CreateTimeEntryRequest
import com.bossless.companion.data.models.CreateFullTimeEntryRequest
import com.bossless.companion.data.models.Job
import com.bossless.companion.data.models.TimeEntry
import com.bossless.companion.data.models.UpdateTimeEntryRequest
import com.bossless.companion.utils.ErrorReporter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimeEntriesRepository @Inject constructor(
    private val apiService: ApiService,
    private val securePrefs: SecurePrefs
) {

    class TimeEntryNotFoundException(message: String = "Time entry not found") : Exception(message)
    suspend fun getActiveTimeEntry(): Result<TimeEntry?> {
        val userId = securePrefs.getUserId() ?: return Result.failure(Exception("User not logged in"))
        
        return try {
            val response = apiService.getActiveTimeEntry(userId = "eq.$userId")
            if (response.isSuccessful) {
                Result.success(response.body()?.firstOrNull())
            } else {
                Result.failure(Exception("Failed to fetch active timer: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTimeEntriesForJob(jobId: String): Result<List<TimeEntry>> {
        return try {
            val response = apiService.getTimeEntriesForJob(jobId = "eq.$jobId")
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch time entries: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startTimeEntry(jobId: String, startTime: String, note: String? = null): Result<TimeEntry> {
        val userId = securePrefs.getUserId() ?: return Result.failure(Exception("User not logged in"))
        
        return try {
            val request = CreateTimeEntryRequest(
                job_id = jobId,
                user_id = userId,
                start_time = startTime,
                start_note = note
            )
            val response = apiService.createTimeEntry(request = request)
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                Result.success(response.body()!!.first())
            } else {
                val error = Exception("Failed to start timer: ${response.code()}")
                ErrorReporter.logAndEmailError(
                    context = "TimeEntriesRepository.startTimeEntry",
                    error = error,
                    userId = userId,
                    additionalInfo = mapOf("jobId" to jobId)
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            ErrorReporter.logAndEmailError(
                context = "TimeEntriesRepository.startTimeEntry",
                error = e,
                userId = userId,
                additionalInfo = mapOf("jobId" to jobId)
            )
            Result.failure(e)
        }
    }

    suspend fun createFullTimeEntry(
        jobId: String,
        startTime: String,
        finishTime: String,
        durationSeconds: Int,
        note: String? = null
    ): Result<TimeEntry> {
        val userId = securePrefs.getUserId() ?: return Result.failure(Exception("User not logged in"))
        
        return try {
            val request = CreateFullTimeEntryRequest(
                job_id = jobId,
                user_id = userId,
                start_time = startTime,
                finish_time = finishTime,
                duration_seconds = durationSeconds,
                start_note = note
            )
            val response = apiService.createFullTimeEntry(request = request)
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                Result.success(response.body()!!.first())
            } else {
                Result.failure(Exception("Failed to create time entry: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTimeEntry(
        entryId: String,
        startTime: String,
        finishTime: String,
        durationSeconds: Int,
        note: String? = null
    ): Result<TimeEntry> {
        return try {
            val request = UpdateTimeEntryRequest(
                start_time = startTime,
                finish_time = finishTime,
                duration_seconds = durationSeconds,
                start_note = note
            )
            val response = apiService.updateTimeEntry(id = "eq.$entryId", request = request)
            if (response.isSuccessful) {
                val body = response.body()
                if (!body.isNullOrEmpty()) {
                    Result.success(body.first())
                } else {
                    Result.failure(Exception("Time entry not found"))
                }
            } else {
                Result.failure(Exception("Failed to update time entry: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun stopTimeEntry(entryId: String, finishTime: String, durationSeconds: Int, note: String? = null): Result<TimeEntry> {
        val userId = securePrefs.getUserId()
        return try {
            val request = UpdateTimeEntryRequest(
                finish_time = finishTime,
                finish_note = note,
                duration_seconds = durationSeconds
            )
            val response = apiService.updateTimeEntry(id = "eq.$entryId", request = request)
            if (response.isSuccessful) {
                val body = response.body()
                if (!body.isNullOrEmpty()) {
                    Result.success(body.first())
                } else {
                    // Common + expected scenario: timer was created offline (local-only id) so PATCH updates 0 rows.
                    // Let caller decide whether to fallback (e.g. createFullTimeEntry) before reporting.
                    Result.failure(TimeEntryNotFoundException())
                }
            } else {
                val error = Exception("Failed to stop timer: ${response.code()}")
                ErrorReporter.logAndEmailError(
                    context = "TimeEntriesRepository.stopTimeEntry",
                    error = error,
                    userId = userId,
                    additionalInfo = mapOf("entryId" to entryId, "responseCode" to response.code().toString())
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            ErrorReporter.logAndEmailError(
                context = "TimeEntriesRepository.stopTimeEntry",
                error = e,
                userId = userId,
                additionalInfo = mapOf("entryId" to entryId)
            )
            Result.failure(e)
        }
    }

    suspend fun deleteTimeEntry(entryId: String): Result<Unit> {
        return try {
            val response = apiService.deleteTimeEntry(id = "eq.$entryId")
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete time entry: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getJob(jobId: String): Result<Job?> {
        return try {
            val response = apiService.getJob(id = "eq.$jobId")
            if (response.isSuccessful) {
                Result.success(response.body()?.firstOrNull())
            } else {
                Result.failure(Exception("Failed to fetch job: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
