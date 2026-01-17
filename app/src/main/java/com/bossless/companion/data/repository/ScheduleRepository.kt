package com.bossless.companion.data.repository

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.models.ScheduledJobAssignment
import com.bossless.companion.utils.ErrorReporter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(
    private val apiService: ApiService,
    private val securePrefs: SecurePrefs
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE // yyyy-MM-dd

    /**
     * Check if the scheduler feature is enabled for this organization
     */
    suspend fun isSchedulerEnabled(): Boolean {
        return try {
            val response = apiService.getBusinessProfileWithFeatures()
            if (response.isSuccessful) {
                response.body()?.firstOrNull()?.feature_scheduler == true
            } else {
                false
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "ScheduleRepository.isSchedulerEnabled",
                error = e
            )
            false
        }
    }

    /**
     * Fetch today's scheduled job assignments for the current user.
     * Returns jobs ordered by planned_start_time ascending.
     */
    suspend fun getTodaysSchedule(): Result<List<ScheduledJobAssignment>> {
        val userId = securePrefs.getUserId()
            ?: return Result.failure(Exception("User not logged in"))

        val today = LocalDate.now().format(dateFormatter)

        return try {
            val response = apiService.getTodaysSchedule(
                userId = "eq.$userId",
                plannedDate = "eq.$today"
            )

            if (response.isSuccessful) {
                val assignments = response.body() ?: emptyList()
                // Filter out completed and cancelled jobs
                val activeAssignments = assignments.filter { assignment ->
                    val status = assignment.jobs?.status?.lowercase() ?: ""
                    status != "completed" && status != "cancelled"
                }
                Result.success(activeAssignments)
            } else {
                val error = Exception("Failed to fetch schedule: ${response.code()}")
                ErrorReporter.logAndReportError(
                    context = "ScheduleRepository.getTodaysSchedule",
                    error = error,
                    userId = userId,
                    additionalInfo = mapOf("responseCode" to response.code().toString())
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "ScheduleRepository.getTodaysSchedule",
                error = e,
                userId = userId
            )
            Result.failure(e)
        }
    }

    /**
     * Fetch schedule for a specific date
     */
    suspend fun getScheduleForDate(date: LocalDate): Result<List<ScheduledJobAssignment>> {
        val userId = securePrefs.getUserId()
            ?: return Result.failure(Exception("User not logged in"))

        val dateString = date.format(dateFormatter)

        return try {
            val response = apiService.getTodaysSchedule(
                userId = "eq.$userId",
                plannedDate = "eq.$dateString"
            )

            if (response.isSuccessful) {
                val assignments = response.body() ?: emptyList()
                // Filter out completed and cancelled jobs
                val activeAssignments = assignments.filter { assignment ->
                    val status = assignment.jobs?.status?.lowercase() ?: ""
                    status != "completed" && status != "cancelled"
                }
                Result.success(activeAssignments)
            } else {
                Result.failure(Exception("Failed to fetch schedule: ${response.code()}"))
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "ScheduleRepository.getScheduleForDate",
                error = e,
                userId = userId,
                additionalInfo = mapOf("date" to dateString)
            )
            Result.failure(e)
        }
    }

    /**
     * Check if Stripe payments feature is enabled for this organization.
     * Both feature_stripe_payments (feature flag) and enable_stripe_payments (configured) must be true.
     */
    suspend fun isStripePaymentsEnabled(): Boolean {
        return try {
            val response = apiService.getBusinessProfileWithFeatures()
            if (response.isSuccessful) {
                val profile = response.body()?.firstOrNull()
                // Both the feature flag AND the configuration must be enabled
                profile?.feature_stripe_payments == true && profile.enable_stripe_payments == true
            } else {
                false
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "ScheduleRepository.isStripePaymentsEnabled",
                error = e
            )
            false
        }
    }
}
