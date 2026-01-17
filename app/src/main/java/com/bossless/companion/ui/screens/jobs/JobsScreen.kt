package com.bossless.companion.ui.screens.jobs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bossless.companion.data.models.JobAssignment
import com.bossless.companion.ui.components.LoadingIndicator
import com.bossless.companion.ui.components.LogoTopAppBar
import com.bossless.companion.ui.components.StatusBadge
import com.bossless.companion.ui.components.ToastHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    onJobClick: (String) -> Unit,
    viewModel: JobsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar("Server Not Available at this time")
        }
    }
    
    // Filter jobs based on selected filter and search query
    val filteredJobs = remember(uiState.jobs, uiState.searchQuery, uiState.selectedFilter) {
        val filterByStatus = when (uiState.selectedFilter) {
            JobFilter.ACTIVE -> uiState.jobs.filter { assignment ->
                val status = assignment.jobs?.status?.lowercase() ?: ""
                status != "cancelled" && status != "completed"
            }
            JobFilter.COMPLETED -> uiState.jobs.filter { assignment ->
                val status = assignment.jobs?.status?.lowercase() ?: ""
                status == "completed"
            }
            JobFilter.ALL -> uiState.jobs
        }
        
        if (uiState.searchQuery.isBlank()) {
            filterByStatus
        } else {
            val query = uiState.searchQuery.lowercase()
            filterByStatus.filter { assignment ->
                val job = assignment.jobs ?: return@filter false
                job.job_number.lowercase().contains(query) ||
                    (job.name?.lowercase()?.contains(query) == true) ||
                    (job.location?.lowercase()?.contains(query) == true) ||
                    (job.status?.lowercase()?.contains(query) == true)
            }
        }
    }

    Scaffold(
        topBar = {
            if (uiState.isSearchActive) {
                // Search mode top bar
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search jobs...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    }
                )
            } else {
                LogoTopAppBar(
                    fallbackTitle = "My Jobs",
                    logoUrl = uiState.businessLogoUrl,
                    actions = {
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { viewModel.loadJobs() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            }
        },
        snackbarHost = { ToastHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Filter tabs
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                JobFilter.entries.forEachIndexed { index, filter ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = JobFilter.entries.size
                        ),
                        onClick = { viewModel.setFilter(filter) },
                        selected = uiState.selectedFilter == filter
                    ) {
                        Text(
                            text = when (filter) {
                                JobFilter.ACTIVE -> "Active"
                                JobFilter.COMPLETED -> "Completed"
                                JobFilter.ALL -> "All"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading) {
                    LoadingIndicator()
                } else if (uiState.error != null && uiState.jobs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
                    }
                } else if (uiState.jobs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No jobs assigned to you.")
                    }
                } else if (filteredJobs.isEmpty() && uiState.searchQuery.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No jobs match \"${uiState.searchQuery}\"")
                    }
                } else if (filteredJobs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = when (uiState.selectedFilter) {
                                JobFilter.ACTIVE -> "No active jobs"
                                JobFilter.COMPLETED -> "No completed jobs"
                                JobFilter.ALL -> "No jobs"
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredJobs) { assignment ->
                            val isArchived = assignment.jobs?.status?.lowercase() in listOf("cancelled", "completed")
                            JobCard(
                                assignment = assignment,
                                onClick = onJobClick,
                                isArchived = isArchived
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun JobCard(
    assignment: JobAssignment,
    onClick: (String) -> Unit,
    isArchived: Boolean = false
) {
    val job = assignment.jobs ?: return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isArchived) 0.7f else 1f)
            .clickable { onClick(job.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isArchived) 1.dp else 2.dp),
        colors = if (isArchived) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.job_number,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                StatusBadge(status = job.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = job.name ?: "Untitled Job",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (!job.location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn, 
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = job.location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            if (!job.scheduled_date.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Scheduled: ${job.scheduled_date}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
