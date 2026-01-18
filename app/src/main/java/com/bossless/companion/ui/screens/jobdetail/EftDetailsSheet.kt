package com.bossless.companion.ui.screens.jobdetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bossless.companion.data.models.BusinessProfile
import com.bossless.companion.data.models.InvoiceDocument
import java.text.NumberFormat
import java.util.Locale

/**
 * Bottom sheet that displays EFT payment details (bank account info).
 * Shows BSB/Account, PayID, and BPAY options when available.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EftDetailsSheet(
    invoice: InvoiceDocument,
    businessProfile: BusinessProfile,
    onDismiss: () -> Unit,
    onMarkAsPaid: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("en", "AU")) }
    val context = LocalContext.current
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    var copiedText by remember { mutableStateOf<String?>(null) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Check what payment options are available
    val hasBankAccount = !businessProfile.bsb.isNullOrBlank() && !businessProfile.account_number.isNullOrBlank()
    val hasPayId = !businessProfile.payid.isNullOrBlank()
    val hasBpay = !businessProfile.bpay_biller_code.isNullOrBlank() && !businessProfile.bpay_reference.isNullOrBlank()
    
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
                text = "Bank Transfer Details",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Amount to pay
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                            text = "Amount Due",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = invoice.document_number,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                    Text(
                        text = currencyFormat.format((invoice.total_amount ?: 0.0) - (invoice.total_paid ?: 0.0)),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Bank Account Details
            if (hasBankAccount) {
                PaymentMethodSection(
                    title = "Bank Transfer",
                    icon = Icons.Default.AccountBalance
                ) {
                    CopyableField(
                        label = "BSB",
                        value = businessProfile.bsb!!,
                        clipboardManager = clipboardManager,
                        onCopied = { copiedText = "BSB" }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CopyableField(
                        label = "Account Number",
                        value = businessProfile.account_number!!,
                        clipboardManager = clipboardManager,
                        onCopied = { copiedText = "Account Number" }
                    )
                    if (!businessProfile.account_name.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        CopyableField(
                            label = "Account Name",
                            value = businessProfile.account_name,
                            clipboardManager = clipboardManager,
                            onCopied = { copiedText = "Account Name" }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // PayID
            if (hasPayId) {
                PaymentMethodSection(
                    title = "PayID",
                    icon = Icons.Default.FlashOn
                ) {
                    CopyableField(
                        label = "PayID",
                        value = businessProfile.payid!!,
                        clipboardManager = clipboardManager,
                        onCopied = { copiedText = "PayID" }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // BPAY
            if (hasBpay) {
                PaymentMethodSection(
                    title = "BPAY",
                    icon = Icons.Default.Payment
                ) {
                    CopyableField(
                        label = "Biller Code",
                        value = businessProfile.bpay_biller_code!!,
                        clipboardManager = clipboardManager,
                        onCopied = { copiedText = "Biller Code" }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CopyableField(
                        label = "Reference",
                        value = businessProfile.bpay_reference!!,
                        clipboardManager = clipboardManager,
                        onCopied = { copiedText = "Reference" }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // No payment details configured
            if (!hasBankAccount && !hasPayId && !hasBpay) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "No bank details configured. Please set up payment details in the web app.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Copied toast
            if (copiedText != null) {
                LaunchedEffect(copiedText) {
                    kotlinx.coroutines.delay(2000)
                    copiedText = null
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$copiedText copied to clipboard",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Mark as Paid button
            Button(
                onClick = onMarkAsPaid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Customer Has Paid via EFT")
            }
        }
    }
}

@Composable
private fun PaymentMethodSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun CopyableField(
    label: String,
    value: String,
    clipboardManager: ClipboardManager,
    onCopied: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(
            onClick = {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(label, value)
                )
                onCopied()
            }
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
