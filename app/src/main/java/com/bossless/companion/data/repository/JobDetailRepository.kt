package com.bossless.companion.data.repository

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.db.JobDetailDao
import com.bossless.companion.data.local.db.toEntity
import com.bossless.companion.data.local.db.toModel
import com.bossless.companion.data.models.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JobDetailRepository @Inject constructor(
    private val apiService: ApiService,
    private val jobDetailDao: JobDetailDao
) {
    fun getJobDetailFlow(jobId: String): Flow<Job?> =
        jobDetailDao.getJobDetailById(jobId).map { it?.toModel() }

    suspend fun getJobDetail(jobId: String): Result<Job> {
        return try {
            val response = apiService.getJob("eq.$jobId")
            if (response.isSuccessful && response.body() != null) {
                val job = response.body()!!.firstOrNull()
                if (job != null) {
                    jobDetailDao.insertJobDetail(job.toEntity())
                    Result.success(job)
                } else {
                    Result.failure(Exception("Job not found with id: $jobId"))
                }
            } else {
                val cachedJob = jobDetailDao.getJobDetailById(jobId).firstOrNull()?.toModel()
                cachedJob?.let { Result.success(it) } ?: Result.failure(Exception("Failed to fetch job: ${response.code()}"))
            }
        } catch (e: Exception) {
            val cachedJob = jobDetailDao.getJobDetailById(jobId).firstOrNull()?.toModel()
            cachedJob?.let { Result.success(it) } ?: Result.failure(e)
        }
    }
}
