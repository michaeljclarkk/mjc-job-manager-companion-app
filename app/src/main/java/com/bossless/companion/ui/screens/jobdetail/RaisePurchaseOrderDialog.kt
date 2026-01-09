package com.bossless.companion.ui.screens.jobdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bossless.companion.data.models.Document
import com.bossless.companion.data.models.PurchaseOrderLineItem
import com.bossless.companion.data.models.ThirdParty

/**
 * Purchase Order Dialog that supports two workflows:
 * 1. Create new PO - Immediately generates PO number, then user fills in details
 * 2. Edit existing PO - Opens with existing PO data for editing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaisePurchaseOrderDialog(
    onDismiss: () -> Unit,
    onCreateDraft: (onResult: (Document?) -> Unit) -> Unit,
    onSave: (documentId: String, supplierId: String?, lineItems: List<PurchaseOrderLineItem>, notes: String?) -> Unit,
    suppliers: List<ThirdParty>,
    isLoading: Boolean,
    // For editing existing PO
    editingDocument: Document? = null,
    editingLineItems: List<PurchaseOrderLineItem> = emptyList(),
    editingSupplier: ThirdParty? = null,
    isLoadingEdit: Boolean = false,
    // For creating supplier inline
    onCreateSupplier: (name: String, email: String?, phone: String?) -> Unit,
    isCreatingSupplier: Boolean = false,
    // Success state
    savedPONumber: String? = null,
    // Camera shortcut
    onCameraClick: (() -> Unit)? = null
) {
    // Current PO being edited (either passed in or created as draft)
    var currentDocument by remember { mutableStateOf(editingDocument) }
    var isCreatingDraft by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf(false) }
    
    // Form state
    var selectedSupplier by remember(editingSupplier) { mutableStateOf(editingSupplier) }
    var lineItems by remember(editingLineItems) { 
        mutableStateOf(
            if (editingLineItems.isNotEmpty()) editingLineItems 
            else listOf(PurchaseOrderLineItem())
        ) 
    }
    var notes by remember { mutableStateOf("") }
    var showSupplierDropdown by remember { mutableStateOf(false) }
    var showCreateSupplierForm by remember { mutableStateOf(false) }
    var supplierSearchQuery by remember { mutableStateOf("") }
    
    // Filter suppliers based on search
    val filteredSuppliers = remember(suppliers, supplierSearchQuery) {
        if (supplierSearchQuery.isBlank()) {
            suppliers
        } else {
            suppliers.filter { 
                it.name.contains(supplierSearchQuery, ignoreCase = true) ||
                it.email?.contains(supplierSearchQuery, ignoreCase = true) == true
            }
        }
    }
    
    // New supplier form state
    var newSupplierName by remember { mutableStateOf("") }
    var newSupplierEmail by remember { mutableStateOf("") }
    var newSupplierPhone by remember { mutableStateOf("") }

    fun ensureDraftExists() {
        if (currentDocument != null || isCreatingDraft) return
        isCreatingDraft = true
        onCreateDraft { document ->
            currentDocument = document
            isCreatingDraft = false

            if (pendingSave) {
                pendingSave = false
                document?.let { doc ->
                    val validItems = lineItems.filter { it.description.isNotBlank() }
                    onSave(doc.id, selectedSupplier?.id, validItems, notes.ifBlank { null })
                }
            }
        }
    }

    // Success dialog after saving
    if (savedPONumber != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { 
                Text(
                    "Purchase Order Saved",
                    textAlign = TextAlign.Center
                ) 
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Your PO number is:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = savedPONumber,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Give this number to your supplier",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("Done")
                }
            }
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { 
                            Text(if (editingDocument != null) "Edit Purchase Order" else "New Purchase Order")
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            if (onCameraClick != null) {
                                IconButton(onClick = onCameraClick) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Take Photo")
                                }
                            }
                        }
                    )
                },
                bottomBar = {
                    Surface(
                        tonalElevation = 3.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Totals (POs don't include GST)
                            val total = lineItems.sumOf { it.subtotal }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total (Ex GST):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("$${String.format("%.2f", total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    val doc = currentDocument
                                    if (doc != null) {
                                        val validItems = lineItems.filter { it.description.isNotBlank() }
                                        onSave(doc.id, selectedSupplier?.id, validItems, notes.ifBlank { null })
                                    } else {
                                        pendingSave = true
                                        ensureDraftExists()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading && !isCreatingDraft && !isLoadingEdit
                            ) {
                                if (isLoading || isCreatingDraft) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (isLoading) "Saving..." else "Generating...")
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Purchase Order")
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // PO Number Display - PROMINENT at the top
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Purchase Order Number",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                if (isCreatingDraft || isLoadingEdit) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = currentDocument?.document_number ?: "---",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Give this number to your supplier",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Supplier Selection
                    item {
                        Text(
                            "Supplier (Optional)",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        
                        ExposedDropdownMenuBox(
                            expanded = showSupplierDropdown,
                            onExpandedChange = { showSupplierDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = if (showSupplierDropdown) supplierSearchQuery else (selectedSupplier?.name ?: ""),
                                onValueChange = { 
                                    supplierSearchQuery = it
                                    showSupplierDropdown = true
                                },
                                placeholder = { Text("Search or select supplier...") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSupplierDropdown) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = showSupplierDropdown,
                                onDismissRequest = { 
                                    showSupplierDropdown = false
                                    supplierSearchQuery = ""
                                },
                                modifier = Modifier.heightIn(max = 350.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("None") },
                                    onClick = {
                                        selectedSupplier = null
                                        showSupplierDropdown = false
                                        supplierSearchQuery = ""
                                    }
                                )
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Add New Supplier")
                                        }
                                    },
                                    onClick = {
                                        showSupplierDropdown = false
                                        showCreateSupplierForm = true
                                        supplierSearchQuery = ""
                                    }
                                )
                                HorizontalDivider()
                                if (filteredSuppliers.isEmpty() && supplierSearchQuery.isNotBlank()) {
                                    Text(
                                        "No suppliers found",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                filteredSuppliers.forEach { supplier ->
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text(supplier.name)
                                                supplier.email?.let {
                                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedSupplier = supplier
                                            ensureDraftExists()
                                            showSupplierDropdown = false
                                            supplierSearchQuery = ""
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Inline Create Supplier Form
                    if (showCreateSupplierForm) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("New Supplier", style = MaterialTheme.typography.titleSmall)
                                        IconButton(
                                            onClick = { showCreateSupplierForm = false },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    
                                    OutlinedTextField(
                                        value = newSupplierName,
                                        onValueChange = { newSupplierName = it },
                                        label = { Text("Name *") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                    
                                    OutlinedTextField(
                                        value = newSupplierEmail,
                                        onValueChange = { newSupplierEmail = it },
                                        label = { Text("Email") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                                    )
                                    
                                    OutlinedTextField(
                                        value = newSupplierPhone,
                                        onValueChange = { newSupplierPhone = it },
                                        label = { Text("Phone") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                                    )
                                    
                                    Button(
                                        onClick = {
                                            if (newSupplierName.isNotBlank()) {
                                                onCreateSupplier(
                                                    newSupplierName,
                                                    newSupplierEmail.ifBlank { null },
                                                    newSupplierPhone.ifBlank { null }
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = newSupplierName.isNotBlank() && !isCreatingSupplier
                                    ) {
                                        if (isCreatingSupplier) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(if (isCreatingSupplier) "Creating..." else "Create Supplier")
                                    }
                                }
                            }
                        }
                    }

                    // Line Items Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Line Items",
                                style = MaterialTheme.typography.labelLarge
                            )
                            TextButton(
                                onClick = { lineItems = lineItems + PurchaseOrderLineItem() }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Item")
                            }
                        }
                    }

                    // Line Items List
                    itemsIndexed(lineItems, key = { _, item -> item.id }) { index, item ->
                        LineItemCard(
                            item = item,
                            index = index + 1,
                            onUpdate = { updated ->
                                val isMeaningfulEdit = updated.description.isNotBlank() || updated.quantity > 0.0 || updated.unitPrice > 0.0
                                if (isMeaningfulEdit) {
                                    ensureDraftExists()
                                }
                                lineItems = lineItems.map { if (it.id == item.id) updated else it }
                            },
                            onRemove = {
                                if (lineItems.size > 1) {
                                    lineItems = lineItems.filter { it.id != item.id }
                                }
                            },
                            canRemove = lineItems.size > 1
                        )
                    }

                    // Notes
                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = {
                                notes = it
                                if (it.isNotBlank()) {
                                    ensureDraftExists()
                                }
                            },
                            label = { Text("Notes (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.height(100.dp)) // Space for bottom bar
                    }
                }
            }
        }
    }
}

@Composable
private fun LineItemCard(
    item: PurchaseOrderLineItem,
    index: Int,
    onUpdate: (PurchaseOrderLineItem) -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Item $index", style = MaterialTheme.typography.labelMedium)
                if (canRemove) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = item.description,
                onValueChange = { onUpdate(item.copy(description = it)) },
                label = { Text("Description *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = if (item.quantity == 0.0) "" else item.quantity.toString(),
                    onValueChange = { 
                        val qty = it.toDoubleOrNull() ?: 0.0
                        onUpdate(item.copy(quantity = qty))
                    },
                    label = { Text("Qty") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                
                OutlinedTextField(
                    value = if (item.unitPrice == 0.0) "" else String.format("%.2f", item.unitPrice),
                    onValueChange = { 
                        val price = it.toDoubleOrNull() ?: 0.0
                        onUpdate(item.copy(unitPrice = price))
                    },
                    label = { Text("Unit Price") },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") }
                )
                
                // Line total display
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Total", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "$${String.format("%.2f", item.total)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
