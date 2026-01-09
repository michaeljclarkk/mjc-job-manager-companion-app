package com.bossless.companion.ui.screens.timer

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bossless.companion.data.models.Job
import com.bossless.companion.data.models.TimeEntry
import com.bossless.companion.data.repository.TimeEntriesRepository
import com.bossless.companion.data.repository.TimeEntriesRepositoryOffline
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.service.TimerService
import com.bossless.companion.utils.ErrorReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject



@HiltViewModel
class TimerViewModel @Inject constructor(
    private val timeEntriesRepository: TimeEntriesRepository,
    private val timeEntriesRepositoryOffline: TimeEntriesRepositoryOffline,
    private val securePrefs: SecurePrefs,
    private val application: Application // Inject Application context for starting service
) : ViewModel() {

    fun startTimer(jobId: String, note: String? = null) {
        viewModelScope.launch {
            val userId = securePrefs.getUserId() ?: return@launch
            
            // Check if there's already an active timer
            val activeTimer = timeEntriesRepositoryOffline.getActiveTimeEntryForUser(userId)
            if (activeTimer != null) {
                // Timer already running, don't start a new one
                return@launch
            }
            
            val newEntryStartTime = Instant.now().toString()
            
            // Try to create on server first
            val result = timeEntriesRepository.startTimeEntry(jobId, newEntryStartTime, note)
            
            val entry = if (result.isSuccess) {
                result.getOrNull()!!
            } else {
                TimeEntry(
                    id = java.util.UUID.randomUUID().toString(),
                    job_id = jobId,
                    user_id = userId,
                    start_time = newEntryStartTime,
                    finish_time = null,
                    start_note = note,
                    finish_note = null,
                    duration_seconds = 0,
                    created_at = newEntryStartTime
                )
            }
            
            timeEntriesRepositoryOffline.insertTimeEntry(entry, synced = result.isSuccess)
            TimerService.startService(application.applicationContext, newEntryStartTime, "")
            checkActiveTimer()
        }
    }

    private val _uiState = MutableStateFlow(TimerUiState(
        businessLogoUrl = securePrefs.getBusinessLogoUrl()
    ))
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    init {
        checkActiveTimer()
    }

    fun checkActiveTimer() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val userId = securePrefs.getUserId()
            if (userId == null) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }
            val entry = timeEntriesRepositoryOffline.getActiveTimeEntryForUser(userId)
            _uiState.value = _uiState.value.copy(
                activeEntry = entry,
                isLoading = false
            )
            // Fetch job info if we have an active entry
            if (entry != null) {
                val jobResult = timeEntriesRepository.getJob(entry.job_id)
                if (jobResult.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        activeJob = jobResult.getOrNull()
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(activeJob = null)
            }
        }
    }

    fun stopTimer(note: String?) {
        val entry = _uiState.value.activeEntry ?: return
        
        viewModelScope.launch {
            val finishNote = note?.trim().takeUnless { it.isNullOrBlank() } ?: "Finish timer"
            val now = Instant.now()
            val start = Instant.parse(entry.start_time)
            val duration = Duration.between(start, now).seconds.toInt()
            
            val result = timeEntriesRepository.stopTimeEntry(
                entryId = entry.id,
                finishTime = now.toString(),
                durationSeconds = duration,
                note = finishNote
            )
            
            if (result.isSuccess) {
                val updatedEntry = result.getOrNull() ?: entry.copy(
                    finish_time = now.toString(),
                    finish_note = finishNote,
                    duration_seconds = duration
                )
                timeEntriesRepositoryOffline.insertTimeEntry(updatedEntry, synced = true) // Mark as synced after successful remote stop
                TimerService.stopService(application.applicationContext)
                checkActiveTimer() // Re-check active timer to update UI state from local source of truth
            } else {
                // If stop failed, check if it was because the entry wasn't found (e.g. created offline)
                if (result.exceptionOrNull() is TimeEntriesRepository.TimeEntryNotFoundException) {
                    // Try to create a full entry instead
                    val fullResult = timeEntriesRepository.createFullTimeEntry(
                        jobId = entry.job_id,
                        startTime = entry.start_time,
                        finishTime = now.toString(),
                        durationSeconds = duration,
                        note = finishNote
                    )
                    
                    if (fullResult.isSuccess) {
                        // Delete the old local entry (with temp ID) and insert the new one
                        timeEntriesRepositoryOffline.deleteTimeEntryById(entry.id)
                        timeEntriesRepositoryOffline.insertTimeEntry(fullResult.getOrNull()!!, synced = true)
                        TimerService.stopService(application.applicationContext)
                        checkActiveTimer()
                    } else {
                        // Both failed: this is now worth reporting.
                        ErrorReporter.logAndEmailError(
                            context = "TimerViewModel.stopTimer",
                            error = fullResult.exceptionOrNull() ?: Exception("Failed to create full time entry"),
                            userId = securePrefs.getUserId(),
                            additionalInfo = mapOf(
                                "entryId" to entry.id,
                                "jobId" to entry.job_id,
                                "finishNoteWasAuto" to (note.isNullOrBlank()).toString()
                            )
                        )
                        // Both failed (likely network), update local entry to stopped state but unsynced
                        val updatedEntry = entry.copy(
                            finish_time = now.toString(),
                            finish_note = finishNote,
                            duration_seconds = duration
                        )
                        timeEntriesRepositoryOffline.insertTimeEntry(updatedEntry, synced = false)
                        TimerService.stopService(application.applicationContext)
                        checkActiveTimer()
                    }
                } else {
                    // Network error or other failure, update local entry
                    val updatedEntry = entry.copy(
                        finish_time = now.toString(),
                        finish_note = finishNote,
                        duration_seconds = duration
                    )
                    timeEntriesRepositoryOffline.insertTimeEntry(updatedEntry, synced = false)
                    TimerService.stopService(application.applicationContext)
                    checkActiveTimer()
                }
            }
        }
    }
}

data class TimerUiState(
    val activeEntry: TimeEntry? = null,
    val activeJob: Job? = null,
    val isLoading: Boolean = false,
    val businessLogoUrl: String? = null
)
