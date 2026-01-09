package com.bossless.companion.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.repository.AuthRepository
import com.bossless.companion.di.OkHttpClientFactory
import com.bossless.companion.di.RetrofitBuilderProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val securePrefs: SecurePrefs,
    private val okHttpClientFactory: OkHttpClientFactory,
    private val retrofitBuilderProvider: RetrofitBuilderProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // Load saved values
        val savedUrl = securePrefs.getServerUrl() ?: ""
        val savedApiKey = securePrefs.getApiKey() ?: ""
        val trustCerts = securePrefs.getTrustAllCerts()
        
        _uiState.value = _uiState.value.copy(
            serverUrl = savedUrl,
            apiKey = savedApiKey,
            trustAllCerts = trustCerts
        )
    }

    fun onServerUrlChanged(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url)
    }

    fun onApiKeyChanged(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key)
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun onTrustAllCertsChanged(trust: Boolean) {
        _uiState.value = _uiState.value.copy(trustAllCerts = trust)
        securePrefs.setTrustAllCerts(trust)
        
        // Recreate the HTTP client and ApiService with the new SSL setting
        viewModelScope.launch {
            try {
                val newClient = okHttpClientFactory.createClient()
                val newApiService = retrofitBuilderProvider.createApiService(newClient)
                // Reinject the new ApiService into AuthRepository
                authRepository.updateApiService(newApiService)
            } catch (e: Exception) {
                // Silently ignore - will be caught on next login attempt
            }
        }
    }

    fun login() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.apiKey.isBlank() || state.email.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(error = "All fields are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            
            val result = authRepository.login(
                serverUrl = state.serverUrl,
                apiKey = state.apiKey,
                email = state.email,
                password = state.password
            )

            if (result.isSuccess) {
                _uiState.value = state.copy(isLoading = false, isSuccess = true)
            } else {
                _uiState.value = state.copy(isLoading = false, error = result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }
}

data class LoginUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val email: String = "",
    val password: String = "",
    val trustAllCerts: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)
