package com.bossless.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bossless.companion.ui.theme.*
import java.util.Locale

@Composable
fun StatusBadge(status: String) {
    val (backgroundColor, textColor) = when (status.lowercase(Locale.ROOT)) {
        "pending" -> StatusPending to Color.White
        "in_progress" -> StatusInProgress to Color.White
        "completed" -> StatusCompleted to Color.White
        "on_hold" -> StatusOnHold to Color.White
        "cancelled" -> StatusCancelled to Color.White
        else -> Color.Gray to Color.White
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.replace("_", " ").uppercase(Locale.ROOT),
            color = textColor,
            fontSize = 12.sp,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
