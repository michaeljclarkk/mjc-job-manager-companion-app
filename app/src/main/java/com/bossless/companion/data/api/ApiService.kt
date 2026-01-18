package com.bossless.companion.data.api

import com.bossless.companion.data.models.*
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("auth/v1/token")
    suspend fun login(
        @Query("grant_type") grantType: String = "password",
        @Header("apikey") apiKey: String,
        @Body request: LoginRequest
    ): Response<AuthResponse>

    @POST("auth/v1/token")
    suspend fun refreshToken(
        @Query("grant_type") grantType: String = "refresh_token",
        @Header("apikey") apiKey: String,
        @Body request: RefreshTokenRequest
    ): Response<AuthResponse>

    // Jobs - PostgREST requires "eq." prefix for equality filters
    @GET("rest/v1/job_assignments")
    suspend fun getAssignedJobs(
        @Query("select") select: String = "*,jobs(*)",
        @Query("user_id") userId: String,  // Caller must prefix with "eq."
        @Header("Prefer") prefer: String = "count=none"
    ): Response<List<JobAssignment>>

    @GET("rest/v1/third_parties")
    suspend fun getThirdParty(
        @Query("id") id: String  // Caller must prefix with "eq."
    ): Response<List<ThirdParty>>

    @GET("rest/v1/job_updates")
    suspend fun getJobUpdates(
        @Query("job_id") jobId: String,  // Caller must prefix with "eq."
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 20
    ): Response<List<JobUpdate>>

    @POST("rest/v1/job_updates")
    suspend fun createJobUpdate(
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: CreateJobUpdateRequest
    ): Response<List<JobUpdate>>

    // Time Entries
    @POST("rest/v1/time_entries")
    suspend fun createTimeEntry(
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: CreateTimeEntryRequest
    ): Response<List<TimeEntry>>

    @POST("rest/v1/time_entries")
    suspend fun createFullTimeEntry(
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: CreateFullTimeEntryRequest
    ): Response<List<TimeEntry>>

    @PATCH("rest/v1/time_entries")
    suspend fun updateTimeEntry(
        @Header("Prefer") prefer: String = "return=representation",
        @Query("id") id: String,  // Caller must prefix with "eq."
        @Body request: UpdateTimeEntryRequest
    ): Response<List<TimeEntry>>

    @GET("rest/v1/time_entries")
    suspend fun getActiveTimeEntry(
        @Query("user_id") userId: String,  // Caller must prefix with "eq."
        @Query("finish_time") finishTime: String = "is.null",
        @Query("order") order: String = "start_time.desc",
        @Query("limit") limit: Int = 1
    ): Response<List<TimeEntry>>

    @GET("rest/v1/time_entries")
    suspend fun getTimeEntriesForJob(
        @Query("job_id") jobId: String,  // Caller must prefix with "eq."
        @Query("order") order: String = "start_time.desc"
    ): Response<List<TimeEntry>>

    @DELETE("rest/v1/time_entries")
    suspend fun deleteTimeEntry(
        @Query("id") id: String  // Caller must prefix with "eq."
    ): Response<Unit>

    @GET("rest/v1/jobs")
    suspend fun getJob(
        @Query("id") id: String  // Caller must prefix with "eq."
    ): Response<List<Job>>

    // Notifications
    @GET("rest/v1/notifications")
    suspend fun getNotifications(
        @Query("user_id") userId: String,  // Caller must prefix with "eq."
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): Response<List<Notification>>

    @PATCH("rest/v1/notifications")
    suspend fun markNotificationRead(
        @Query("id") id: String,  // Caller must prefix with "eq."
        @Body request: UpdateNotificationRequest
    ): Response<Unit>
    
    @PATCH("rest/v1/notifications")
    suspend fun markAllNotificationsRead(
        @Query("user_id") userId: String,  // Caller must prefix with "eq."
        @Body request: UpdateNotificationRequest
    ): Response<Unit>
    
    @DELETE("rest/v1/notifications")
    suspend fun deleteNotification(
        @Query("id") id: String  // Caller must prefix with "eq."
    ): Response<Unit>
    
    @DELETE("rest/v1/notifications")
    suspend fun deleteAllNotifications(
        @Query("user_id") userId: String  // Caller must prefix with "eq."
    ): Response<Unit>

    // Job Documents
    @POST("rest/v1/job_documents")
    suspend fun createJobDocument(
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: CreateJobDocumentRequest
    ): Response<List<JobDocument>>

    @Multipart
    @POST("storage/v1/object/job-documents/{path}")
    suspend fun uploadJobDocument(
        @Path("path") path: String,
        @Part file: okhttp3.MultipartBody.Part
    ): Response<Unit>

    // Suppliers (Third Parties)
    @GET("rest/v1/third_parties")
    suspend fun getSuppliers(
        @Query("type") type: String = "eq.supplier",
        @Query("select") select: String = "id,name,type,email,phone",
        @Query("order") order: String = "name.asc"
    ): Response<List<ThirdParty>>

    @GET("rest/v1/third_parties")
    suspend fun getThirdPartyById(
        @Query("id") id: String,  // Caller must prefix with "eq."
        @Query("select") select: String = "id,name,type,email,phone"
    ): Response<List<ThirdParty>>

    @POST("rest/v1/third_parties")
    suspend fun createSupplier(
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: CreateSupplierRequest
    ): Response<List<ThirdParty>>

    // Business Profile (for document prefix)
    @GET("rest/v1/business_profiles")
    suspend fun getBusinessProfile(
        @Query("select") select: String = "document_prefix,business_name,logo_url,business_hours",
        @Query("limit") limit: Int = 1
    ): Response<List<BusinessProfile>>

    // Documents (Purchase Orders)
    @GET("rest/v1/documents")
    suspend fun getLatestPurchaseOrder(
        @Query("type") type: String = "eq.purchase_order",
        @Query("order") order: String = "document_number.desc",
        @Query("limit") limit: Int = 1
    ): Response<List<Document>>

    @GET("rest/v1/documents")
    suspend fun getPurchaseOrdersForJob(
        @Query("job_id") jobId: String,  // Caller must prefix with "eq."
        @Query("type") type: String = "eq.purchase_order",
        @Query("select") select: String = "id,document_number,type,total_amount,status,issue_date,third_party_id,created_at",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<Document>>

    @POST("rest/v1/documents")
    suspend fun createDocument(
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: CreateDocumentRequest
    ): Response<List<Document>>

    @PATCH("rest/v1/documents")
    suspend fun updateDocument(
        @Query("id") id: String,  // Caller must prefix with "eq."
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: UpdateDocumentRequest
    ): Response<List<Document>>

    // Document Items (line items for PO)
    @POST("rest/v1/document_items")
    suspend fun createDocumentItems(
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: List<CreateDocumentItemRequest>
    ): Response<List<DocumentItem>>

    @DELETE("rest/v1/document_items")
    suspend fun deleteDocumentItems(
        @Query("document_id") documentId: String  // Caller must prefix with "eq."
    ): Response<Unit>

    @GET("rest/v1/document_items")
    suspend fun getDocumentItems(
        @Query("document_id") documentId: String,  // Caller must prefix with "eq."
        @Query("select") select: String = "id,document_id,description,quantity,unit_price,gst_rate,total",
        @Query("order") order: String = "created_at.asc"
    ): Response<List<DocumentItem>>

    // Job PO Numbers (link PO to job)
    @POST("rest/v1/job_po_numbers")
    suspend fun createJobPoNumber(
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: CreateJobPoNumberRequest
    ): Response<List<JobPoNumber>>

    @PATCH("rest/v1/job_po_numbers")
    suspend fun updateJobPoNumber(
        @Query("source_document_id") sourceDocumentId: String,  // Caller must prefix with "eq."
        @Body request: UpdateJobPoNumberRequest
    ): Response<Unit>

    @DELETE("rest/v1/job_po_numbers")
    suspend fun deleteJobPoNumbers(
        @Query("source_document_id") sourceDocumentId: String  // Caller must prefix with "eq."
    ): Response<Unit>

    @DELETE("rest/v1/job_documents")
    suspend fun deleteJobDocumentsByDocumentId(
        @Query("document_id") documentId: String  // Caller must prefix with "eq."
    ): Response<Unit>

    @DELETE("rest/v1/documents")
    suspend fun deleteDocument(
        @Query("id") id: String  // Caller must prefix with "eq."
    ): Response<Unit>

    // Location Tracking
    @POST("rest/v1/user_locations")
    suspend fun createUserLocation(
        @Header("Prefer") prefer: String = "return=representation",
        @Body request: CreateUserLocationRequest
    ): Response<List<UserLocation>>

    // Daily Schedule - Job assignments with planned times
    @GET("rest/v1/job_assignments")
    suspend fun getTodaysSchedule(
        @Query("select") select: String = "id,job_id,user_id,planned_date,planned_start_time,planned_finish_time,jobs(id,job_number,name,status,location,latitude,longitude)",
        @Query("user_id") userId: String,  // Caller must prefix with "eq."
        @Query("planned_date") plannedDate: String,  // Caller must prefix with "eq."
        @Query("order") order: String = "planned_start_time.asc.nullslast"
    ): Response<List<com.bossless.companion.data.models.ScheduledJobAssignment>>

    // Business Profile with feature flags and payment details
    @GET("rest/v1/business_profiles")
    suspend fun getBusinessProfileWithFeatures(
        @Query("select") select: String = "document_prefix,business_name,logo_url,business_hours,feature_scheduler,feature_stripe_payments,enable_stripe_payments,bsb,account_number,account_name,payid,bpay_biller_code,bpay_reference",
        @Query("limit") limit: Int = 1
    ): Response<List<com.bossless.companion.data.models.BusinessProfile>>

    // ============== Invoice Payments ==============

    /**
     * Get invoices for a specific job.
     * Filters to unpaid statuses by default.
     */
    @GET("rest/v1/documents")
    suspend fun getInvoicesForJob(
        @Query("job_id") jobId: String,  // Caller must prefix with "eq."
        @Query("type") type: String = "eq.invoice",
        @Query("status") status: String = "in.(draft,validated,sent,overdue)",
        @Query("select") select: String = "id,document_number,type,total_amount,status,issue_date,due_date,third_party_id,payment_token,payment_token_expires_at,payment_token_invalidated_at,total_paid,paid_at,created_at,third_parties(id,name,type,email,phone),document_items(id,document_id,description,quantity,unit_price,gst_rate,total)",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<com.bossless.companion.data.models.InvoiceDocument>>

    /**
     * Regenerate payment token for an invoice via edge function.
     */
    @POST("functions/v1/regenerate-payment-token")
    suspend fun regeneratePaymentToken(
        @Body request: com.bossless.companion.data.models.RegeneratePaymentTokenRequest
    ): Response<com.bossless.companion.data.models.RegeneratePaymentTokenResponse>

    /**
     * Send document (invoice) email via edge function.
     */
    @POST("functions/v1/send-document-email")
    suspend fun sendDocumentEmail(
        @Body request: com.bossless.companion.data.models.SendDocumentEmailRequest
    ): Response<com.bossless.companion.data.models.SendDocumentEmailResponse>

    /**
     * Record a manual payment (Cash/EFT) via edge function.
     */
    @POST("functions/v1/record-manual-payment")
    suspend fun recordManualPayment(
        @Body request: com.bossless.companion.data.models.RecordManualPaymentRequest
    ): Response<com.bossless.companion.data.models.RecordManualPaymentResponse>
}
