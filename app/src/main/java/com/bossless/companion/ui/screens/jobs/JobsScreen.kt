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
    
    // Filter jobs based on search query
    val filteredJobs = remember(uiState.jobs, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.jobs
        } else {
            val query = uiState.searchQuery.lowercase()
            uiState.jobs.filter { assignment ->
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
        Box(modifier = Modifier.padding(padding)) {
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
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredJobs) { assignment ->
                        JobCard(assignment, onJobClick)
                    }
                }
            }
        }
    }
}

@Composable
fun JobCard(assignment: JobAssignment, onClick: (String) -> Unit) {
    val job = assignment.jobs ?: return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(job.id) },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
