package com.bossless.companion.ui.screens.jobdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import android.util.Log
import com.bossless.companion.BuildConfig
import com.bossless.companion.data.models.Job
import com.bossless.companion.data.models.JobUpdate
import com.bossless.companion.data.models.ThirdParty
import com.bossless.companion.data.models.TimeEntry
import com.bossless.companion.data.models.Document
import com.bossless.companion.data.models.DocumentItem
import com.bossless.companion.data.models.PurchaseOrderLineItem
import com.bossless.companion.data.models.InvoiceDocument
import com.bossless.companion.data.models.BusinessProfile
import com.bossless.companion.data.models.UpdateInvoiceLineItem
import com.bossless.companion.data.repository.JobsRepository
import com.bossless.companion.data.repository.JobDetailRepository
import com.bossless.companion.data.models.JobDocument
import com.bossless.companion.data.repository.JobDocumentsRepository
import com.bossless.companion.data.repository.TimeEntriesRepositoryOffline
import com.bossless.companion.data.repository.PurchaseOrderRepository
import com.bossless.companion.data.repository.PaymentsRepository
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.service.TimerService
import com.bossless.companion.utils.NetworkUtils
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

@HiltViewModel
class JobDetailViewModel @Inject constructor(
    private val jobsRepository: JobsRepository,
    private val jobDetailRepository: JobDetailRepository,
    private val timeEntriesRepositoryOffline: TimeEntriesRepositoryOffline,
    private val timeEntriesRepository: com.bossless.companion.data.repository.TimeEntriesRepository,
    private val jobDocumentsRepository: JobDocumentsRepository,
    private val purchaseOrderRepository: PurchaseOrderRepository,
    private val paymentsRepository: PaymentsRepository,
    private val networkUtils: NetworkUtils,
    private val securePrefs: SecurePrefs,
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d("JobDetailViewModel", message)
    }

    private val jobId: String = checkNotNull(savedStateHandle["jobId"])

    private val _uiState = MutableStateFlow(JobDetailUiState(
        serverUrl = securePrefs.getServerUrl() ?: "",
        authToken = securePrefs.getAccessToken() ?: ""
    ))
    val uiState: StateFlow<JobDetailUiState> = _uiState.asStateFlow()

    init {
        observeJobDetail()
        loadJobDetails()
        loadTimeEntries()
        loadDocuments()
        loadPurchaseOrders()
    }

    private fun loadDocuments() {
        viewModelScope.launch {
            jobDocumentsRepository.getDocumentsForJob(jobId).collect { docs ->
                _uiState.value = _uiState.value.copy(
                    documents = docs,
                    // Refresh token in case it changed
                    authToken = securePrefs.getAccessToken() ?: ""
                )
            }
        }
        // Trigger sync for pending uploads
        viewModelScope.launch {
            jobDocumentsRepository.syncPendingUploads()
        }
    }

    fun uploadDocument(uri: Uri, isCamera: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true)
            val result = jobDocumentsRepository.uploadDocument(jobId, uri, isCamera)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(error = "Upload failed: ${result.exceptionOrNull()?.message}")
            }
            _uiState.value = _uiState.value.copy(isUploading = false)
        }
    }


    private fun observeJobDetail() {
        viewModelScope.launch {
            jobDetailRepository.getJobDetailFlow(jobId).collect { job ->
                if (job != null) {
                    _uiState.value = _uiState.value.copy(job = job)
                }
            }
        }
    }

    fun loadJobDetails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = jobDetailRepository.getJobDetail(jobId)
            if (result.isSuccess) {
                val job = result.getOrNull()
                _uiState.value = _uiState.value.copy(job = job)
                // Fetch customer if exists
                if (job?.third_party_id != null) {
                    val customerResult = jobsRepository.getThirdParty(job.third_party_id)
                    _uiState.value = _uiState.value.copy(customer = customerResult.getOrNull())
                }
                // Fetch updates
                val updatesResult = jobsRepository.getJobUpdates(jobId)
                _uiState.value = _uiState.value.copy(updates = updatesResult.getOrDefault(emptyList()))
            } else {
                _uiState.value = _uiState.value.copy(error = result.exceptionOrNull()?.message ?: "Job not found")
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun loadTimeEntries() {
        // Observe local data
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingTimeEntries = true)
            timeEntriesRepositoryOffline.getTimeEntriesForJobFlow(jobId).collect { entries ->
                _uiState.value = _uiState.value.copy(
                    timeEntries = entries,
                    isLoadingTimeEntries = false
                )
            }
        }

        // Fetch from network to sync
        viewModelScope.launch {
            // First, try to push any unsynced entries for this job
            val unsynced = timeEntriesRepositoryOffline.getUnsyncedEntries()
            unsynced.filter { it.job_id == jobId }.forEach { entry ->
                if (entry.finish_time != null) {
                    // Completed entry
                    val result = timeEntriesRepository.createFullTimeEntry(
                        jobId = entry.job_id,
                        startTime = entry.start_time,
                        finishTime = entry.finish_time,
                        durationSeconds = entry.duration_seconds,
                        note = entry.start_note ?: entry.finish_note
                    )
                    if (result.isSuccess) {
                        timeEntriesRepositoryOffline.deleteTimeEntryById(entry.id)
                        timeEntriesRepositoryOffline.insertTimeEntry(result.getOrNull()!!, synced = true)
                    }
                } else {
                    // Active entry
                    val result = timeEntriesRepository.startTimeEntry(entry.job_id, entry.start_time, entry.start_note)
                    if (result.isSuccess) {
                        val newEntry = result.getOrNull()!!
                        timeEntriesRepositoryOffline.deleteTimeEntryById(entry.id)
                        timeEntriesRepositoryOffline.insertTimeEntry(newEntry, synced = true)
                    }
                }
            }

            // Then fetch latest from server
            val result = timeEntriesRepository.getTimeEntriesForJob(jobId)
            if (result.isSuccess) {
                val remoteEntries = result.getOrNull() ?: emptyList()
                remoteEntries.forEach { entry ->
                    // Insert/Update local DB with server data, marking as synced
                    timeEntriesRepositoryOffline.insertTimeEntry(entry, synced = true)
                }
            }
        }
    }

    fun startTimer() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isStartingTimer = true)
            val userId = securePrefs.getUserId() ?: run {
                _uiState.value = _uiState.value.copy(isStartingTimer = false, error = "User not logged in")
                return@launch
            }
            
            // Check if there's already an active timer
            val activeTimer = timeEntriesRepositoryOffline.getActiveTimeEntryForUser(userId)
            if (activeTimer != null) {
                _uiState.value = _uiState.value.copy(
                    isStartingTimer = false,
                    error = "A timer is already running on another job. Stop it first."
                )
                return@launch
            }
            
            val entry = TimeEntry(
                id = java.util.UUID.randomUUID().toString(),
                job_id = jobId,
                user_id = userId,
                start_time = Instant.now().toString(),
                finish_time = null,
                start_note = null,
                finish_note = null,
                duration_seconds = 0,
                created_at = Instant.now().toString()
            )
            try {
                timeEntriesRepositoryOffline.insertTimeEntry(entry, synced = false)
                _uiState.value = _uiState.value.copy(
                    isStartingTimer = false,
                    timerStarted = true
                )
                loadTimeEntries()
                
                // Start the TimerService with job name
                val jobName = _uiState.value.job?.name ?: "Timer"
                TimerService.startService(application.applicationContext, entry.start_time, jobName)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isStartingTimer = false,
                    error = "Failed to start timer: ${e.message}"
                )
            }
        }
    }
    
    fun clearTimerStarted() {
        _uiState.value = _uiState.value.copy(timerStarted = false)
    }
    
    fun addUpdate(content: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (content.isBlank()) {
            onError("Update content cannot be empty")
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPostingUpdate = true, updateError = null)
            
            val result = jobsRepository.createJobUpdate(jobId, "<p>$content</p>")
            
            if (result.isSuccess) {
                logDebug("Update created successfully")
                _uiState.value = _uiState.value.copy(isPostingUpdate = false)
                loadJobDetails() // Refresh updates
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to create update"
                Log.e("JobDetailViewModel", "Failed to create update: $errorMsg")
                _uiState.value = _uiState.value.copy(
                    isPostingUpdate = false,
                    updateError = errorMsg
                )
                onError(errorMsg)
            }
        }
    }
    
    fun refreshUpdates() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshingUpdates = true)
            val updatesResult = jobsRepository.getJobUpdates(jobId)
            _uiState.value = _uiState.value.copy(
                updates = updatesResult.getOrDefault(emptyList()),
                isRefreshingUpdates = false
            )
        }
    }
    
    fun clearUpdateError() {
        _uiState.value = _uiState.value.copy(updateError = null)
    }

    fun addTimeEntry(startTime: String, finishTime: String, durationSeconds: Int, note: String?) {
        viewModelScope.launch {
            val userId = securePrefs.getUserId() ?: run {
                _uiState.value = _uiState.value.copy(error = "User not logged in")
                return@launch
            }
            val entry = TimeEntry(
                id = java.util.UUID.randomUUID().toString(),
                job_id = jobId,
                user_id = userId,
                start_time = startTime,
                finish_time = finishTime,
                start_note = note,
                finish_note = note,
                duration_seconds = durationSeconds,
                created_at = startTime
            )
            try {
                timeEntriesRepositoryOffline.insertTimeEntry(entry, synced = false)
                loadTimeEntries()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to add time entry: ${e.message}")
            }
        }
    }

    // For offline, updating means re-inserting with the same ID and synced = false
    fun updateTimeEntry(entryId: String, startTime: String, finishTime: String, durationSeconds: Int, note: String?) {
        viewModelScope.launch {
            val userId = securePrefs.getUserId() ?: run {
                _uiState.value = _uiState.value.copy(error = "User not logged in")
                return@launch
            }
            val entry = TimeEntry(
                id = entryId,
                job_id = jobId,
                user_id = userId,
                start_time = startTime,
                finish_time = finishTime,
                start_note = note,
                finish_note = note,
                duration_seconds = durationSeconds,
                created_at = startTime
            )
            try {
                timeEntriesRepositoryOffline.insertTimeEntry(entry, synced = false)
                loadTimeEntries()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to update time entry: ${e.message}")
            }
        }
    }

    fun deleteTimeEntry(entryId: String) {
        viewModelScope.launch {
            try {
                // Delete from remote API
                val remoteResult = timeEntriesRepository.deleteTimeEntry(entryId)
                if (remoteResult.isFailure) {
                    _uiState.value = _uiState.value.copy(error = "Failed to delete time entry remotely: ${remoteResult.exceptionOrNull()?.message}")
                }
                // Delete from local DB
                timeEntriesRepositoryOffline.deleteTimeEntryById(entryId)
                loadTimeEntries()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to delete time entry: ${e.message}")
            }
        }
    }

    // ============== Purchase Order Functions ==============

    fun loadPurchaseOrders() {
        viewModelScope.launch {
            val result = purchaseOrderRepository.getPurchaseOrdersForJob(jobId)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(purchaseOrders = result.getOrNull() ?: emptyList())
            }
        }
    }

    fun loadSuppliers() {
        viewModelScope.launch {
            logDebug("loadSuppliers() called")
            _uiState.value = _uiState.value.copy(isLoadingSuppliers = true)
            val result = purchaseOrderRepository.getSuppliers()
            logDebug("getSuppliers result: isSuccess=${result.isSuccess}")
            if (result.isSuccess) {
                val suppliersList = result.getOrNull() ?: emptyList()
                logDebug("Setting suppliers in state: count=${suppliersList.size}")
                _uiState.value = _uiState.value.copy(
                    suppliers = suppliersList,
                    isLoadingSuppliers = false
                )
                logDebug("State after update: suppliers=${_uiState.value.suppliers.size}")
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoadingSuppliers = false,
                    poError = "Failed to load suppliers"
                )
            }
        }
    }

    fun createSupplier(name: String, email: String?, phone: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingSupplier = true)
            val result = purchaseOrderRepository.createSupplier(name, email, phone)
            if (result.isSuccess) {
                val newSupplier = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isCreatingSupplier = false,
                    newlyCreatedSupplier = newSupplier
                )
                // Refresh suppliers list
                loadSuppliers()
            } else {
                _uiState.value = _uiState.value.copy(
                    isCreatingSupplier = false,
                    poError = "Failed to create supplier: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun clearNewlyCreatedSupplier() {
        _uiState.value = _uiState.value.copy(newlyCreatedSupplier = null)
    }

    suspend fun checkServerAvailable(): Boolean {
        return networkUtils.isServerReachable()
    }

    /**
     * Create a draft PO immediately and return it (for showing PO number right away)
     */
    fun createDraftPurchaseOrder(onResult: (Document?) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingPO = true, poError = null)
            
            val result = purchaseOrderRepository.createDraftPurchaseOrder(jobId)
            
            if (result.isSuccess) {
                val document = result.getOrNull()
                _uiState.value = _uiState.value.copy(
                    isCreatingPO = false,
                    editingPurchaseOrder = document
                )
                loadPurchaseOrders()
                onResult(document)
            } else {
                _uiState.value = _uiState.value.copy(
                    isCreatingPO = false,
                    poError = result.exceptionOrNull()?.message ?: "Failed to create purchase order"
                )
                onResult(null)
            }
        }
    }

    /**
     * Load an existing PO for editing
     */
    fun loadPurchaseOrderForEdit(document: Document) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                editingPurchaseOrder = document,
                editingPOLineItems = emptyList(),
                editingPOSupplier = null,
                isLoadingEditPO = true
            )
            
            // Load line items
            val itemsResult = purchaseOrderRepository.getDocumentItems(document.id)
            val items = if (itemsResult.isSuccess) {
                itemsResult.getOrNull()?.map { item ->
                    PurchaseOrderLineItem(
                        description = item.description,
                        quantity = item.quantity,
                        unitPrice = item.unit_price,
                        gstRate = item.gst_rate
                    )
                } ?: emptyList()
            } else {
                emptyList()
            }
            
            // Load supplier if present
            val supplier = document.third_party_id?.let { id ->
                purchaseOrderRepository.getThirdPartyById(id).getOrNull()
            }
            
            _uiState.value = _uiState.value.copy(
                editingPOLineItems = items,
                editingPOSupplier = supplier,
                isLoadingEditPO = false
            )
        }
    }

    /**
     * Update an existing PO with supplier, line items, notes
     */
    fun updatePurchaseOrder(
        documentId: String,
        supplierId: String?,
        lineItems: List<PurchaseOrderLineItem>,
        notes: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingPO = true, poError = null)
            
            val result = purchaseOrderRepository.updatePurchaseOrder(
                documentId = documentId,
                supplierId = supplierId,
                lineItems = lineItems.filter { it.description.isNotBlank() },
                notes = notes
            )
            
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isCreatingPO = false,
                    createdPONumber = _uiState.value.editingPurchaseOrder?.document_number,
                    editingPurchaseOrder = null
                )
                loadPurchaseOrders()
            } else {
                _uiState.value = _uiState.value.copy(
                    isCreatingPO = false,
                    poError = result.exceptionOrNull()?.message ?: "Failed to update purchase order"
                )
            }
        }
    }

    fun clearEditingPurchaseOrder() {
        _uiState.value = _uiState.value.copy(
            editingPurchaseOrder = null,
            editingPOLineItems = emptyList(),
            editingPOSupplier = null
        )
    }

    fun createPurchaseOrder(
        supplierId: String?,
        lineItems: List<PurchaseOrderLineItem>,
        notes: String?
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingPO = true, poError = null)
            
            val result = purchaseOrderRepository.createPurchaseOrder(
                jobId = jobId,
                supplierId = supplierId,
                lineItems = lineItems,
                notes = notes
            )
            
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isCreatingPO = false,
                    createdPONumber = result.getOrNull()
                )
                // Refresh PO list
                loadPurchaseOrders()
            } else {
                _uiState.value = _uiState.value.copy(
                    isCreatingPO = false,
                    poError = result.exceptionOrNull()?.message ?: "Failed to create purchase order"
                )
            }
        }
    }

    fun clearCreatedPONumber() {
        _uiState.value = _uiState.value.copy(createdPONumber = null)
    }

    fun clearPOError() {
        _uiState.value = _uiState.value.copy(poError = null)
    }

    fun deletePurchaseOrder(document: Document) {
        viewModelScope.launch {
            val total = document.total_amount ?: 0.0
            if (total != 0.0) {
                _uiState.value = _uiState.value.copy(poError = "Only $0 purchase orders can be deleted")
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isDeletingPO = true,
                deletingPOId = document.id,
                poError = null
            )

            val result = purchaseOrderRepository.deletePurchaseOrderIfEmpty(
                documentId = document.id,
                totalAmount = document.total_amount
            )

            if (result.isSuccess) {
                // Close edit dialog if it was open for this PO
                if (_uiState.value.editingPurchaseOrder?.id == document.id) {
                    clearEditingPurchaseOrder()
                }
                loadPurchaseOrders()
                _uiState.value = _uiState.value.copy(isDeletingPO = false, deletingPOId = null)
            } else {
                _uiState.value = _uiState.value.copy(
                    isDeletingPO = false,
                    deletingPOId = null,
                    poError = result.exceptionOrNull()?.message ?: "Failed to delete purchase order"
                )
            }
        }
    }

    /**
     * Load a document for preview with its line items and third party details
     */
    fun loadDocumentPreview(document: Document) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                selectedDocument = document,
                isLoadingDocumentPreview = true,
                selectedDocumentItems = emptyList(),
                selectedDocumentThirdParty = null
            )

            // Load line items
            val itemsResult = purchaseOrderRepository.getDocumentItems(document.id)
            val items = if (itemsResult.isSuccess) {
                itemsResult.getOrNull() ?: emptyList()
            } else {
                emptyList()
            }

            // Load third party if present
            val thirdParty = document.third_party_id?.let { thirdPartyId ->
                val result = purchaseOrderRepository.getThirdPartyById(thirdPartyId)
                if (result.isSuccess) result.getOrNull() else null
            }

            _uiState.value = _uiState.value.copy(
                selectedDocumentItems = items,
                selectedDocumentThirdParty = thirdParty,
                isLoadingDocumentPreview = false
            )
        }
    }

    fun clearDocumentPreview() {
        _uiState.value = _uiState.value.copy(
            selectedDocument = null,
            selectedDocumentItems = emptyList(),
            selectedDocumentThirdParty = null,
            isLoadingDocumentPreview = false
        )
    }

    // ============== Payments/Invoices Functions ==============

    fun loadInvoices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingInvoices = true, paymentError = null)
            
            // Load invoices and business profile in parallel
            val invoicesResult = paymentsRepository.getInvoicesForJob(jobId)
            val profileResult = paymentsRepository.getBusinessProfile()
            
            _uiState.value = _uiState.value.copy(
                invoices = invoicesResult.getOrNull() ?: emptyList(),
                businessProfile = profileResult.getOrNull(),
                isLoadingInvoices = false,
                paymentError = if (invoicesResult.isFailure) {
                    invoicesResult.exceptionOrNull()?.message ?: "Failed to load invoices"
                } else null
            )
        }
    }

    fun selectInvoiceForPayment(invoice: InvoiceDocument) {
        val paymentUrl = invoice.payment_token?.let { 
            paymentsRepository.buildPaymentUrl(it)
        }
        _uiState.value = _uiState.value.copy(
            selectedInvoice = invoice,
            selectedInvoicePaymentUrl = paymentUrl
        )
    }

    fun clearSelectedInvoice() {
        _uiState.value = _uiState.value.copy(
            selectedInvoice = null,
            selectedInvoicePaymentUrl = null,
            invoiceEmailSent = false
        )
    }

    fun regeneratePaymentToken() {
        val invoice = _uiState.value.selectedInvoice ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRegeneratingToken = true, paymentError = null)
            val result = paymentsRepository.regeneratePaymentToken(invoice.id)
            if (result.isSuccess) {
                val response = result.getOrNull()
                if (response?.success == true && response.paymentToken != null) {
                    // Update the selected invoice with new token
                    val updatedInvoice = invoice.copy(
                        payment_token = response.paymentToken,
                        payment_token_expires_at = null,
                        payment_token_invalidated_at = null
                    )
                    _uiState.value = _uiState.value.copy(
                        selectedInvoice = updatedInvoice,
                        selectedInvoicePaymentUrl = response.paymentUrl,
                        isRegeneratingToken = false
                    )
                    // Refresh invoices list
                    loadInvoices()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isRegeneratingToken = false,
                        paymentError = "Failed to regenerate payment link"
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isRegeneratingToken = false,
                    paymentError = result.exceptionOrNull()?.message ?: "Failed to regenerate payment link"
                )
            }
        }
    }

    fun sendInvoiceEmail() {
        val invoice = _uiState.value.selectedInvoice ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSendingInvoiceEmail = true, paymentError = null)
            val result = paymentsRepository.sendInvoiceEmail(invoice.id)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isSendingInvoiceEmail = false,
                    invoiceEmailSent = true
                )
                // Refresh invoices to update status
                loadInvoices()
            } else {
                _uiState.value = _uiState.value.copy(
                    isSendingInvoiceEmail = false,
                    paymentError = result.exceptionOrNull()?.message ?: "Failed to send invoice email"
                )
            }
        }
    }

    /**
     * Record a manual payment (Cash or EFT) for the selected invoice.
     */
    fun recordManualPayment(amount: Double, paymentMethod: String) {
        val invoice = _uiState.value.selectedInvoice ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecordingPayment = true, paymentError = null)
            val result = paymentsRepository.recordManualPayment(
                documentId = invoice.id,
                amount = amount,
                paymentMethod = paymentMethod
            )
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isRecordingPayment = false,
                    paymentRecorded = true,
                    selectedInvoice = null
                )
                // Refresh invoices to remove paid invoice
                loadInvoices()
            } else {
                _uiState.value = _uiState.value.copy(
                    isRecordingPayment = false,
                    paymentError = result.exceptionOrNull()?.message ?: "Failed to record payment"
                )
            }
        }
    }

    fun clearPaymentRecorded() {
        _uiState.value = _uiState.value.copy(paymentRecorded = false)
    }

    fun clearPaymentError() {
        _uiState.value = _uiState.value.copy(paymentError = null)
    }

    fun clearInvoiceEmailSent() {
        _uiState.value = _uiState.value.copy(invoiceEmailSent = false)
    }

    // ========== Invoice Editing ==========
    
    /**
     * Check if invoice editing is enabled.
     * Currently just uses local app preference.
     * TODO: Add business-level feature_invoice_editing flag when DB column exists
     */
    fun isInvoiceEditingEnabled(): Boolean {
        return securePrefs.getInvoiceEditingEnabled()
    }

    /**
     * Start editing an invoice
     */
    fun startEditingInvoice(invoice: InvoiceDocument) {
        _uiState.value = _uiState.value.copy(editingInvoice = invoice)
    }

    /**
     * Cancel editing and close the dialog
     */
    fun cancelEditingInvoice() {
        _uiState.value = _uiState.value.copy(editingInvoice = null)
    }

    /**
     * Update invoice line items
     */
    fun updateInvoice(lineItems: List<UpdateInvoiceLineItem>, notes: String?) {
        val invoice = _uiState.value.editingInvoice ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUpdatingInvoice = true, invoiceUpdateError = null)
            val result = paymentsRepository.updateInvoice(
                documentId = invoice.id,
                lineItems = lineItems,
                notes = notes
            )
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isUpdatingInvoice = false,
                    invoiceUpdated = true,
                    editingInvoice = null
                )
                // Refresh invoices to show updated totals
                loadInvoices()
            } else {
                _uiState.value = _uiState.value.copy(
                    isUpdatingInvoice = false,
                    invoiceUpdateError = result.exceptionOrNull()?.message ?: "Failed to update invoice"
                )
            }
        }
    }

    fun clearInvoiceUpdated() {
        _uiState.value = _uiState.value.copy(invoiceUpdated = false)
    }

    fun clearInvoiceUpdateError() {
        _uiState.value = _uiState.value.copy(invoiceUpdateError = null)
    }

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    fun checkOnlineStatus() {
        viewModelScope.launch {
            _isOnline.value = networkUtils.isServerReachable()
        }
    }
}

data class JobDetailUiState(
    val job: Job? = null,
    val customer: ThirdParty? = null,
    val updates: List<JobUpdate> = emptyList(),
    val timeEntries: List<TimeEntry> = emptyList(),
    val documents: List<JobDocument> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingTimeEntries: Boolean = false,
    val isStartingTimer: Boolean = false,
    val isUploading: Boolean = false,
    val isPostingUpdate: Boolean = false,
    val isRefreshingUpdates: Boolean = false,
    val timerStarted: Boolean = false,
    val error: String? = null,
    val updateError: String? = null,
    val serverUrl: String = "",
    val authToken: String = "",
    // Purchase Order state
    val suppliers: List<ThirdParty> = emptyList(),
    val purchaseOrders: List<Document> = emptyList(),
    val isLoadingSuppliers: Boolean = false,
    val isCreatingSupplier: Boolean = false,
    val isCreatingPO: Boolean = false,
    val isDeletingPO: Boolean = false,
    val deletingPOId: String? = null,
    val createdPONumber: String? = null,
    val newlyCreatedSupplier: ThirdParty? = null,
    val poError: String? = null,
    // Edit PO state
    val editingPurchaseOrder: Document? = null,
    val editingPOLineItems: List<PurchaseOrderLineItem> = emptyList(),
    val editingPOSupplier: ThirdParty? = null,
    val isLoadingEditPO: Boolean = false,
    // Document Preview state
    val selectedDocument: Document? = null,
    val selectedDocumentItems: List<DocumentItem> = emptyList(),
    val selectedDocumentThirdParty: ThirdParty? = null,
    val isLoadingDocumentPreview: Boolean = false,
    // Payments/Invoices state
    val invoices: List<InvoiceDocument> = emptyList(),
    val isLoadingInvoices: Boolean = false,
    val selectedInvoice: InvoiceDocument? = null,
    val selectedInvoicePaymentUrl: String? = null,
    val isRegeneratingToken: Boolean = false,
    val isSendingInvoiceEmail: Boolean = false,
    val isRecordingPayment: Boolean = false,
    val paymentError: String? = null,
    val invoiceEmailSent: Boolean = false,
    val paymentRecorded: Boolean = false,
    val businessProfile: BusinessProfile? = null,
    // Invoice editing state
    val editingInvoice: InvoiceDocument? = null,
    val isUpdatingInvoice: Boolean = false,
    val invoiceUpdated: Boolean = false,
    val invoiceUpdateError: String? = null
)
