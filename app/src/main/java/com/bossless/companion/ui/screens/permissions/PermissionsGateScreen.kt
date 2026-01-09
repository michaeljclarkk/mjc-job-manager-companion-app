package com.bossless.companion.ui.screens.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsGateScreen(
    notificationsGranted: Boolean,
    foregroundLocationGranted: Boolean,
    backgroundLocationGranted: Boolean,
    cameraGranted: Boolean,
    storageGranted: Boolean,
    systemLocationEnabled: Boolean,
    onRequestNext: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Permissions required",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Location tracking is enabled by default and requires the following to be turned on.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = buildString {
                append("Notifications: ")
                append(if (notificationsGranted) "Granted" else "Required")
                append("\nForeground location: ")
                append(if (foregroundLocationGranted) "Granted" else "Required")
                append("\nBackground location: ")
                append(if (backgroundLocationGranted) "Granted" else "Required")
                append("\nCamera: ")
                append(if (cameraGranted) "Granted" else "Required")
                append("\nStorage/Photos: ")
                append(if (storageGranted) "Granted" else "Required")
                append("\nLocation services (device): ")
                append(if (systemLocationEnabled) "On" else "Off")
            },
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = onRequestNext) {
            Text("Enable required access")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(onClick = onOpenAppSettings) {
            Text("Open app settings")
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(onClick = onOpenLocationSettings) {
            Text("Open location settings")
        }
    }
}
