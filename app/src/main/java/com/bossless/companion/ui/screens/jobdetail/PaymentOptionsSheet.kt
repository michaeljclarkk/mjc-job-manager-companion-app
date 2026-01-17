package com.bossless.companion.ui.screens.jobdetail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bossless.companion.data.models.InvoiceDocument
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentOptionsSheet(
    invoice: InvoiceDocument,
    paymentUrl: String,
    isRegenerating: Boolean,
    isSendingEmail: Boolean,
    onDismiss: () -> Unit,
    onShowQrCode: () -> Unit,
    onTakePayment: () -> Unit,
    onSendToCustomer: () -> Unit,
    onRegenerateLink: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "AU")) }
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = "Payment Options",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Invoice summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = invoice.document_number,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        invoice.third_parties?.let { customer ->
                            Text(
                                text = customer.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = currencyFormat.format(invoice.total_amount ?: 0.0),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action buttons
            val hasValidToken = invoice.payment_token != null && 
                invoice.payment_token_invalidated_at == null
            
            // Show QR Code
            PaymentOptionButton(
                icon = Icons.Default.QrCode2,
                title = "Show QR Code",
                subtitle = "Customer scans to pay",
                enabled = hasValidToken,
                onClick = onShowQrCode
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Take Payment (WebView)
            PaymentOptionButton(
                icon = Icons.Default.CreditCard,
                title = "Take Payment",
                subtitle = "Enter card details for customer",
                enabled = hasValidToken,
                onClick = onTakePayment
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Send to Customer
            PaymentOptionButton(
                icon = Icons.Default.Email,
                title = "Send to Customer",
                subtitle = "Email invoice with payment link",
                enabled = !isSendingEmail && invoice.third_parties?.email != null,
                isLoading = isSendingEmail,
                onClick = onSendToCustomer
            )
            
            // Show no email warning
            if (invoice.third_parties?.email == null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Customer has no email address",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 56.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Regenerate Link
            if (!hasValidToken) {
                PaymentOptionButton(
                    icon = Icons.Default.Refresh,
                    title = "Regenerate Link",
                    subtitle = "Create new payment link",
                    enabled = !isRegenerating,
                    isLoading = isRegenerating,
                    onClick = onRegenerateLink
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled && !isLoading,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
