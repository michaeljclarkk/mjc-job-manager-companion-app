package com.bossless.companion.ui.screens.jobs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.models.JobAssignment
import com.bossless.companion.data.repository.BusinessProfileRepository
import com.bossless.companion.data.repository.JobsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class JobsViewModel @Inject constructor(
    private val jobsRepository: JobsRepository,
    private val businessProfileRepository: BusinessProfileRepository,
    private val securePrefs: SecurePrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(JobsUiState(
        // Load cached logo immediately for fast display
        businessLogoUrl = businessProfileRepository.getCachedLogoUrl()
    ))
    val uiState: StateFlow<JobsUiState> = _uiState.asStateFlow()

    private var lastUpdated: Long? = null

    init {
        observeJobs()
        loadJobs()
        loadBusinessProfile()
    }
    
    private fun loadBusinessProfile() {
        viewModelScope.launch {
            val result = businessProfileRepository.fetchAndCacheBusinessProfile()
            result.onSuccess { profile ->
                _uiState.value = _uiState.value.copy(
                    businessLogoUrl = profile?.logo_url
                )
            }
        }
    }

    private fun observeJobs() {
        viewModelScope.launch {
            jobsRepository.getAssignedJobsFlow().collectLatest { jobs ->
                _uiState.value = _uiState.value.copy(jobs = jobs)
            }
        }
    }

    fun loadJobs() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            val result = jobsRepository.getAssignedJobs()
            
            if (result.isSuccess) {
                lastUpdated = Instant.now().epochSecond
                securePrefs.saveLastSyncTimestamp(lastUpdated!!)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    jobs = result.getOrDefault(emptyList()),
                    lastUpdated = lastUpdated
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load jobs",
                    lastUpdated = lastUpdated
                )
            }
        }
    }
    
    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }
    
    fun toggleSearch() {
        val newIsActive = !_uiState.value.isSearchActive
        _uiState.value = _uiState.value.copy(
            isSearchActive = newIsActive,
            searchQuery = if (!newIsActive) "" else _uiState.value.searchQuery
        )
    }
    
    fun closeSearch() {
        _uiState.value = _uiState.value.copy(isSearchActive = false, searchQuery = "")
    }
    
    fun setFilter(filter: JobFilter) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }
}

enum class JobFilter {
    ACTIVE,    // Everything except cancelled/completed
    COMPLETED, // Only completed jobs
    ALL        // Everything including cancelled
}

data class JobsUiState(
    val jobs: List<JobAssignment> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdated: Long? = null,
    val businessLogoUrl: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedFilter: JobFilter = JobFilter.ACTIVE
)
