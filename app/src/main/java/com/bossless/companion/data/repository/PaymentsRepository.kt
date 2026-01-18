package com.bossless.companion.data.repository

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.models.BusinessProfile
import com.bossless.companion.data.models.CreateDocumentItemRequest
import com.bossless.companion.data.models.InvoiceDocument
import com.bossless.companion.data.models.RecordManualPaymentRequest
import com.bossless.companion.data.models.RecordManualPaymentResponse
import com.bossless.companion.data.models.RegeneratePaymentTokenRequest
import com.bossless.companion.data.models.RegeneratePaymentTokenResponse
import com.bossless.companion.data.models.SendDocumentEmailRequest
import com.bossless.companion.data.models.SendDocumentEmailResponse
import com.bossless.companion.data.models.UpdateDocumentRequest
import com.bossless.companion.data.models.UpdateInvoiceLineItem
import com.bossless.companion.data.models.UpdateInvoiceResponse
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

    /**
     * Get business profile with payment details (bank info, etc.)
     */
    suspend fun getBusinessProfile(): Result<BusinessProfile> {
        return try {
            val response = apiService.getBusinessProfileWithFeatures()
            if (response.isSuccessful) {
                val profile = response.body()?.firstOrNull()
                if (profile != null) {
                    Result.success(profile)
                } else {
                    Result.failure(Exception("No business profile found"))
                }
            } else {
                Result.failure(Exception("Failed to fetch business profile: ${response.code()}"))
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "PaymentsRepository.getBusinessProfile",
                error = e
            )
            Result.failure(e)
        }
    }

    /**
     * Record a manual payment (Cash or EFT).
     * This creates a payment record and updates the invoice status.
     */
    suspend fun recordManualPayment(
        documentId: String,
        amount: Double,
        paymentMethod: String
    ): Result<RecordManualPaymentResponse> {
        val userId = securePrefs.getUserId()
            ?: return Result.failure(Exception("User not logged in"))

        return try {
            val response = apiService.recordManualPayment(
                RecordManualPaymentRequest(
                    documentId = documentId,
                    amount = amount,
                    paymentMethod = paymentMethod
                )
            )

            if (response.isSuccessful) {
                val result = response.body()
                if (result != null && result.success) {
                    Result.success(result)
                } else {
                    val error = Exception(result?.error ?: "Failed to record payment")
                    Result.failure(error)
                }
            } else {
                val error = Exception("Failed to record payment: ${response.code()}")
                ErrorReporter.logAndReportError(
                    context = "PaymentsRepository.recordManualPayment",
                    error = error,
                    userId = userId,
                    additionalInfo = mapOf(
                        "documentId" to documentId,
                        "amount" to amount.toString(),
                        "paymentMethod" to paymentMethod,
                        "responseCode" to response.code().toString()
                    )
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "PaymentsRepository.recordManualPayment",
                error = e,
                userId = userId,
                additionalInfo = mapOf(
                    "documentId" to documentId,
                    "amount" to amount.toString(),
                    "paymentMethod" to paymentMethod
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Update an invoice's line items using direct API calls.
     * Pattern: DELETE existing items → INSERT new items → PATCH document total
     * (Same pattern as PO creation/editing)
     */
    suspend fun updateInvoice(
        documentId: String,
        lineItems: List<UpdateInvoiceLineItem>,
        notes: String? = null
    ): Result<UpdateInvoiceResponse> {
        val userId = securePrefs.getUserId()
            ?: return Result.failure(Exception("User not logged in"))

        return try {
            // Step 1: Delete existing line items
            val deleteResponse = apiService.deleteDocumentItems(documentId = "eq.$documentId")
            if (!deleteResponse.isSuccessful && deleteResponse.code() != 404) {
                // 404 is OK - means no existing items
                return Result.failure(Exception("Failed to delete existing items: ${deleteResponse.code()}"))
            }

            // Step 2: Calculate totals and create new line items
            var subtotal = 0.0
            var totalGst = 0.0
            
            val itemRequests = lineItems.mapIndexed { index, item ->
                val itemSubtotal = item.quantity * item.unit_price
                val itemGst = itemSubtotal * (item.gst_rate / 100.0)
                val itemTotal = itemSubtotal + itemGst
                
                subtotal += itemSubtotal
                totalGst += itemGst
                
                CreateDocumentItemRequest(
                    document_id = documentId,
                    description = item.description,
                    quantity = item.quantity,
                    unit_price = item.unit_price,
                    gst_rate = item.gst_rate,
                    total = itemTotal
                )
            }
            
            val totalAmount = subtotal + totalGst

            // Step 3: Insert new line items
            if (itemRequests.isNotEmpty()) {
                val insertResponse = apiService.createDocumentItems(request = itemRequests)
                if (!insertResponse.isSuccessful) {
                    return Result.failure(Exception("Failed to create line items: ${insertResponse.code()}"))
                }
            }

            // Step 4: Update document total
            val updateRequest = UpdateDocumentRequest(
                total_amount = totalAmount,
                notes = notes
            )
            val updateResponse = apiService.updateDocument(
                id = "eq.$documentId",
                request = updateRequest
            )
            
            if (!updateResponse.isSuccessful) {
                return Result.failure(Exception("Failed to update document total: ${updateResponse.code()}"))
            }

            Result.success(UpdateInvoiceResponse(
                success = true,
                totalAmount = totalAmount,
                lineItemsCount = lineItems.size
            ))
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "PaymentsRepository.updateInvoice",
                error = e,
                userId = userId,
                additionalInfo = mapOf(
                    "documentId" to documentId,
                    "lineItemsCount" to lineItems.size.toString()
                )
            )
            Result.failure(e)
        }
    }
}
