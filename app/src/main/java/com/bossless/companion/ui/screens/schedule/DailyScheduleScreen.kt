package com.bossless.companion.ui.screens.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bossless.companion.data.models.ScheduledJobAssignment
import com.bossless.companion.ui.components.LoadingIndicator
import com.bossless.companion.ui.components.StatusBadge
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyScheduleScreen(
    onJobClick: (String) -> Unit,
    viewModel: DailyScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Update current time every minute for live highlighting
    val currentTime by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(60_000L) // Update every minute
        }
    }
    
    val isToday = uiState.selectedDate == LocalDate.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Schedule") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Date Navigation Header
            DateNavigationHeader(
                displayDate = uiState.displayDate,
                isToday = isToday,
                onPreviousDay = { viewModel.goToPreviousDay() },
                onNextDay = { viewModel.goToNextDay() },
                onToday = { viewModel.goToToday() }
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
                uiState.error != null -> {
                    ErrorContent(
                        message = uiState.error!!,
                        onRetry = { viewModel.refresh() }
                    )
                }
                uiState.scheduledJobs.isEmpty() -> {
                    EmptyScheduleContent()
                }
                else -> {
                    ScheduleTimeline(
                        jobs = uiState.scheduledJobs,
                        onJobClick = onJobClick,
                        isToday = isToday,
                        currentTime = currentTime
                    )
                }
            }
        }
    }
}

@Composable
private fun DateNavigationHeader(
    displayDate: String,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousDay) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Previous day"
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (isToday) "Today" else displayDate.substringBefore(","),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!isToday || displayDate.contains(",")) {
                    Text(
                        text = if (isToday) displayDate else displayDate.substringAfter(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isToday) {
                TextButton(onClick = onToday) {
                    Text("Today")
                }
            } else {
                Spacer(modifier = Modifier.width(48.dp))
            }

            IconButton(onClick = onNextDay) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Next day"
                )
            }
        }
    }
}

@Composable
private fun ScheduleTimeline(
    jobs: List<ScheduledJobAssignment>,
    onJobClick: (String) -> Unit,
    isToday: Boolean,
    currentTime: LocalTime
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        itemsIndexed(jobs) { index, assignment ->
            // Determine time status for today's jobs
            val timeStatus = if (isToday) {
                getTimeStatus(assignment, currentTime, jobs.getOrNull(index + 1))
            } else {
                TimeStatus.UPCOMING
            }
            
            ScheduleJobCard(
                assignment = assignment,
                isFirst = index == 0,
                isLast = index == jobs.lastIndex,
                timeStatus = timeStatus,
                onClick = { onJobClick(assignment.job_id) }
            )
        }
    }
}

private enum class TimeStatus {
    PAST,    // Job time has passed
    CURRENT, // Currently scheduled (between start and finish, or current slot)
    UPCOMING // Not yet started
}

private fun getTimeStatus(
    assignment: ScheduledJobAssignment,
    currentTime: LocalTime,
    nextAssignment: ScheduledJobAssignment?
): TimeStatus {
    val startTime = parseTime(assignment.planned_start_time)
    val finishTime = parseTime(assignment.planned_finish_time)
    val nextStartTime = parseTime(nextAssignment?.planned_start_time)
    
    return when {
        startTime == null -> TimeStatus.UPCOMING
        finishTime != null -> {
            // Has explicit finish time
            when {
                currentTime < startTime -> TimeStatus.UPCOMING
                currentTime >= startTime && currentTime < finishTime -> TimeStatus.CURRENT
                else -> TimeStatus.PAST
            }
        }
        nextStartTime != null -> {
            // No finish time, use next job's start as implicit end
            when {
                currentTime < startTime -> TimeStatus.UPCOMING
                currentTime >= startTime && currentTime < nextStartTime -> TimeStatus.CURRENT
                else -> TimeStatus.PAST
            }
        }
        else -> {
            // Last job with no finish time - current if started
            if (currentTime >= startTime) TimeStatus.CURRENT else TimeStatus.UPCOMING
        }
    }
}

private fun parseTime(time: String?): LocalTime? {
    if (time == null) return null
    return try {
        // Handle both "HH:mm" and "HH:mm:ss" formats
        val parts = time.split(":")
        if (parts.size >= 2) {
            LocalTime.of(parts[0].toInt(), parts[1].toInt())
        } else null
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun ScheduleJobCard(
    assignment: ScheduledJobAssignment,
    isFirst: Boolean,
    isLast: Boolean,
    timeStatus: TimeStatus,
    onClick: () -> Unit
) {
    val job = assignment.jobs ?: return
    
    // Colors based on time status
    val isPast = timeStatus == TimeStatus.PAST
    val isCurrent = timeStatus == TimeStatus.CURRENT
    
    val dotColor = when (timeStatus) {
        TimeStatus.PAST -> MaterialTheme.colorScheme.outlineVariant
        TimeStatus.CURRENT -> MaterialTheme.colorScheme.primary
        TimeStatus.UPCOMING -> MaterialTheme.colorScheme.primary
    }
    val primaryColor = dotColor
    val connectorColor = MaterialTheme.colorScheme.outlineVariant
    val contentAlpha = if (isPast) 0.5f else 1f

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Timeline connector
        Box(
            modifier = Modifier.width(56.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Time display (formatted to HH:mm)
                Text(
                    text = formatTimeShort(assignment.planned_start_time) ?: "--:--",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alpha(contentAlpha)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Timeline dot with ring
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(primaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary)
                    )
                }

                // Connecting line with arrow (if not last)
                if (!isLast) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(70.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(primaryColor, connectorColor)
                                )
                            )
                    )
                    // Arrow pointing down
                    Canvas(
                        modifier = Modifier.size(12.dp)
                    ) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width / 2, size.height)
                            lineTo(0f, 0f)
                            lineTo(size.width, 0f)
                            close()
                        }
                        drawPath(path, color = connectorColor)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Job Card
        Card(
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp)
                .alpha(contentAlpha),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCurrent) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            border = if (isCurrent) BorderStroke(
                2.dp, MaterialTheme.colorScheme.primary
            ) else null
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Job number and status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = job.job_number,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    StatusBadge(status = job.status)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Job name
                Text(
                    text = job.name ?: "Unnamed Job",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Time range
                if (assignment.planned_start_time != null || assignment.planned_finish_time != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimeRange(assignment.planned_start_time, assignment.planned_finish_time),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Location
                if (!job.location.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = job.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyScheduleContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No jobs scheduled for this day",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Unable to load schedule",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

private fun formatTimeRange(start: String?, finish: String?): String {
    val formattedStart = formatTimeShort(start)
    val formattedFinish = formatTimeShort(finish)
    return when {
        formattedStart != null && formattedFinish != null -> "$formattedStart - $formattedFinish"
        formattedStart != null -> "From $formattedStart"
        formattedFinish != null -> "Until $formattedFinish"
        else -> ""
    }
}

/**
 * Format time string to HH:mm (removes seconds if present)
 * Input: "08:00:00" or "08:00" → Output: "08:00"
 */
private fun formatTimeShort(time: String?): String? {
    if (time == null) return null
    // Take only first 5 chars (HH:mm) if longer
    return if (time.length >= 5) time.substring(0, 5) else time
}
