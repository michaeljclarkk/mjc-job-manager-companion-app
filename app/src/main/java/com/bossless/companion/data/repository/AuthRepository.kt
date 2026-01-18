package com.bossless.companion.data.repository

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.models.LoginRequest
import com.bossless.companion.data.models.RefreshTokenRequest
import com.bossless.companion.data.models.User
import com.bossless.companion.utils.ErrorReporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private var apiService: ApiService,
    private val securePrefs: SecurePrefs
) {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        checkLoginState()
    }

    fun updateApiService(newApiService: ApiService) {
        this.apiService = newApiService
    }

    private fun checkLoginState() {
        val userId = securePrefs.getUserId()
        val email = securePrefs.getUserEmail()
        if (userId != null && email != null) {
            _currentUser.value = User(id = userId, email = email)
            _isLoggedIn.value = true
        } else {
            _isLoggedIn.value = false
        }
    }

    suspend fun login(serverUrl: String, apiKey: String, email: String, password: String): Result<Unit> {
        return try {
            // Save server URL and API key first as they might be needed for the request (though we pass them explicitly here)
            securePrefs.saveServerUrl(serverUrl)
            securePrefs.saveApiKey(apiKey)

            val response = apiService.login(
                apiKey = apiKey,
                request = LoginRequest(email, password)
            )

            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                val expiresAtEpochSeconds = (System.currentTimeMillis() / 1000L) + authResponse.expires_in
                securePrefs.saveAuthToken(
                    authResponse.access_token,
                    authResponse.refresh_token,
                    authResponse.user.id,
                    authResponse.user.email ?: "",
                    tokenExpiresAtEpochSeconds = expiresAtEpochSeconds
                )
                _currentUser.value = authResponse.user
                _isLoggedIn.value = true
                
                // Initialize ErrorReporter with the new server URL and user ID
                ErrorReporter.init(serverUrl, authResponse.user.id)
                
                Result.success(Unit)
            } else {
                val error = Exception("Login failed: ${response.code()} ${response.message()}")
                ErrorReporter.logAndReportError(
                    context = "AuthRepository.login",
                    error = error,
                    additionalInfo = mapOf("email" to email, "responseCode" to response.code().toString())
                )
                Result.failure(error)
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "AuthRepository.login",
                error = e,
                additionalInfo = mapOf("email" to email, "serverUrl" to serverUrl)
            )
            Result.failure(e)
        }
    }

    suspend fun refreshToken(): Boolean {
        val refreshToken = securePrefs.getRefreshToken() ?: return false
        val apiKey = securePrefs.getApiKey() ?: return false

        return try {
            val response = apiService.refreshToken(
                apiKey = apiKey,
                request = RefreshTokenRequest(refreshToken)
            )

            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                val expiresAtEpochSeconds = (System.currentTimeMillis() / 1000L) + authResponse.expires_in
                securePrefs.saveAuthToken(
                    authResponse.access_token,
                    authResponse.refresh_token,
                    authResponse.user.id,
                    authResponse.user.email ?: "",
                    tokenExpiresAtEpochSeconds = expiresAtEpochSeconds
                )
                true
            } else {
                ErrorReporter.logAndReportError(
                    context = "AuthRepository.refreshToken",
                    message = "Token refresh failed: ${response.code()} ${response.message()}",
                    userId = securePrefs.getUserId()
                )
                logout()
                false
            }
        } catch (e: Exception) {
            ErrorReporter.logAndReportError(
                context = "AuthRepository.refreshToken",
                error = e,
                userId = securePrefs.getUserId()
            )
            false
        } 
    }

    fun logout() {
        ErrorReporter.setUserId(null)
        securePrefs.clearAuth()
        _currentUser.value = null
        _isLoggedIn.value = false
    }
}
