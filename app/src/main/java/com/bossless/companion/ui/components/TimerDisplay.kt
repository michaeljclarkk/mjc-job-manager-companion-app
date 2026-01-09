package com.bossless.companion.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException

@Composable
fun TimerDisplay(
    startTime: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displayMedium
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(startTime) {
        try {
            val start = Instant.parse(startTime)
            while (true) {
                val now = Instant.now()
                elapsedSeconds = Duration.between(start, now).seconds
                delay(1000)
            }
        } catch (e: DateTimeParseException) {
            elapsedSeconds = 0
        }
    }

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60

    Text(
        text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
        modifier = modifier,
        style = style,
        textAlign = TextAlign.Center
    )
}
