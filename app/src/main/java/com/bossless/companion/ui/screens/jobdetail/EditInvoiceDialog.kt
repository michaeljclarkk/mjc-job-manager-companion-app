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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bossless.companion.data.models.DocumentItem
import com.bossless.companion.data.models.InvoiceDocument
import java.util.UUID

/**
 * Editable line item for the invoice editor.
 * Similar to PurchaseOrderLineItem but includes GST rate.
 */
data class InvoiceLineItem(
    val id: String = UUID.randomUUID().toString(),
    val description: String = "",
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val gstRate: Double = 10.0  // Default 10% GST
) {
    val subtotal: Double get() = quantity * unitPrice
    val gstAmount: Double get() = subtotal * (gstRate / 100.0)
    val total: Double get() = subtotal + gstAmount
}

/**
 * Convert DocumentItem to editable InvoiceLineItem
 */
fun DocumentItem.toInvoiceLineItem(): InvoiceLineItem {
    return InvoiceLineItem(
        id = id,
        description = description,
        quantity = quantity,
        unitPrice = unit_price,
        gstRate = gst_rate
    )
}

/**
 * Dialog for editing an invoice's line items.
 * Similar to RaisePurchaseOrderDialog but for invoices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInvoiceDialog(
    invoice: InvoiceDocument,
    onDismiss: () -> Unit,
    onSave: (documentId: String, lineItems: List<InvoiceLineItem>, notes: String?) -> Unit,
    isLoading: Boolean = false
) {
    // Convert existing document items to editable line items
    val initialItems = remember(invoice.document_items) {
        invoice.document_items?.map { it.toInvoiceLineItem() }?.ifEmpty { listOf(InvoiceLineItem()) }
            ?: listOf(InvoiceLineItem())
    }
    
    var lineItems by remember { mutableStateOf(initialItems) }
    var notes by remember { mutableStateOf("") }

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
                        title = { Text("Edit Invoice") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
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
                            // Totals
                            val subtotal = lineItems.sumOf { it.subtotal }
                            val gst = lineItems.sumOf { it.gstAmount }
                            val total = lineItems.sumOf { it.total }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Subtotal:", style = MaterialTheme.typography.bodyMedium)
                                Text("$${String.format("%.2f", subtotal)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("GST:", style = MaterialTheme.typography.bodyMedium)
                                Text("$${String.format("%.2f", gst)}", style = MaterialTheme.typography.bodyMedium)
                            }
                            
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("$${String.format("%.2f", total)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Button(
                                onClick = {
                                    val validItems = lineItems.filter { it.description.isNotBlank() }
                                    onSave(invoice.id, validItems, notes.ifBlank { null })
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading && lineItems.any { it.description.isNotBlank() }
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Saving...")
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Save Changes")
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
                    // Invoice Number Display - Read only
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
                                    "Invoice Number",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = invoice.document_number,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    // Customer info (read-only display)
                    invoice.third_parties?.let { customer ->
                        item {
                            Text(
                                "Customer",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            OutlinedTextField(
                                value = customer.name,
                                onValueChange = {},
                                label = { Text("Customer Name") },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = false,
                                singleLine = true
                            )
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
                                onClick = { lineItems = lineItems + InvoiceLineItem() }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Item")
                            }
                        }
                    }

                    // Line Items List
                    itemsIndexed(lineItems, key = { _, item -> item.id }) { index, item ->
                        InvoiceLineItemCard(
                            item = item,
                            index = index + 1,
                            onUpdate = { updated ->
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
                            onValueChange = { notes = it },
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
private fun InvoiceLineItemCard(
    item: InvoiceLineItem,
    index: Int,
    onUpdate: (InvoiceLineItem) -> Unit,
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
                    label = { Text("Price") },
                    modifier = Modifier.weight(1.2f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("$") }
                )
            }
            
            // Line totals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "Subtotal: $${String.format("%.2f", item.subtotal)} + GST: $${String.format("%.2f", item.gstAmount)} = ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "$${String.format("%.2f", item.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
