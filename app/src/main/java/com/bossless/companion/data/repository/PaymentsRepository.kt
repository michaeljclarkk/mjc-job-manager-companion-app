package com.bossless.companion.data.repository

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.models.InvoiceDocument
import com.bossless.companion.data.models.RegeneratePaymentTokenRequest
import com.bossless.companion.data.models.RegeneratePaymentTokenResponse
import com.bossless.companion.data.models.SendDocumentEmailRequest
import com.bossless.companion.data.models.SendDocumentEmailResponse
import com.bossless.companion.utils.ErrorReporter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentsRepository @Inject constructor(
    private val apiService: ApiService,
    private val securePrefs: SecurePrefs
) {
    /**
     * Get unpaid invoices for a specific job.
     * Only returns invoices with status: draft, validated, sent, overdue
     */
    suspend fun getInvoicesForJob(jobId: String): Result<List<InvoiceDocument>> {
        val userId = securePrefs.getUserId()
            ?: return Result.failure(Exception("User not logged in"))

        return try {
            val response = apiService.getInvoicesForJob(jobId = "eq.$jobId")

            if (response.isSuccessful) {
                val invoices = response.body() ?: emptyList()
                Result.success(invoices)
            } else {
                val error = Exception("Failed to fetch invoices: ${response.code()}")
                ErrorReporter.logAndReportError(
                    context = "PaymentsRepository.getInvoicesForJob",
                    error = error,
                    userId = userId,
                    additionalInfo = mapOf(
                        "jobId" to jobId,
                        "responseCode" to response.code().toString()
                    )
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "PaymentsRepository.getInvoicesForJob",
                error = e,
                userId = userId,
                additionalInfo = mapOf("jobId" to jobId)
            )
            Result.failure(e)
        }
    }

    /**
     * Regenerate a payment token for an invoice.
     * Used when a customer says the link has expired.
     */
    suspend fun regeneratePaymentToken(documentId: String): Result<RegeneratePaymentTokenResponse> {
        val userId = securePrefs.getUserId()
            ?: return Result.failure(Exception("User not logged in"))

        return try {
            val response = apiService.regeneratePaymentToken(
                RegeneratePaymentTokenRequest(documentId = documentId)
            )

            if (response.isSuccessful) {
                val result = response.body()
                if (result != null && result.success) {
                    Result.success(result)
                } else {
                    val error = Exception(result?.error ?: "Failed to regenerate token")
                    Result.failure(error)
                }
            } else {
                val error = Exception("Failed to regenerate token: ${response.code()}")
                ErrorReporter.logAndReportError(
                    context = "PaymentsRepository.regeneratePaymentToken",
                    error = error,
                    userId = userId,
                    additionalInfo = mapOf(
                        "documentId" to documentId,
                        "responseCode" to response.code().toString()
                    )
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "PaymentsRepository.regeneratePaymentToken",
                error = e,
                userId = userId,
                additionalInfo = mapOf("documentId" to documentId)
            )
            Result.failure(e)
        }
    }

    /**
     * Build the payment URL for an invoice.
     */
    fun buildPaymentUrl(paymentToken: String): String {
        val serverUrl = securePrefs.getServerUrl() ?: ""
        // Convert API URL to web URL (e.g., https://api.example.com:8000 -> https://example.com:8080)
        val webUrl = serverUrl
            .replace("api.", "")
            .replace(":8000", ":8080")
            .replace("/rest/v1", "")
            .replace("/auth/v1", "")
            .trimEnd('/')
        return "$webUrl/pay/$paymentToken"
    }

    /**
     * Send invoice email to customer via edge function.
     */
    suspend fun sendInvoiceEmail(documentId: String): Result<Unit> {
        val userId = securePrefs.getUserId()
            ?: return Result.failure(Exception("User not logged in"))

        return try {
            val response = apiService.sendDocumentEmail(
                SendDocumentEmailRequest(documentId = documentId)
            )

            if (response.isSuccessful) {
                val result = response.body()
                if (result?.success == true) {
                    Result.success(Unit)
                } else {
                    val error = Exception(result?.error ?: "Failed to send email")
                    Result.failure(error)
                }
            } else {
                val error = Exception("Failed to send email: ${response.code()}")
                ErrorReporter.logAndReportError(
                    context = "PaymentsRepository.sendInvoiceEmail",
                    error = error,
                    userId = userId,
                    additionalInfo = mapOf(
                        "documentId" to documentId,
                        "responseCode" to response.code().toString()
                    )
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "PaymentsRepository.sendInvoiceEmail",
                error = e,
                userId = userId,
                additionalInfo = mapOf("documentId" to documentId)
            )
            Result.failure(e)
        }
    }
}
