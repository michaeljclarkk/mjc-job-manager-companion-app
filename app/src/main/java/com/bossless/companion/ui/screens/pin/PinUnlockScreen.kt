package com.bossless.companion.ui.screens.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace

@Composable
fun PinUnlockScreen(
    onUnlocked: () -> Unit,
    onRequireLogin: () -> Unit,
    viewModel: PinUnlockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onUnlocked()
        }
    }

    LaunchedEffect(uiState.requiresLogin) {
        if (uiState.requiresLogin) {
            onRequireLogin()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Unlock",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Enter your 4-digit PIN.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        PinDots(
            filledCount = uiState.pin.length,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        PinPadWithContinue(
            enabled = !uiState.isLoading,
            onDigit = { digit ->
                viewModel.onPinChanged(uiState.pin + digit)
            },
            onBackspace = {
                viewModel.onPinChanged(uiState.pin.dropLast(1))
            },
            onContinue = viewModel::unlock,
            continueEnabled = uiState.pin.length == 4 && !uiState.isLoading
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        TextButton(
            onClick = viewModel::forgotPin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Forgot your PIN? Use password")
        }
    }
}

@Composable
private fun PinDots(
    filledCount: Int,
    modifier: Modifier = Modifier,
    totalDots: Int = 4,
    dotSize: Int = 12,
    shape: Shape = CircleShape,
    emptyColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    filledColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalDots) { index ->
            val isFilled = index < filledCount
            Surface(
                modifier = Modifier
                    .size(dotSize.dp)
                    .clip(shape),
                color = if (isFilled) filledColor else emptyColor.copy(alpha = 0.35f),
                content = {}
            )
        }
    }
}

@Composable
private fun PinPadWithContinue(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onContinue: () -> Unit,
    continueEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PinPadRow(
                labels = listOf("1", "2", "3"),
                enabled = enabled,
                onDigit = onDigit
            )
            PinPadRow(
                labels = listOf("4", "5", "6"),
                enabled = enabled,
                onDigit = onDigit
            )
            PinPadRow(
                labels = listOf("7", "8", "9"),
                enabled = enabled,
                onDigit = onDigit
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(72.dp))

                PinPadKey(
                    label = "0",
                    enabled = enabled,
                    onClick = { onDigit('0') }
                )

                FilledTonalButton(
                    onClick = onBackspace,
                    enabled = enabled,
                    modifier = Modifier.size(72.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        FilledTonalButton(
            onClick = onContinue,
            enabled = enabled && continueEnabled,
            modifier = Modifier.size(72.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Continue"
            )
        }
    }
}

@Composable
private fun PinPadRow(
    labels: List<String>,
    enabled: Boolean,
    onDigit: (Char) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEach { label ->
            PinPadKey(
                label = label,
                enabled = enabled,
                onClick = { onDigit(label.first()) }
            )
        }
    }
}

@Composable
private fun PinPadKey(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(72.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
    }
}
