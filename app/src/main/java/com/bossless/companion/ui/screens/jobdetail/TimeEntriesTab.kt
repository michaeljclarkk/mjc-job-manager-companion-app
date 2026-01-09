package com.bossless.companion.ui.screens.jobdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bossless.companion.data.models.TimeEntry
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class TimeEntryFormData(
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val finishDate: LocalDate = LocalDate.now(),
    val finishTime: LocalTime = LocalTime.of(17, 0),
    val note: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeEntriesTab(
    timeEntries: List<TimeEntry>,
    isLoading: Boolean,
    onAddEntry: (startTime: String, finishTime: String, durationSeconds: Int, note: String?) -> Unit,
    onUpdateEntry: (entryId: String, startTime: String, finishTime: String, durationSeconds: Int, note: String?) -> Unit,
    onDeleteEntry: (entryId: String) -> Unit
) {
    var showAddForm by remember { mutableStateOf(false) }
    var editingEntryId by remember { mutableStateOf<String?>(null) }
    var formData by remember { mutableStateOf(TimeEntryFormData()) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var timeError by remember { mutableStateOf<String?>(null) }
    
    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    
    // Calculate total hours
    val totalSeconds = timeEntries.sumOf { it.duration_seconds }
    val totalHours = totalSeconds / 3600.0

    Column(modifier = Modifier.fillMaxSize()) {
        // Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                        "Total Time",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        String.format("%.2f hours", totalHours),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    "${timeEntries.size} entries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Add Entry Button
        if (!showAddForm && editingEntryId == null) {
            Button(
                onClick = {
                    formData = TimeEntryFormData()
                    showAddForm = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Time Entry")
            }
        }

        // Add/Edit Form
        if (showAddForm || editingEntryId != null) {
            TimeEntryForm(
                formData = formData,
                onFormDataChange = { 
                    formData = it
                    timeError = null // Clear error when user changes input
                },
                onSubmit = {
                    val zoneId = ZoneId.systemDefault()
                    val startInstant = formData.startDate.atTime(formData.startTime)
                        .atZone(zoneId).toInstant()
                    val finishInstant = formData.finishDate.atTime(formData.finishTime)
                        .atZone(zoneId).toInstant()
                    val durationSeconds = Duration.between(startInstant, finishInstant).seconds.toInt()
                    
                    if (durationSeconds <= 0) {
                        timeError = "Finish time must be after start time"
                        return@TimeEntryForm
                    }
                    timeError = null
                    
                    if (editingEntryId != null) {
                        onUpdateEntry(
                            editingEntryId!!,
                            startInstant.toString(),
                            finishInstant.toString(),
                            durationSeconds,
                            formData.note.ifBlank { null }
                        )
                        editingEntryId = null
                    } else {
                        onAddEntry(
                            startInstant.toString(),
                            finishInstant.toString(),
                            durationSeconds,
                            formData.note.ifBlank { null }
                        )
                        showAddForm = false
                    }
                },
                onCancel = {
                    showAddForm = false
                    editingEntryId = null
                    timeError = null
                },
                isEditing = editingEntryId != null,
                errorMessage = timeError
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Time Entries List
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (timeEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No time entries yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(timeEntries, key = { it.id }) { entry ->
                    TimeEntryCard(
                        entry = entry,
                        dateFormatter = dateFormatter,
                        timeFormatter = timeFormatter,
                        onEdit = {
                            val startInstant = Instant.parse(entry.start_time)
                            val finishInstant = entry.finish_time?.let { Instant.parse(it) } ?: Instant.now()
                            val zoneId = ZoneId.systemDefault()
                            
                            formData = TimeEntryFormData(
                                startDate = startInstant.atZone(zoneId).toLocalDate(),
                                startTime = startInstant.atZone(zoneId).toLocalTime(),
                                finishDate = finishInstant.atZone(zoneId).toLocalDate(),
                                finishTime = finishInstant.atZone(zoneId).toLocalTime(),
                                note = entry.start_note ?: ""
                            )
                            editingEntryId = entry.id
                        },
                        onDelete = { showDeleteConfirm = entry.id }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteConfirm?.let { entryId ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete Time Entry?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteEntry(entryId)
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TimeEntryForm(
    formData: TimeEntryFormData,
    onFormDataChange: (TimeEntryFormData) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    isEditing: Boolean,
    errorMessage: String? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isEditing) "Edit Time Entry" else "Add Time Entry",
                style = MaterialTheme.typography.titleMedium
            )
            
            // Error message
            errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Start Date & Time Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DatePickerField(
                    label = "Start Date",
                    value = formData.startDate,
                    onValueChange = { onFormDataChange(formData.copy(startDate = it)) },
                    modifier = Modifier.weight(1f)
                )
                TimePickerField(
                    label = "Start Time",
                    value = formData.startTime,
                    onValueChange = { onFormDataChange(formData.copy(startTime = it)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Finish Date & Time Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DatePickerField(
                    label = "Finish Date",
                    value = formData.finishDate,
                    onValueChange = { onFormDataChange(formData.copy(finishDate = it)) },
                    modifier = Modifier.weight(1f)
                )
                TimePickerField(
                    label = "Finish Time",
                    value = formData.finishTime,
                    onValueChange = { onFormDataChange(formData.copy(finishTime = it)) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Note Field
            OutlinedTextField(
                value = formData.note,
                onValueChange = { onFormDataChange(formData.copy(note = it)) },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2
            )

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSubmit) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isEditing) "Update" else "Add")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    value: LocalDate,
    onValueChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    var showDatePicker by remember { mutableStateOf(false) }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = value.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    
    OutlinedTextField(
        value = value.format(formatter),
        onValueChange = { /* Read-only, use date picker */ },
        label = { Text(label) },
        modifier = modifier
            .clickable { showDatePicker = true },
        readOnly = true,
        singleLine = true,
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onValueChange(selectedDate)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun TimePickerField(
    label: String,
    value: LocalTime,
    onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    
    // Use separate text state to allow free-form editing without resetting
    var textValue by remember(value) { mutableStateOf(value.format(formatter)) }
    var isError by remember { mutableStateOf(false) }
    
    OutlinedTextField(
        value = textValue,
        onValueChange = { input ->
            // Allow free-form editing - only keep digits and colon
            val filtered = input.filter { it.isDigit() || it == ':' }.take(5)
            textValue = filtered
            
            // Try to parse and update the actual value
            try {
                val parts = filtered.split(":")
                if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                    val hours = parts[0].toIntOrNull()
                    val minutes = parts[1].toIntOrNull()
                    if (hours != null && minutes != null && hours in 0..23 && minutes in 0..59) {
                        onValueChange(LocalTime.of(hours, minutes))
                        isError = false
                    } else {
                        isError = true
                    }
                } else {
                    isError = filtered.isNotEmpty() // Only show error if there's input
                }
            } catch (e: Exception) {
                isError = true
            }
        },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        isError = isError,
        placeholder = { Text("HH:mm") }
    )
}

@Composable
private fun TimeEntryCard(
    entry: TimeEntry,
    dateFormatter: DateTimeFormatter,
    timeFormatter: DateTimeFormatter,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val zoneId = ZoneId.systemDefault()
    val startInstant = Instant.parse(entry.start_time)
    val startZoned = startInstant.atZone(zoneId)
    val finishZoned = entry.finish_time?.let { Instant.parse(it).atZone(zoneId) }
    
    val durationText = formatDuration(entry.duration_seconds)

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Date
                Text(
                    startZoned.format(dateFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                // Time range
                Text(
                    if (finishZoned != null) {
                        "${startZoned.format(timeFormatter)} - ${finishZoned.format(timeFormatter)}"
                    } else {
                        "${startZoned.format(timeFormatter)} - Running..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Duration
                Text(
                    durationText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                // Note
                entry.start_note?.let { note ->
                    if (note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Action buttons
            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}
