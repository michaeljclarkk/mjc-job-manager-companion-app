package com.bossless.companion.data.repository

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.models.*
import com.bossless.companion.utils.ErrorReporter
import com.bossless.companion.BuildConfig
import android.util.Log
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseOrderRepository @Inject constructor(
    private val apiService: ApiService,
    private val securePrefs: SecurePrefs
) {
    /**
     * Fetch all suppliers from third_parties table
     */
    suspend fun getSuppliers(): Result<List<ThirdParty>> {
        return try {
            if (BuildConfig.DEBUG) Log.d("PORepo", "getSuppliers() calling API...")
            val response = apiService.getSuppliers()
            if (BuildConfig.DEBUG) {
                Log.d("PORepo", "getSuppliers() response: code=${response.code()}, success=${response.isSuccessful}")
            }
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                val error = Exception("Failed to fetch suppliers: ${response.code()}")
                ErrorReporter.logAndReportError(
                    context = "PurchaseOrderRepository.getSuppliers",
                    error = error,
                    userId = securePrefs.getUserId()
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            Log.e("PORepo", "getSuppliers() exception", e)
            ErrorReporter.logAndReportError(
                context = "PurchaseOrderRepository.getSuppliers",
                error = e,
                userId = securePrefs.getUserId()
            )
            Result.failure(e)
        }
    }

    /**
     * Create a new supplier with minimal info (just name required)
     */
    suspend fun createSupplier(name: String, email: String?, phone: String?): Result<ThirdParty> {
        val userId = securePrefs.getUserId() 
            ?: return Result.failure(Exception("User not authenticated"))
        
        return try {
            val request = CreateSupplierRequest(
                user_id = userId,
                name = name,
                email = email,
                phone = phone
            )
            
            val response = apiService.createSupplier(request = request)
            if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                Result.success(response.body()!!.first())
            } else {
                val error = Exception("Failed to create supplier: ${response.code()}")
                ErrorReporter.logAndReportError(
                    context = "PurchaseOrderRepository.createSupplier",
                    error = error,
                    userId = userId,
                    additionalInfo = mapOf("supplierName" to name)
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "PurchaseOrderRepository.createSupplier",
                error = e,
                userId = userId,
                additionalInfo = mapOf("supplierName" to name)
            )
            Result.failure(e)
        }
    }

    /**
     * Generate a unique PO number following the pattern: {PREFIX}-PO-{NNNN}-{TIMESTAMP}
     */
    suspend fun generatePONumber(): Result<String> {
        return try {
            // Get business profile for prefix
            val profileResponse = apiService.getBusinessProfile()
            val prefix = if (profileResponse.isSuccessful && profileResponse.body()?.isNotEmpty() == true) {
                val profile = profileResponse.body()!!.first()
                profile.document_prefix ?: "PO"
            } else {
                "PO"
            }

            // Get latest PO number to determine sequence
            val latestResponse = apiService.getLatestPurchaseOrder()
            var nextNumber = 1
            
            if (latestResponse.isSuccessful && latestResponse.body()?.isNotEmpty() == true) {
                val latestDoc = latestResponse.body()!!.first()
                // Parse number from format: PREFIX-PO-0001-1234
                val parts = latestDoc.document_number.split("-")
                if (parts.size >= 3) {
                    // The sequence number is the third part (index 2) for PREFIX-PO-NNNN-TIMESTAMP
                    // or second part (index 1) for PO-NNNN-TIMESTAMP
                    val numPart = parts.find { it.all { c -> c.isDigit() } && it.length == 4 }
                    numPart?.toIntOrNull()?.let { nextNumber = it + 1 }
                }
            }

            // Add timestamp suffix for uniqueness
            val timestamp = System.currentTimeMillis().toString().takeLast(4)
            val poNumber = "$prefix-PO-${String.format("%04d", nextNumber)}-$timestamp"
            
            Result.success(poNumber)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a purchase order with line items and link to job
     */
    suspend fun createPurchaseOrder(
        jobId: String,
        supplierId: String?,
        lineItems: List<PurchaseOrderLineItem>,
        notes: String?
    ): Result<String> {
        return try {
            val userId = securePrefs.getUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // Generate PO number
            val poNumberResult = generatePONumber()
            if (poNumberResult.isFailure) {
                return Result.failure(poNumberResult.exceptionOrNull() ?: Exception("Failed to generate PO number"))
            }
            val poNumber = poNumberResult.getOrThrow()

            // Calculate totals
            val subtotal = lineItems.sumOf { it.subtotal }
            val gstAmount = lineItems.sumOf { it.gstAmount }
            val totalAmount = lineItems.sumOf { it.total }

            // Create document
            val issueDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val documentRequest = CreateDocumentRequest(
                user_id = userId,
                document_number = poNumber,
                type = "purchase_order",
                job_id = jobId,
                third_party_id = supplierId,
                amount = subtotal,
                gst_amount = gstAmount,
                total_amount = totalAmount,
                issue_date = issueDate,
                notes = notes
            )

            val docResponse = apiService.createDocument(request = documentRequest)
            if (!docResponse.isSuccessful || docResponse.body().isNullOrEmpty()) {
                return Result.failure(Exception("Failed to create document: ${docResponse.code()}"))
            }
            val document = docResponse.body()!!.first()

            // Create line items
            val itemRequests = lineItems.map { item ->
                CreateDocumentItemRequest(
                    document_id = document.id,
                    description = item.description,
                    quantity = item.quantity,
                    unit_price = item.unitPrice,
                    gst_rate = item.gstRate,
                    total = item.total
                )
            }

            val itemsResponse = apiService.createDocumentItems(request = itemRequests)
            if (!itemsResponse.isSuccessful) {
                android.util.Log.w("PurchaseOrderRepository", "Failed to create line items: ${itemsResponse.code()}")
                // Continue anyway - document is created
            }

            // Link PO to job via job_po_numbers
            val poNumberRequest = CreateJobPoNumberRequest(
                job_id = jobId,
                user_id = userId,
                po_number = poNumber,
                po_type = "supplier",
                source_document_id = document.id,
                third_party_id = supplierId
            )

            val poLinkResponse = apiService.createJobPoNumber(request = poNumberRequest)
            if (!poLinkResponse.isSuccessful) {
                android.util.Log.w("PurchaseOrderRepository", "Failed to link PO to job: ${poLinkResponse.code()}")
                // Continue anyway - document is created
            }

            // Also create job_documents entry for JobDocumentsPool visibility
            // Use empty file_url - the web frontend will generate PDF on-demand
            val jobDocRequest = CreateJobDocumentRequest(
                job_id = jobId,
                file_url = "", // No physical file - web will generate PDF on-demand
                file_name = poNumber,
                document_type = "purchase_order",
                document_id = document.id
            )

            val jobDocResponse = apiService.createJobDocument(request = jobDocRequest)
            if (!jobDocResponse.isSuccessful) {
                android.util.Log.w("PurchaseOrderRepository", "Failed to create job_document link: ${jobDocResponse.code()}")
                // Continue anyway - document is created
            }

            Result.success(poNumber)
        } catch (e: Exception) {
            android.util.Log.e("PurchaseOrderRepository", "Failed to create PO", e)
            ErrorReporter.logAndReportError(
                context = "PurchaseOrderRepository.createPurchaseOrder",
                error = e,
                userId = securePrefs.getUserId(),
                additionalInfo = mapOf(
                    "jobId" to jobId,
                    "supplierId" to (supplierId ?: "none"),
                    "lineItemCount" to lineItems.size.toString()
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Get all purchase orders for a specific job
     */
    suspend fun getPurchaseOrdersForJob(jobId: String): Result<List<Document>> {
        return try {
            val response = apiService.getPurchaseOrdersForJob(jobId = "eq.$jobId")
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch POs: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a draft purchase order immediately (no line items required)
     * Returns the Document with its PO number so user can give it to supplier right away
     */
    suspend fun createDraftPurchaseOrder(jobId: String): Result<Document> {
        return try {
            val userId = securePrefs.getUserId()
                ?: return Result.failure(Exception("User not authenticated"))

            // Generate PO number
            val poNumberResult = generatePONumber()
            if (poNumberResult.isFailure) {
                return Result.failure(poNumberResult.exceptionOrNull() ?: Exception("Failed to generate PO number"))
            }
            val poNumber = poNumberResult.getOrThrow()

            // Create document with draft status and zero amounts
            val issueDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val documentRequest = CreateDocumentRequest(
                user_id = userId,
                document_number = poNumber,
                type = "purchase_order",
                job_id = jobId,
                third_party_id = null,
                amount = 0.0,
                gst_amount = 0.0,
                total_amount = 0.0,
                issue_date = issueDate,
                notes = null
            )

            val docResponse = apiService.createDocument(request = documentRequest)
            if (!docResponse.isSuccessful || docResponse.body().isNullOrEmpty()) {
                return Result.failure(Exception("Failed to create document: ${docResponse.code()}"))
            }
            val document = docResponse.body()!!.first()

            // Link PO to job via job_po_numbers
            val poNumberRequest = CreateJobPoNumberRequest(
                job_id = jobId,
                user_id = userId,
                po_number = poNumber,
                po_type = "supplier",
                source_document_id = document.id,
                third_party_id = null
            )

            val poLinkResponse = apiService.createJobPoNumber(request = poNumberRequest)
            if (!poLinkResponse.isSuccessful) {
                android.util.Log.w("PurchaseOrderRepository", "Failed to link PO to job: ${poLinkResponse.code()}")
            }

            // Create job_documents entry for visibility
            val jobDocRequest = CreateJobDocumentRequest(
                job_id = jobId,
                file_url = "",
                file_name = poNumber,
                document_type = "purchase_order",
                document_id = document.id
            )

            val jobDocResponse = apiService.createJobDocument(request = jobDocRequest)
            if (!jobDocResponse.isSuccessful) {
                android.util.Log.w("PurchaseOrderRepository", "Failed to create job_document link: ${jobDocResponse.code()}")
            }

            Result.success(document)
        } catch (e: Exception) {
            android.util.Log.e("PurchaseOrderRepository", "Failed to create draft PO", e)
            ErrorReporter.logAndReportError(
                context = "PurchaseOrderRepository.createDraftPurchaseOrder",
                error = e,
                userId = securePrefs.getUserId(),
                additionalInfo = mapOf("jobId" to jobId)
            )
            Result.failure(e)
        }
    }

    /**
     * Update an existing purchase order with supplier, line items, and notes
     */
    suspend fun updatePurchaseOrder(
        documentId: String,
        supplierId: String?,
        lineItems: List<PurchaseOrderLineItem>,
        notes: String?
    ): Result<Document> {
        return try {
            // Calculate totals
            val subtotal = lineItems.sumOf { it.subtotal }
            val gstAmount = lineItems.sumOf { it.gstAmount }
            val totalAmount = lineItems.sumOf { it.total }

            // Update document
            val updateRequest = UpdateDocumentRequest(
                third_party_id = supplierId,
                amount = subtotal,
                gst_amount = gstAmount,
                total_amount = totalAmount,
                notes = notes
            )
            
            val docResponse = apiService.updateDocument(
                id = "eq.$documentId",
                request = updateRequest
            )
            
            if (!docResponse.isSuccessful || docResponse.body().isNullOrEmpty()) {
                return Result.failure(Exception("Failed to update document: ${docResponse.code()}"))
            }
            val document = docResponse.body()!!.first()

            // Delete existing line items
            apiService.deleteDocumentItems(documentId = "eq.$documentId")

            // Create new line items
            if (lineItems.isNotEmpty()) {
                val itemRequests = lineItems.map { item ->
                    CreateDocumentItemRequest(
                        document_id = documentId,
                        description = item.description,
                        quantity = item.quantity,
                        unit_price = item.unitPrice,
                        gst_rate = item.gstRate,
                        total = item.total
                    )
                }

                val itemsResponse = apiService.createDocumentItems(request = itemRequests)
                if (!itemsResponse.isSuccessful) {
                    android.util.Log.w("PurchaseOrderRepository", "Failed to create line items: ${itemsResponse.code()}")
                }
            }

            // Update job_po_numbers with supplier if changed
            if (supplierId != null) {
                apiService.updateJobPoNumber(
                    sourceDocumentId = "eq.$documentId",
                    request = UpdateJobPoNumberRequest(third_party_id = supplierId)
                )
            }

            Result.success(document)
        } catch (e: Exception) {
            android.util.Log.e("PurchaseOrderRepository", "Failed to update PO", e)
            ErrorReporter.logAndReportError(
                context = "PurchaseOrderRepository.updatePurchaseOrder",
                error = e,
                userId = securePrefs.getUserId(),
                additionalInfo = mapOf(
                    "documentId" to documentId,
                    "supplierId" to (supplierId ?: "none"),
                    "lineItemCount" to lineItems.size.toString()
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Delete an existing purchase order, but only if it is empty (no line items) and totals are $0.
     * This is used to avoid accidental deletion.
     */
    suspend fun deletePurchaseOrderIfEmpty(
        documentId: String,
        totalAmount: Double?
    ): Result<Unit> {
        return try {
            val total = totalAmount ?: 0.0
            if (total != 0.0) {
                return Result.failure(Exception("Only $0 purchase orders can be deleted"))
            }

            val itemsResponse = apiService.getDocumentItems(documentId = "eq.$documentId")
            if (!itemsResponse.isSuccessful) {
                return Result.failure(Exception("Failed to verify PO items: ${itemsResponse.code()}"))
            }
            val items = itemsResponse.body() ?: emptyList()
            if (items.isNotEmpty()) {
                return Result.failure(Exception("Remove all line items before deleting this PO"))
            }

            // Delete dependent rows first (FK safety)
            val deleteItemsResponse = apiService.deleteDocumentItems(documentId = "eq.$documentId")
            if (!deleteItemsResponse.isSuccessful) {
                return Result.failure(Exception("Failed to delete PO items: ${deleteItemsResponse.code()}"))
            }

            val deleteJobDocsResponse = apiService.deleteJobDocumentsByDocumentId(documentId = "eq.$documentId")
            if (!deleteJobDocsResponse.isSuccessful) {
                return Result.failure(Exception("Failed to delete job document links: ${deleteJobDocsResponse.code()}"))
            }

            val deletePoNumbersResponse = apiService.deleteJobPoNumbers(sourceDocumentId = "eq.$documentId")
            if (!deletePoNumbersResponse.isSuccessful) {
                return Result.failure(Exception("Failed to delete PO links: ${deletePoNumbersResponse.code()}"))
            }

            val deleteDocResponse = apiService.deleteDocument(id = "eq.$documentId")
            if (!deleteDocResponse.isSuccessful) {
                return Result.failure(Exception("Failed to delete purchase order: ${deleteDocResponse.code()}"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("PurchaseOrderRepository", "Failed to delete PO", e)
            ErrorReporter.logAndReportError(
                context = "PurchaseOrderRepository.deletePurchaseOrderIfEmpty",
                error = e,
                userId = securePrefs.getUserId(),
                additionalInfo = mapOf(
                    "documentId" to documentId,
                    "totalAmount" to (totalAmount?.toString() ?: "null")
                )
            )
            Result.failure(e)
        }
    }

    /**
     * Get line items for a specific document
     */
    suspend fun getDocumentItems(documentId: String): Result<List<DocumentItem>> {
        return try {
            val response = apiService.getDocumentItems(documentId = "eq.$documentId")
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to fetch document items: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get third party (supplier/customer) by ID
     */
    suspend fun getThirdPartyById(thirdPartyId: String): Result<ThirdParty?> {
        return try {
            val response = apiService.getThirdPartyById(id = "eq.$thirdPartyId")
            if (response.isSuccessful) {
                Result.success(response.body()?.firstOrNull())
            } else {
                Result.failure(Exception("Failed to fetch third party: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
