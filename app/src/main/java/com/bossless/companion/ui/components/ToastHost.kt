package com.bossless.companion.ui.components

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun ToastHost(snackbarHostState: SnackbarHostState) {
    SnackbarHost(hostState = snackbarHostState)
}
