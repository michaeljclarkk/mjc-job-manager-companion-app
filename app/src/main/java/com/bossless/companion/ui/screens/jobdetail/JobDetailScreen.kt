package com.bossless.companion.ui.screens.jobdetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Person
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.runtime.*
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.widget.TextView
import com.bossless.companion.ui.components.generateShareableText
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.filled.Description
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.bossless.companion.ui.components.DocumentPreviewSheet
import com.bossless.companion.data.models.JobDocument
import com.bossless.companion.data.models.Document
import kotlinx.coroutines.launch
import com.bossless.companion.ui.components.StatusBadge
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.core.text.HtmlCompat
import android.util.Log
import com.bossless.companion.BuildConfig
import com.bossless.companion.ui.navigation.FeatureFlagsViewModel
import androidx.compose.material.icons.filled.CreditCard

private fun logDebug(tag: String, message: String) {
    if (BuildConfig.DEBUG) Log.d(tag, message)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JobDetailScreen(
    onBackClick: () -> Unit,
    onTimerStarted: () -> Unit = {},
    viewModel: JobDetailViewModel = hiltViewModel(),
    featureFlagsViewModel: FeatureFlagsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isStripePaymentsEnabled by featureFlagsViewModel.isStripePaymentsEnabled.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateText by remember { mutableStateOf("") }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showPODialog by remember { mutableStateOf(false) }
    var showPaymentOptionsSheet by remember { mutableStateOf(false) }
    var showQrCodeScreen by remember { mutableStateOf(false) }
    var showPaymentWebView by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    val context = LocalContext.current
    var tempImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingCameraLaunch by remember { mutableStateOf(false) }

    // Check online status when payments tab is visible
    LaunchedEffect(isStripePaymentsEnabled) {
        if (isStripePaymentsEnabled) {
            viewModel.checkOnlineStatus()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempImageUri != null) {
            viewModel.uploadDocument(tempImageUri!!, isCamera = true)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            viewModel.uploadDocument(uri, isCamera = false)
        }
    }

    // Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && pendingCameraLaunch) {
            pendingCameraLaunch = false
            val file = File(context.cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            tempImageUri = uri
            cameraLauncher.launch(uri)
        }
    }

    // Helper function to launch camera with permission check
    fun launchCameraWithPermission() {
        when {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted, launch camera
                val file = File(context.cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                tempImageUri = uri
                cameraLauncher.launch(uri)
            }
            else -> {
                // Request permission
                pendingCameraLaunch = true
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
        }
    }
    
    // Define tabs based on feature flag
    val baseTabs = listOf(
        TabItem("Details", Icons.Default.Edit),
        TabItem("Time", Icons.Default.Schedule),
        TabItem("Updates", Icons.Default.Update),
        TabItem("Docs", Icons.Default.Description)
    )
    val tabs = if (isStripePaymentsEnabled) {
        baseTabs + TabItem("Payments", Icons.Default.CreditCard)
    } else {
        baseTabs
    }
    
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    // Load invoices when Payments tab is enabled
    LaunchedEffect(isStripePaymentsEnabled) {
        if (isStripePaymentsEnabled) {
            viewModel.loadInvoices()
        }
    }

    // Navigate to timer when started
    LaunchedEffect(uiState.timerStarted) {
        if (uiState.timerStarted) {
            viewModel.clearTimerStarted()
            onTimerStarted()
        }
    }

    // Show PO error as snackbar
    LaunchedEffect(uiState.poError) {
        uiState.poError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearPOError()
        }
    }

    // Load suppliers when PO dialog opens (new or edit)
    LaunchedEffect(showPODialog, uiState.editingPurchaseOrder) {
        if (showPODialog || uiState.editingPurchaseOrder != null) {
            viewModel.loadSuppliers()
        }
    }

    // Handle newly created supplier - close form
    LaunchedEffect(uiState.newlyCreatedSupplier) {
        if (uiState.newlyCreatedSupplier != null) {
            // Supplier was created, user can now select it from dropdown
            viewModel.clearNewlyCreatedSupplier()
        }
    }

    if (showUploadDialog) {
        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("Upload Document") },
            text = { Text("Choose source") },
            confirmButton = {
                TextButton(onClick = {
                    showUploadDialog = false
                    launchCameraWithPermission()
                }) {
                    Text("Camera")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUploadDialog = false
                    galleryLauncher.launch("*/*")
                }) {
                    Text("Files")
                }
            }
        )
    }

    // Raise Purchase Order Dialog (new PO or edit existing)
    if (showPODialog || uiState.editingPurchaseOrder != null) {
        RaisePurchaseOrderDialog(
            onDismiss = {
                showPODialog = false
                viewModel.clearCreatedPONumber()
                viewModel.clearEditingPurchaseOrder()
            },
            onCreateDraft = { onResult ->
                viewModel.createDraftPurchaseOrder(onResult)
            },
            onSave = { documentId, supplierId, lineItems, notes ->
                viewModel.updatePurchaseOrder(documentId, supplierId, lineItems, notes)
            },
            suppliers = uiState.suppliers,
            isLoading = uiState.isCreatingPO,
            editingDocument = uiState.editingPurchaseOrder,
            editingLineItems = uiState.editingPOLineItems,
            editingSupplier = uiState.editingPOSupplier,
            isLoadingEdit = uiState.isLoadingEditPO,
            onCreateSupplier = { name, email, phone ->
                viewModel.createSupplier(name, email, phone)
            },
            isCreatingSupplier = uiState.isCreatingSupplier,
            savedPONumber = uiState.createdPONumber,
            onCameraClick = { showUploadDialog = true }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.job?.job_number ?: "Job Detail") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showUploadDialog = true }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Upload")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Show FAB on Updates tab (Add Update) and Details tab (Raise PO)
            when (pagerState.currentPage) {
                0 -> {
                    // Raise PO FAB on Details tab
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                val isAvailable = viewModel.checkServerAvailable()
                                if (isAvailable) {
                                    val pendingDraft = uiState.purchaseOrders
                                        .asSequence()
                                        .filter { po ->
                                            po.status?.lowercase() == "draft" && (po.total_amount ?: 0.0) == 0.0
                                        }
                                        .maxByOrNull { po ->
                                            runCatching { java.time.Instant.parse(po.created_at) }
                                                .getOrNull()
                                                ?.toEpochMilli()
                                                ?: Long.MIN_VALUE
                                        }

                                    if (pendingDraft != null) {
                                        viewModel.loadPurchaseOrderForEdit(pendingDraft)
                                    } else {
                                        showPODialog = true
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("Server unavailable, please check network and try again")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = "Raise Purchase Order")
                    }
                }
                2 -> {
                    // Add Update FAB on Updates tab
                    FloatingActionButton(onClick = { showUpdateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Update")
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.job != null) {
                // Tab Bar
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            text = { Text(tab.title) },
                            icon = { Icon(tab.icon, contentDescription = tab.title) }
                        )
                    }
                }

                // Tab Content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> DetailsTab(
                            job = uiState.job!!,
                            customer = uiState.customer,
                            isStartingTimer = uiState.isStartingTimer,
                            onStartTimer = { viewModel.startTimer() }
                        )
                        1 -> TimeEntriesTab(
                            timeEntries = uiState.timeEntries,
                            isLoading = uiState.isLoadingTimeEntries,
                            onAddEntry = viewModel::addTimeEntry,
                            onUpdateEntry = { timeEntryId, start, finish, duration, note ->
                                viewModel.updateTimeEntry(
                                    entryId = timeEntryId,
                                    startTime = start,
                                    finishTime = finish,
                                    durationSeconds = duration,
                                    note = note
                                )
                            },
                            onDeleteEntry = viewModel::deleteTimeEntry
                        )
                        2 -> UpdatesTab(
                            updates = uiState.updates,
                            isRefreshing = uiState.isRefreshingUpdates,
                            onRefresh = { viewModel.refreshUpdates() }
                        )
                        3 -> DocumentsTab(
                            documents = uiState.documents,
                            purchaseOrders = uiState.purchaseOrders,
                            serverUrl = uiState.serverUrl,
                            authToken = uiState.authToken,
                            onPOClick = { document ->
                                viewModel.loadDocumentPreview(document)
                            },
                            onPODelete = { document ->
                                viewModel.deletePurchaseOrder(document)
                            },
                            isDeletingPO = uiState.isDeletingPO,
                            deletingPOId = uiState.deletingPOId
                        )
                        4 -> if (isStripePaymentsEnabled) {
                            PaymentsTab(
                                invoices = uiState.invoices,
                                isLoading = uiState.isLoadingInvoices,
                                isOnline = isOnline,
                                onInvoiceClick = { invoice ->
                                    viewModel.selectInvoiceForPayment(invoice)
                                    showPaymentOptionsSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Document Preview Sheet
    uiState.selectedDocument?.let { document ->
        DocumentPreviewSheet(
            document = document,
            lineItems = uiState.selectedDocumentItems,
            thirdParty = uiState.selectedDocumentThirdParty,
            isLoading = uiState.isLoadingDocumentPreview,
            onDismiss = { viewModel.clearDocumentPreview() },
            onShare = {
                val shareText = generateShareableText(
                    document = document,
                    lineItems = uiState.selectedDocumentItems,
                    thirdParty = uiState.selectedDocumentThirdParty
                )
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Share ${document.document_number}")
                context.startActivity(shareIntent)
            },
            onEdit = if (document.type == "purchase_order") {
                {
                    viewModel.clearDocumentPreview()
                    viewModel.loadPurchaseOrderForEdit(document)
                }
            } else null
        )
    }
    
    // Payment Options Sheet
    if (showPaymentOptionsSheet && uiState.selectedInvoice != null) {
        PaymentOptionsSheet(
            invoice = uiState.selectedInvoice!!,
            paymentUrl = uiState.selectedInvoicePaymentUrl ?: "",
            isRegenerating = uiState.isRegeneratingToken,
            isSendingEmail = uiState.isSendingInvoiceEmail,
            onDismiss = {
                showPaymentOptionsSheet = false
                viewModel.clearSelectedInvoice()
            },
            onShowQrCode = {
                showPaymentOptionsSheet = false
                showQrCodeScreen = true
            },
            onTakePayment = {
                showPaymentOptionsSheet = false
                showPaymentWebView = true
            },
            onSendToCustomer = {
                viewModel.sendInvoiceEmail()
            },
            onRegenerateLink = {
                viewModel.regeneratePaymentToken()
            }
        )
    }
    
    // QR Code Screen (full screen)
    if (showQrCodeScreen && uiState.selectedInvoice != null && uiState.selectedInvoicePaymentUrl != null) {
        QrCodeScreen(
            paymentUrl = uiState.selectedInvoicePaymentUrl!!,
            invoiceNumber = uiState.selectedInvoice!!.document_number,
            customerName = uiState.selectedInvoice!!.third_parties?.name,
            amount = uiState.selectedInvoice!!.total_amount,
            onNavigateBack = {
                showQrCodeScreen = false
                showPaymentOptionsSheet = true
            }
        )
    }
    
    // Payment WebView Screen (full screen)
    if (showPaymentWebView && uiState.selectedInvoice != null && uiState.selectedInvoicePaymentUrl != null) {
        PaymentWebViewScreen(
            paymentUrl = uiState.selectedInvoicePaymentUrl!!,
            invoiceNumber = uiState.selectedInvoice!!.document_number,
            onNavigateBack = {
                showPaymentWebView = false
                showPaymentOptionsSheet = true
            },
            onPaymentComplete = {
                showPaymentWebView = false
                viewModel.clearSelectedInvoice()
                viewModel.loadInvoices() // Refresh to remove paid invoice
            }
        )
    }
    
    // Show payment error snackbar
    LaunchedEffect(uiState.paymentError) {
        uiState.paymentError?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearPaymentError()
        }
    }
    
    // Show invoice email sent confirmation
    LaunchedEffect(uiState.invoiceEmailSent) {
        if (uiState.invoiceEmailSent) {
            snackbarHostState.showSnackbar(
                message = "Invoice email sent successfully",
                duration = SnackbarDuration.Short
            )
            viewModel.clearInvoiceEmailSent()
        }
    }
    
    // Show error snackbar for update errors
    LaunchedEffect(uiState.updateError) {
        uiState.updateError?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearUpdateError()
        }
    }
    
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (!uiState.isPostingUpdate) {
                    showUpdateDialog = false 
                }
            },
            title = { Text("Add Update") },
            text = {
                Column {
                    OutlinedTextField(
                        value = updateText,
                        onValueChange = { updateText = it },
                        label = { Text("Update content") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = !uiState.isPostingUpdate
                    )
                    if (uiState.isPostingUpdate) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addUpdate(
                            content = updateText,
                            onSuccess = {
                                updateText = ""
                                showUpdateDialog = false
                            },
                            onError = { /* Error shown via snackbar */ }
                        )
                    },
                    enabled = updateText.isNotBlank() && !uiState.isPostingUpdate
                ) {
                    if (uiState.isPostingUpdate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Submit")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showUpdateDialog = false },
                    enabled = !uiState.isPostingUpdate
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

private data class TabItem(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun DetailsTab(
    job: com.bossless.companion.data.models.Job,
    customer: com.bossless.companion.data.models.ThirdParty?,
    isStartingTimer: Boolean,
    onStartTimer: () -> Unit
) {
    val context = LocalContext.current

    fun openMapsForAddress(address: String) {
        val query = Uri.encode(address)
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query"))
        runCatching {
            context.startActivity(geoIntent)
        }.recoverCatching {
            // Fallback for devices without geo handler
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
            )
            context.startActivity(webIntent)
        }
    }

    fun openDialer(phone: String) {
        val uri = Uri.parse("tel:${Uri.encode(phone)}")
        val intent = Intent(Intent.ACTION_DIAL, uri)
        runCatching { context.startActivity(intent) }
    }

    fun openEmail(email: String) {
        val uri = Uri.parse("mailto:${Uri.encode(email)}")
        val intent = Intent(Intent.ACTION_SENDTO, uri)
        runCatching { context.startActivity(intent) }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = job.name ?: "Untitled",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(status = job.status)
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val location = job.location?.trim().orEmpty()
                    if (location.isBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Open map",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "N/A",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Open map",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { openMapsForAddress(location) }
                            )
                            Text(
                                text = location,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier.clickable { openMapsForAddress(location) }
                            )
                        }
                    }

                    if (!job.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Description:", style = MaterialTheme.typography.labelLarge)
                        Text(job.description)
                    }
                }
            }
        }

        if (customer != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Customer", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(customer.name)

                        customer.phone?.trim()?.takeIf { it.isNotBlank() }?.let { phone ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Call",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { openDialer(phone) }
                                )
                                Text(
                                    text = phone,
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable { openDialer(phone) }
                                )
                            }
                        }

                        customer.email?.trim()?.takeIf { it.isNotBlank() }?.let { email ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { openEmail(email) }
                                )
                                Text(
                                    text = email,
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable { openEmail(email) }
                                )
                            }
                        }

                        customer.address?.trim()?.takeIf { it.isNotBlank() }?.let { address ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Map,
                                    contentDescription = "Open map",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { openMapsForAddress(address) }
                                )
                                Text(
                                    text = address,
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                    modifier = Modifier.clickable { openMapsForAddress(address) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = onStartTimer,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isStartingTimer
            ) {
                if (isStartingTimer) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Starting...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Timer")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentsTab(
    documents: List<JobDocument>,
    purchaseOrders: List<Document>,
    serverUrl: String,
    authToken: String,
    onPOClick: (Document) -> Unit,
    onPODelete: (Document) -> Unit,
    isDeletingPO: Boolean,
    deletingPOId: String?
) {
    var selectedDocumentIndex by remember { mutableStateOf<Int?>(null) }
    var showPhotos by remember { mutableStateOf(true) }  // true = Photos, false = Purchase Orders
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = showPhotos,
                onClick = { showPhotos = true },
                label = { Text("Photos (${documents.size})") }
            )
            FilterChip(
                selected = !showPhotos,
                onClick = { showPhotos = false },
                label = { Text("Purchase Orders (${purchaseOrders.size})") }
            )
        }
        
        if (showPhotos) {
            // Show Photos/Documents
            PhotosContent(
                documents = documents,
                serverUrl = serverUrl,
                authToken = authToken,
                selectedDocumentIndex = selectedDocumentIndex,
                onSelectDocument = { selectedDocumentIndex = it },
                onDismissPreview = { selectedDocumentIndex = null }
            )
        } else {
            // Show Purchase Orders
            PurchaseOrdersContent(
                purchaseOrders = purchaseOrders,
                onPOClick = onPOClick,
                onPODelete = onPODelete,
                isDeletingPO = isDeletingPO,
                deletingPOId = deletingPOId
            )
        }
    }
}

@Composable
private fun PhotosContent(
    documents: List<JobDocument>,
    serverUrl: String,
    authToken: String,
    selectedDocumentIndex: Int?,
    onSelectDocument: (Int?) -> Unit,
    onDismissPreview: () -> Unit
) {
    
    // Image preview dialog
    if (selectedDocumentIndex != null) {
        val selectedDoc = documents.getOrNull(selectedDocumentIndex!!)
        if (selectedDoc != null) {
            AlertDialog(
                onDismissRequest = onDismissPreview,
                title = { Text(selectedDoc.file_name, maxLines = 1) },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 400.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val imageUrl = buildImageUrl(serverUrl, selectedDoc.file_url)
                        if (BuildConfig.DEBUG) {
                            logDebug("DocumentsTab", "Preview URL built")
                        }
                        if (selectedDoc.document_type == "image" && imageUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .addHeader("Authorization", "Bearer $authToken")
                                    .build(),
                                contentDescription = selectedDoc.file_name,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                onError = { state ->
                                    val errorMsg = if (state.result is coil.request.ErrorResult) {
                                        val error = (state.result as coil.request.ErrorResult).throwable
                                        "Image load error: ${error?.message ?: error?.toString() ?: "Unknown error"}"
                                    } else {
                                        "Image load error: ${state.result}"
                                    }
                                    Log.e("DocumentsTab", errorMsg)
                                    if (BuildConfig.DEBUG) {
                                        Log.e("DocumentsTab", "Image preview failed URL: ${imageUrl}")
                                    }
                                }
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Document preview not available")
                                if (imageUrl == null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("(URL is null)", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Row {
                        if (selectedDocumentIndex!! > 0) {
                            TextButton(onClick = { onSelectDocument(selectedDocumentIndex!! - 1) }) {
                                Text("← Prev")
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onDismissPreview) {
                            Text("Close")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (selectedDocumentIndex!! < documents.size - 1) {
                            TextButton(onClick = { onSelectDocument(selectedDocumentIndex!! + 1) }) {
                                Text("Next →")
                            }
                        }
                    }
                }
            )
        }
    }

    if (documents.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No photos yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 128.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(documents.size) { index ->
                val doc = documents[index]
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    onClick = { onSelectDocument(index) }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (doc.document_type == "image") {
                            val imageUrl = buildImageUrl(serverUrl, doc.file_url)
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUrl)
                                    .crossfade(true)
                                    .addHeader("Authorization", "Bearer $authToken")
                                    .build(),
                                contentDescription = doc.file_name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                onError = { state ->
                                    val errorMsg = if (state.result is coil.request.ErrorResult) {
                                        val error = (state.result as coil.request.ErrorResult).throwable
                                        "Thumbnail error: ${error?.message ?: error?.toString() ?: "Unknown error"}"
                                    } else {
                                        "Thumbnail error: ${state.result}"
                                    }
                                    Log.e("DocumentsTab", errorMsg)
                                    if (BuildConfig.DEBUG) {
                                        Log.e("DocumentsTab", "Thumbnail failed URL: ${imageUrl}")
                                    }
                                }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .align(Alignment.Center)
                            )
                        }
                        
                        // Overlay for file name
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = doc.file_name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Build a full image URL for storage items
 */
private fun buildImageUrl(serverUrl: String, fileUrl: String?): String? {
    if (fileUrl.isNullOrBlank() || serverUrl.isBlank()) return null
    
    // Check if it's already a full URL or a local file path
    if (fileUrl.startsWith("http") || fileUrl.startsWith("/")) {
        return fileUrl
    }
    
    // Normalize serverUrl: ensure it has proper protocol format (https:// not https:)
    var normalizedUrl = serverUrl
    if (normalizedUrl.startsWith("https:") && !normalizedUrl.startsWith("https://")) {
        normalizedUrl = normalizedUrl.replaceFirst("https:", "https://")
    } else if (normalizedUrl.startsWith("http:") && !normalizedUrl.startsWith("http://")) {
        normalizedUrl = normalizedUrl.replaceFirst("http:", "http://")
    }
    
    // Build storage URL: {serverUrl}/storage/v1/object/authenticated/job-documents/{path}
    val baseUrl = if (normalizedUrl.endsWith("/")) normalizedUrl else "$normalizedUrl/"
    val url = "${baseUrl}storage/v1/object/authenticated/job-documents/$fileUrl"
    if (BuildConfig.DEBUG) {
        logDebug("buildImageUrl", "Built image URL")
    }
    return url
}

@Composable
private fun PurchaseOrdersContent(
    purchaseOrders: List<Document>,
    onPOClick: (Document) -> Unit,
    onPODelete: (Document) -> Unit,
    isDeletingPO: Boolean,
    deletingPOId: String?
) {
    var confirmDeletePo by remember { mutableStateOf<Document?>(null) }

    confirmDeletePo?.let { po ->
        val total = po.total_amount ?: 0.0
        AlertDialog(
            onDismissRequest = { confirmDeletePo = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete Purchase Order?") },
            text = {
                Text(
                    if (total == 0.0) {
                        "Delete empty PO online? This will permanently delete ${po.document_number}."
                    } else {
                        "Only $0 purchase orders can be deleted."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeletePo = null
                        if (total == 0.0) {
                            onPODelete(po)
                        }
                    },
                    enabled = total == 0.0 && !(isDeletingPO && deletingPOId == po.id)
                ) {
                    if (isDeletingPO && deletingPOId == po.id) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePo = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (purchaseOrders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Receipt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "No purchase orders yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tap the receipt button to raise a PO",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(purchaseOrders) { po ->
                val total = po.total_amount ?: 0.0
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onPOClick(po) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = po.document_number,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = po.issue_date ?: po.created_at.take(10),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$${String.format("%.2f", total)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            po.status?.let { status ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = status.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (total == 0.0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { confirmDeletePo = po },
                                enabled = !(isDeletingPO && deletingPOId == po.id)
                            ) {
                                if (isDeletingPO && deletingPOId == po.id) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete PO")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format ISO 8601 timestamp to human-readable format
 */
private fun formatTimestamp(isoTimestamp: String): String {
    return try {
        val zonedDateTime = ZonedDateTime.parse(isoTimestamp)
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
        zonedDateTime.format(formatter)
    } catch (e: DateTimeParseException) {
        isoTimestamp // Return original if parsing fails
    }
}

/**
 * Format relative time (e.g., "2 hours ago")
 */
private fun formatRelativeTime(isoTimestamp: String): String {
    return try {
        val zonedDateTime = ZonedDateTime.parse(isoTimestamp)
        val now = ZonedDateTime.now()
        val duration = java.time.Duration.between(zonedDateTime, now)
        
        when {
            duration.toMinutes() < 1 -> "just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()} min ago"
            duration.toHours() < 24 -> "${duration.toHours()} hours ago"
            duration.toDays() < 7 -> "${duration.toDays()} days ago"
            else -> formatTimestamp(isoTimestamp)
        }
    } catch (e: DateTimeParseException) {
        isoTimestamp
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdatesTab(
    updates: List<com.bossless.companion.data.models.JobUpdate>,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    val pullRefreshState = rememberPullToRefreshState()

    // Keep the pull-to-refresh state in sync with our real refresh work.
    // Without explicitly ending the state, the indicator can remain visible much longer than intended.
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) {
            pullRefreshState.endRefresh()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
        if (updates.isEmpty() && !isRefreshing) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Update,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No updates yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Pull down to refresh",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = updates,
                    key = { it.id }
                ) { update ->
                    UpdateCard(update = update)
                }
            }
        }
        
        LaunchedEffect(pullRefreshState.isRefreshing) {
            if (pullRefreshState.isRefreshing && !isRefreshing) {
                onRefresh()
            }
        }
        
        val showIndicator = pullRefreshState.isRefreshing || isRefreshing || pullRefreshState.progress > 0f
        if (showIndicator) {
            PullToRefreshContainer(
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun UpdateCard(update: com.bossless.companion.data.models.JobUpdate) {
    val isEmailUpdate = update.content_html?.contains("email-update") == true
    var expanded by remember(update.id) { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEmailUpdate) 
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row with avatar and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Avatar placeholder
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (isEmailUpdate) 
                            MaterialTheme.colorScheme.secondary 
                        else 
                            MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isEmailUpdate) Icons.Default.Email else Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    Column {
                        Text(
                            text = if (isEmailUpdate) "Email Update" else "Team Update",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatRelativeTime(update.created_at),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Date badge
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = formatTimestamp(update.created_at),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            // Content - render HTML (strip style/script blocks so CSS doesn't appear as text)
            val rawHtml = update.content_html?.trim().orEmpty()
            val sanitizedHtml = rawHtml
                .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), "")
                .trim()

            if (sanitizedHtml.isNotBlank()) {
                HtmlText(
                    html = sanitizedHtml,
                    textColor = MaterialTheme.colorScheme.onSurface.toArgb(),
                    maxLines = if (expanded) Int.MAX_VALUE else 10,
                    ellipsize = if (expanded) null else TextUtils.TruncateAt.END,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                TextButton(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (expanded) "Show less" else "Show more")
                }
            } else {
                Text(
                    text = "No content",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun HtmlText(
    html: String,
    textColor: Int,
    maxLines: Int,
    ellipsize: TextUtils.TruncateAt?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(textColor)
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.maxLines = maxLines
            view.ellipsize = ellipsize
            view.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    )
}
