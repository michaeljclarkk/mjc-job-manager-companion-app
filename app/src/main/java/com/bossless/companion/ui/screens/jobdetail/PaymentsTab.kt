package com.bossless.companion.ui.screens.jobdetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bossless.companion.data.models.InvoiceDocument
import com.bossless.companion.ui.components.StatusBadge
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PaymentsTab(
    invoices: List<InvoiceDocument>,
    isLoading: Boolean,
    isOnline: Boolean,
    onInvoiceClick: (InvoiceDocument) -> Unit,
    canEditInvoices: Boolean = false,
    onEditInvoice: ((InvoiceDocument) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // State for invoice preview sheet
    var previewInvoice by remember { mutableStateOf<InvoiceDocument?>(null) }
    
    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            invoices.isEmpty() -> {
                EmptyPaymentsState(modifier = Modifier.align(Alignment.Center))
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isOnline) {
                        item {
                            OfflineBanner()
                        }
                    }
                    
                    items(invoices, key = { it.id }) { invoice ->
                        InvoiceCard(
                            invoice = invoice,
                            onClick = { onInvoiceClick(invoice) },
                            onLongClick = { previewInvoice = invoice },
                            enabled = isOnline
                        )
                    }
                }
            }
        }
    }
    
    // Invoice preview sheet (shown on long press)
    previewInvoice?.let { invoice ->
        InvoicePreviewSheet(
            invoice = invoice,
            onDismiss = { previewInvoice = null },
            canEdit = canEditInvoices,
            onEditClick = if (canEditInvoices && onEditInvoice != null) {
                {
                    previewInvoice = null
                    onEditInvoice(invoice)
                }
            } else null
        )
    }
}

@Composable
private fun EmptyPaymentsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Receipt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No unpaid invoices",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "All invoices for this job have been paid",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Offline - Payment actions require internet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun InvoiceCard(
    invoice: InvoiceDocument,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "AU")) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = if (enabled) {
            CardDefaults.cardColors()
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row: Invoice number + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = invoice.document_number,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                StatusBadge(
                    status = invoice.status ?: "draft"
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Customer name
            invoice.third_parties?.let { customer ->
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            // Amount + Due date row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total amount
                Text(
                    text = currencyFormat.format(invoice.total_amount ?: 0.0),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Due date
                invoice.due_date?.let { dueDateStr ->
                    val parsedDueDate = remember(dueDateStr) {
                        runCatching { LocalDate.parse(dueDateStr) }.getOrNull()
                    }
                    parsedDueDate?.let { dueDate ->
                        val isOverdue = dueDate.isBefore(LocalDate.now())
                        Text(
                            text = "Due: ${dueDate.format(dateFormatter)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverdue) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                }
            }
            
            // Payment token status indicator
            if (invoice.payment_token == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠ No payment link",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (invoice.payment_token_invalidated_at != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓ Payment link used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
