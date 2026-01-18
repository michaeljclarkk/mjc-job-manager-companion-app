package com.bossless.companion.data.models

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class AuthResponse(
    val access_token: String,
    val refresh_token: String,
    val expires_in: Int,
    val user: User
)

@Serializable
data class User(
    val id: String,
    val email: String? = null,
    val user_metadata: UserMetadata? = null
)

@Serializable
data class UserMetadata(
    val first_name: String? = null,
    val last_name: String? = null
)

@Serializable
data class Job(
    val id: String,
    val job_number: String,
    val name: String? = null,
    val status: String, // pending, in_progress, completed, on_hold, cancelled
    val location: String? = null,
    val description: String? = null,
    val scheduled_date: String? = null,
    val third_party_id: String? = null,
    val timer_start: String? = null,
    val timer_duration: Int? = null,
    val created_at: String,
    val updated_at: String
)

@Serializable
data class JobAssignment(
    val id: String,
    val job_id: String,
    val user_id: String,
    val role: String? = null,
    val jobs: Job? = null // Embedded when using select=*,jobs(*)
)

@Serializable
data class TimeEntry(
    val id: String,
    val job_id: String,
    val user_id: String,
    val start_time: String,
    val finish_time: String? = null,
    val start_note: String? = null,
    val finish_note: String? = null,
    val duration_seconds: Int = 0,
    val created_at: String
)

@Serializable
data class Notification(
    val id: String,
    val user_id: String,
    val type: String,
    val title: String,
    val message: String? = null,
    val reference_id: String? = null,
    val reference_type: String? = null,
    val read: Boolean = false,
    val created_at: String
)

@Serializable
data class JobUpdate(
    val id: String,
    val job_id: String,
    val author_id: String? = null,
    val content: JsonObject? = null,
    val content_html: String? = null,
    val has_checklist: Boolean = false,
    val created_at: String
)

@Serializable
data class ThirdParty(
    val id: String,
    val name: String,
    val type: String? = null, // customer, supplier, subcontractor - nullable for partial selects
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshTokenRequest(
    val refresh_token: String
)

@Serializable
data class CreateTimeEntryRequest(
    val job_id: String,
    val user_id: String,
    val start_time: String,
    val start_note: String? = null
)

@Serializable
data class UpdateTimeEntryRequest(
    val finish_time: String? = null,
    val finish_note: String? = null,
    val duration_seconds: Int? = null,
    val start_time: String? = null,
    val start_note: String? = null
)

@Serializable
data class CreateFullTimeEntryRequest(
    val job_id: String,
    val user_id: String,
    val start_time: String,
    val finish_time: String,
    val duration_seconds: Int,
    val start_note: String? = null
)

@Serializable
data class CreateJobUpdateRequest(
    val job_id: String,
    val author_id: String,
    val content: JsonObject? = null,
    val content_html: String? = null,
    val has_checklist: Boolean = false
)

@Serializable
data class UpdateNotificationRequest(
    val read: Boolean
)

@Serializable
data class JobDocument(
    val id: String,
    val job_id: String,
    val document_id: String? = null, // Storage path/ID
    val file_url: String? = null,
    val file_name: String,
    val document_type: String = "other",
    val created_at: String
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateJobDocumentRequest(
    val job_id: String,
    val file_name: String,
    val file_url: String,
    @EncodeDefault val document_type: String = "image",
    val document_id: String? = null
)

// ============== Purchase Order Models ==============

@Serializable
data class BusinessProfile(
    val document_prefix: String? = null,
    val business_name: String? = null,
    val logo_url: String? = null,
    val business_hours: JsonObject? = null,
    val feature_scheduler: Boolean? = null,
    val feature_stripe_payments: Boolean? = null,
    val enable_stripe_payments: Boolean? = null,
    val feature_invoice_editing: Boolean? = null,
    // Bank details for EFT payments
    val bsb: String? = null,
    val account_number: String? = null,
    val account_name: String? = null,
    val payid: String? = null,
    val bpay_biller_code: String? = null,
    val bpay_reference: String? = null
)

// ============== Daily Schedule Models ==============

/**
 * A job assignment with scheduled time for the Daily Schedule feature.
 * Fetched from job_assignments with joined job details.
 */
@Serializable
data class ScheduledJobAssignment(
    val id: String,
    val job_id: String,
    val user_id: String,
    val planned_date: String? = null,
    val planned_start_time: String? = null,  // HH:mm format
    val planned_finish_time: String? = null, // HH:mm format
    val jobs: ScheduledJobDetails? = null
)

@Serializable
data class ScheduledJobDetails(
    val id: String,
    val job_number: String,
    val name: String? = null,
    val status: String,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class Document(
    val id: String,
    val document_number: String,
    val type: String,
    val total_amount: Double? = null,
    val status: String? = null,
    val issue_date: String? = null,
    val third_party_id: String? = null,
    val created_at: String
)

// ============== Invoice Payment Models ==============

/**
 * Extended document model for invoices with payment token fields.
 * Used by the Payments feature to display invoices and generate payment links.
 */
@Serializable
data class InvoiceDocument(
    val id: String,
    val document_number: String,
    val type: String,
    val total_amount: Double? = null,
    val status: String? = null,
    val issue_date: String? = null,
    val due_date: String? = null,
    val third_party_id: String? = null,
    val payment_token: String? = null,
    val payment_token_expires_at: String? = null,
    val payment_token_invalidated_at: String? = null,
    val total_paid: Double? = null,
    val paid_at: String? = null,
    val created_at: String,
    val third_parties: ThirdParty? = null,  // Embedded when using select with join
    val document_items: List<DocumentItem>? = null  // Line items for invoice preview
)

/**
 * Response from regenerate-payment-token edge function
 */
@Serializable
data class RegeneratePaymentTokenResponse(
    val success: Boolean,
    val paymentToken: String? = null,
    val paymentUrl: String? = null,
    val error: String? = null
)

/**
 * Request to regenerate a payment token
 */
@Serializable
data class RegeneratePaymentTokenRequest(
    val documentId: String
)

/**
 * Request to send a document email
 */
@Serializable
data class SendDocumentEmailRequest(
    val documentId: String
)

/**
 * Response from send-document-email edge function
 */
@Serializable
data class SendDocumentEmailResponse(
    val success: Boolean,
    val error: String? = null
)

/**
 * Request to record a manual payment (Cash/EFT)
 */
@Serializable
data class RecordManualPaymentRequest(
    val documentId: String,
    val amount: Double,
    val paymentMethod: String  // "Cash", "EFT", etc.
)

/**
 * Response from record-manual-payment edge function
 */
@Serializable
data class RecordManualPaymentResponse(
    val success: Boolean,
    val newStatus: String? = null,
    val error: String? = null
)

/**
 * Line item for update-invoice request
 */
@Serializable
data class UpdateInvoiceLineItem(
    val description: String,
    val quantity: Double,
    val unit_price: Double,
    val gst_rate: Double
)

/**
 * Request to update an invoice's line items
 */
@Serializable
data class UpdateInvoiceRequest(
    val documentId: String,
    val lineItems: List<UpdateInvoiceLineItem>,
    val notes: String? = null
)

/**
 * Response from update-invoice edge function
 */
@Serializable
data class UpdateInvoiceResponse(
    val success: Boolean,
    val totalAmount: Double? = null,
    val lineItemsCount: Int? = null,
    val error: String? = null
)

@Serializable
data class DocumentItem(
    val id: String,
    val document_id: String,
    val description: String,
    val quantity: Double,
    val unit_price: Double,
    val gst_rate: Double,
    val total: Double
)

@Serializable
data class JobPoNumber(
    val id: String,
    val job_id: String,
    val po_number: String,
    val po_type: String,
    val created_at: String
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateSupplierRequest(
    val user_id: String,
    val name: String,
    @EncodeDefault val type: String = "supplier",
    val email: String? = null,
    val phone: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateDocumentRequest(
    val user_id: String,
    val document_number: String,
    @EncodeDefault val type: String = "purchase_order",
    val job_id: String,
    val third_party_id: String? = null,
    val amount: Double,
    val gst_amount: Double,
    val total_amount: Double,
    @EncodeDefault val status: String = "draft",
    val issue_date: String,
    val notes: String? = null
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateDocumentItemRequest(
    val document_id: String,
    val description: String,
    val quantity: Double,
    val unit_price: Double,
    @EncodeDefault val gst_rate: Double = 10.0,
    val total: Double
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateJobPoNumberRequest(
    val job_id: String,
    val user_id: String,
    val po_number: String,
    @EncodeDefault val po_type: String = "supplier",
    val source_document_id: String,
    val third_party_id: String? = null
)

@Serializable
data class UpdateDocumentRequest(
    val third_party_id: String? = null,
    val amount: Double? = null,
    val gst_amount: Double? = null,
    val total_amount: Double? = null,
    val notes: String? = null
)

@Serializable
data class UpdateJobPoNumberRequest(
    val third_party_id: String? = null
)

// Local form state for line items in PO dialog
// Note: Purchase orders do not include GST (gstRate = 0)
data class PurchaseOrderLineItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val description: String = "",
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val gstRate: Double = 0.0  // POs don't include GST
) {
    val subtotal: Double get() = quantity * unitPrice
    val gstAmount: Double get() = subtotal * (gstRate / 100)
    val total: Double get() = subtotal + gstAmount
}

// ============== Location Tracking Models ==============

@Serializable
data class UserLocation(
    val id: String,
    val user_id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val heading: Float? = null,
    val altitude: Float? = null,
    val distance_delta_meters: Float = 0f,
    val recorded_at: String,
    val created_at: String
)

@Serializable
data class CreateUserLocationRequest(
    val user_id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    val speed: Float? = null,
    val heading: Float? = null,
    val altitude: Float? = null,
    val distance_delta_meters: Float = 0f,
    val recorded_at: String
)
