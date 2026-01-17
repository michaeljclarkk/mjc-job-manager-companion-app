package com.bossless.companion.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bossless.companion.data.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeatureFlagsViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val _isSchedulerEnabled = MutableStateFlow(false)
    val isSchedulerEnabled: StateFlow<Boolean> = _isSchedulerEnabled.asStateFlow()

    private val _isStripePaymentsEnabled = MutableStateFlow(false)
    val isStripePaymentsEnabled: StateFlow<Boolean> = _isStripePaymentsEnabled.asStateFlow()

    init {
        checkFeatureFlags()
    }

    private fun checkFeatureFlags() {
        checkSchedulerFeature()
        checkStripePaymentsFeature()
    }

    fun checkSchedulerFeature() {
        viewModelScope.launch {
            _isSchedulerEnabled.value = scheduleRepository.isSchedulerEnabled()
        }
    }

    fun checkStripePaymentsFeature() {
        viewModelScope.launch {
            _isStripePaymentsEnabled.value = scheduleRepository.isStripePaymentsEnabled()
        }
    }

    fun refresh() {
        checkFeatureFlags()
    }
}
