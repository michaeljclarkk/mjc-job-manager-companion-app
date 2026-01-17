package com.bossless.companion.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bossless.companion.data.models.ScheduledJobAssignment
import com.bossless.companion.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class DailyScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyScheduleUiState())
    val uiState: StateFlow<DailyScheduleUiState> = _uiState.asStateFlow()

    private val displayDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

    init {
        loadTodaysSchedule()
    }

    fun loadTodaysSchedule() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                selectedDate = LocalDate.now(),
                displayDate = LocalDate.now().format(displayDateFormatter)
            )

            val result = scheduleRepository.getTodaysSchedule()

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    scheduledJobs = result.getOrDefault(emptyList()),
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load schedule"
                )
            }
        }
    }

    fun refresh() {
        loadScheduleForDate(_uiState.value.selectedDate)
    }

    fun goToToday() {
        loadScheduleForDate(LocalDate.now())
    }

    fun goToPreviousDay() {
        loadScheduleForDate(_uiState.value.selectedDate.minusDays(1))
    }

    fun goToNextDay() {
        loadScheduleForDate(_uiState.value.selectedDate.plusDays(1))
    }

    private fun loadScheduleForDate(date: LocalDate) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                selectedDate = date,
                displayDate = date.format(displayDateFormatter)
            )

            val result = scheduleRepository.getScheduleForDate(date)

            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    scheduledJobs = result.getOrDefault(emptyList()),
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to load schedule"
                )
            }
        }
    }
}

data class DailyScheduleUiState(
    val scheduledJobs: List<ScheduledJobAssignment> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedDate: LocalDate = LocalDate.now(),
    val displayDate: String = ""
)
