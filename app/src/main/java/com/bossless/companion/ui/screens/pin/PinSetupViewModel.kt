package com.bossless.companion.ui.screens.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bossless.companion.data.local.SecurePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val securePrefs: SecurePrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinSetupUiState())
    val uiState: StateFlow<PinSetupUiState> = _uiState.asStateFlow()

    fun onPinChanged(value: String) {
        _uiState.value = _uiState.value.copy(pin = sanitizePin(value), error = null)
    }

    fun onConfirmPinChanged(value: String) {
        _uiState.value = _uiState.value.copy(confirmPin = sanitizePin(value), error = null)
    }

    fun next() {
        val state = _uiState.value
        if (state.pin.length != 4) {
            _uiState.value = state.copy(error = "PIN must be 4 digits")
            return
        }

        _uiState.value = state.copy(step = PinSetupStep.CONFIRM, error = null)
    }

    fun back() {
        val state = _uiState.value
        _uiState.value = state.copy(step = PinSetupStep.CREATE, confirmPin = "", error = null)
    }

    fun savePin() {
        val state = _uiState.value
        if (state.step != PinSetupStep.CONFIRM) {
            next()
            return
        }

        if (state.confirmPin.length != 4) {
            _uiState.value = state.copy(error = "PIN must be 4 digits")
            return
        }

        if (state.pin != state.confirmPin) {
            _uiState.value = state.copy(error = "PINs do not match")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            securePrefs.savePin(state.pin)
            _uiState.value = state.copy(isLoading = false, isSuccess = true)
        }
    }

    private fun sanitizePin(value: String): String {
        return value.filter { it.isDigit() }.take(4)
    }
}

enum class PinSetupStep {
    CREATE,
    CONFIRM
}

data class PinSetupUiState(
    val step: PinSetupStep = PinSetupStep.CREATE,
    val pin: String = "",
    val confirmPin: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)
