package com.bossless.companion.ui.screens.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.repository.AuthRepository
import com.bossless.companion.data.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PinUnlockViewModel @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val authRepository: AuthRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinUnlockUiState())
    val uiState: StateFlow<PinUnlockUiState> = _uiState.asStateFlow()

    fun onPinChanged(value: String) {
        _uiState.value = _uiState.value.copy(pin = sanitizePin(value), error = null)
    }

    fun unlock() {
        val state = _uiState.value
        if (state.pin.length != 4) {
            _uiState.value = state.copy(error = "PIN must be 4 digits")
            return
        }

        if (!securePrefs.verifyPin(state.pin)) {
            _uiState.value = state.copy(error = "Incorrect PIN")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)

            val refreshed = authRepository.refreshToken()
            if (refreshed) {
                securePrefs.setPinUnlockRequired(false)

                // Best-effort: drain any buffered locations now that auth is valid again.
                // Keep it lightweight (single batch) to avoid extra battery/network churn.
                locationRepository.flushBufferedLocations()

                _uiState.value = state.copy(isLoading = false, isSuccess = true)
                return@launch
            }

            // If refresh failed and auth was cleared, force login. Otherwise allow retry.
            val refreshTokenStillPresent = !securePrefs.getRefreshToken().isNullOrBlank()
            if (!refreshTokenStillPresent) {
                _uiState.value = state.copy(isLoading = false, requiresLogin = true)
            } else {
                _uiState.value = state.copy(isLoading = false, error = "Unable to continue. Check connection and try again.")
            }
        }
    }

    fun forgotPin() {
        securePrefs.clearPin()
        authRepository.logout()
        _uiState.value = PinUnlockUiState(requiresLogin = true)
    }

    private fun sanitizePin(value: String): String {
        return value.filter { it.isDigit() }.take(4)
    }
}

data class PinUnlockUiState(
    val pin: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val requiresLogin: Boolean = false
)
