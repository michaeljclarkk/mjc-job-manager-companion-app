package com.bossless.companion.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bossless.companion.data.models.Document
import com.bossless.companion.data.models.DocumentItem
import com.bossless.companion.data.models.ThirdParty
import java.text.NumberFormat
import java.util.Locale

/**
 * Reusable document preview sheet for Purchase Orders, Quotes, Invoices, etc.
 * Displays document header, line items, and totals in a consistent format.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPreviewSheet(
    document: Document,
    lineItems: List<DocumentItem>,
    thirdParty: ThirdParty?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "AU"))
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with close, share, and edit buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
                
                Text(
                    text = getDocumentTypeLabel(document.type),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Row {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                    if (onShare != null) {
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                    if (onShare == null && onEdit == null) {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Document Number - Prominent
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = document.document_number,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                document.status?.let { status ->
                                    StatusBadge(status = status)
                                }
                            }
                        }
                    }
                    
                    // Document Details
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                document.issue_date?.let { date ->
                                    DetailRow(label = "Issue Date", value = formatDate(date))
                                }
                                
                                thirdParty?.let { party ->
                                    DetailRow(
                                        label = getThirdPartyLabel(document.type),
                                        value = party.name
                                    )
                                    party.email?.let { email ->
                                        DetailRow(label = "Email", value = email)
                                    }
                                    party.phone?.let { phone ->
                                        DetailRow(label = "Phone", value = phone)
                                    }
                                }
                            }
                        }
                    }
                    
                    // Line Items Header
                    if (lineItems.isNotEmpty()) {
                        item {
                            Text(
                                text = "Line Items",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        // Line Items
                        items(lineItems) { item ->
                            LineItemCard(item = item, currencyFormat = currencyFormat)
                        }
                        
                        // Totals
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            TotalsCard(
                                lineItems = lineItems,
                                document = document,
                                currencyFormat = currencyFormat
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "No line items",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LineItemCard(item: DocumentItem, currencyFormat: NumberFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${item.quantity} × ${currencyFormat.format(item.unit_price)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currencyFormat.format(item.total),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            if (item.gst_rate > 0) {
                Text(
                    text = "GST: ${item.gst_rate.toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(
    lineItems: List<DocumentItem>,
    document: Document,
    currencyFormat: NumberFormat
) {
    val subtotal = lineItems.sumOf { it.quantity * it.unit_price }
    val gstAmount = lineItems.sumOf { (it.quantity * it.unit_price) * (it.gst_rate / 100) }
    val total = document.total_amount ?: (subtotal + gstAmount)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Subtotal", style = MaterialTheme.typography.bodyMedium)
                Text(currencyFormat.format(subtotal), style = MaterialTheme.typography.bodyMedium)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("GST", style = MaterialTheme.typography.bodyMedium)
                Text(currencyFormat.format(gstAmount), style = MaterialTheme.typography.bodyMedium)
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    currencyFormat.format(total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun getDocumentTypeLabel(type: String): String {
    return when (type.lowercase()) {
        "purchase_order" -> "Purchase Order"
        "quote" -> "Quote"
        "invoice" -> "Invoice"
        "credit_note" -> "Credit Note"
        "receipt" -> "Receipt"
        else -> type.replace("_", " ").replaceFirstChar { it.uppercase() }
    }
}

private fun getThirdPartyLabel(documentType: String): String {
    return when (documentType.lowercase()) {
        "purchase_order" -> "Supplier"
        "quote", "invoice" -> "Customer"
        else -> "Contact"
    }
}

private fun formatDate(dateString: String): String {
    return try {
        // Parse ISO date and format for display
        val parts = dateString.split("-")
        if (parts.size >= 3) {
            "${parts[2].take(2)}/${parts[1]}/${parts[0]}"
        } else {
            dateString
        }
    } catch (e: Exception) {
        dateString
    }
}

/**
 * Helper function to generate shareable text from a document
 */
fun generateShareableText(
    document: Document,
    lineItems: List<DocumentItem>,
    thirdParty: ThirdParty?
): String {
    val sb = StringBuilder()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "AU"))
    
    sb.appendLine(getDocumentTypeLabel(document.type))
    sb.appendLine("=" .repeat(30))
    sb.appendLine()
    sb.appendLine("Document #: ${document.document_number}")
    document.status?.let { sb.appendLine("Status: ${it.replaceFirstChar { c -> c.uppercase() }}") }
    document.issue_date?.let { sb.appendLine("Date: ${formatDate(it)}") }
    thirdParty?.let { sb.appendLine("${getThirdPartyLabel(document.type)}: ${it.name}") }
    sb.appendLine()
    
    if (lineItems.isNotEmpty()) {
        sb.appendLine("Line Items:")
        sb.appendLine("-".repeat(30))
        lineItems.forEach { item ->
            sb.appendLine("• ${item.description}")
            sb.appendLine("  ${item.quantity} × ${currencyFormat.format(item.unit_price)} = ${currencyFormat.format(item.total)}")
        }
        sb.appendLine()
        
        val subtotal = lineItems.sumOf { it.quantity * it.unit_price }
        val gstAmount = lineItems.sumOf { (it.quantity * it.unit_price) * (it.gst_rate / 100) }
        val total = document.total_amount ?: (subtotal + gstAmount)
        
        sb.appendLine("Subtotal: ${currencyFormat.format(subtotal)}")
        sb.appendLine("GST: ${currencyFormat.format(gstAmount)}")
        sb.appendLine("TOTAL: ${currencyFormat.format(total)}")
    }
    
    return sb.toString()
}
