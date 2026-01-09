package com.bossless.companion.data.api

import android.util.Log
import com.bossless.companion.BuildConfig
import com.bossless.companion.data.auth.AuthGateEvent
import com.bossless.companion.data.auth.AuthGateEventBus
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.models.AuthResponse
import com.bossless.companion.data.models.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Authenticator that handles 401 responses by refreshing the JWT token.
 * This automatically retries failed requests with a new access token.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val securePrefs: SecurePrefs,
    private val authGateEventBus: AuthGateEventBus
) : Authenticator {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private const val TAG = "TokenAuthenticator"
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private sealed interface RefreshOutcome {
        data class Success(val authResponse: AuthResponse) : RefreshOutcome
        data object AuthRejected : RefreshOutcome
        data object NetworkError : RefreshOutcome
        data object ParseError : RefreshOutcome
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        // Don't retry if we've already tried to refresh
        if (response.request.header("X-Retry-With-Refresh") != null) {
            Log.w(TAG, "Already attempted token refresh, not retrying again")
            return null
        }

        // Don't try to refresh for auth endpoints themselves
        if (response.request.url.encodedPath.contains("/auth/v1/")) {
            logDebug("Auth endpoint failed, not attempting refresh")
            return null
        }

        logDebug("Got 401, attempting token refresh")

        val refreshToken = securePrefs.getRefreshToken()
        if (refreshToken.isNullOrBlank()) {
            Log.w(TAG, "No refresh token available, cannot refresh")
            return null
        }

        val serverUrl = securePrefs.getServerUrl()
        val apiKey = securePrefs.getApiKey()
        if (serverUrl.isNullOrBlank() || apiKey.isNullOrBlank()) {
            Log.w(TAG, "Missing server URL or API key")
            return null
        }

        // Perform token refresh synchronously (OkHttp Authenticator runs on background thread)
        val outcome = runBlocking {
            try {
                refreshAccessToken(serverUrl, apiKey, refreshToken)
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh failed", e)
                RefreshOutcome.NetworkError
            }
        }

        return when (outcome) {
            is RefreshOutcome.Success -> {
                val authResponse = outcome.authResponse
                val expiresAtEpochSeconds = (System.currentTimeMillis() / 1000L) + authResponse.expires_in
                securePrefs.saveAuthToken(
                    accessToken = authResponse.access_token,
                    refreshToken = authResponse.refresh_token,
                    userId = authResponse.user.id,
                    email = authResponse.user.email ?: "",
                    tokenExpiresAtEpochSeconds = expiresAtEpochSeconds
                )

                logDebug("Token refresh successful, retrying request")
                response.request.newBuilder()
                    .header("Authorization", "Bearer ${authResponse.access_token}")
                    .header("X-Retry-With-Refresh", "true")
                    .build()
            }

            RefreshOutcome.AuthRejected, RefreshOutcome.ParseError -> {
                Log.w(TAG, "Token refresh rejected; requiring PIN unlock")
                if (securePrefs.hasPin()) {
                    securePrefs.setPinUnlockRequired(true)
                    authGateEventBus.tryEmit(AuthGateEvent.RequirePinUnlock)
                } else {
                    // If there is no PIN set, fall back to normal auth clearing behaviour.
                    securePrefs.clearAuth()
                }
                null
            }

            RefreshOutcome.NetworkError -> {
                Log.w(TAG, "Token refresh failed due to network; not forcing PIN")
                null
            }
        }
    }

    private suspend fun refreshAccessToken(
        serverUrl: String,
        apiKey: String,
        refreshToken: String
    ): RefreshOutcome {
        // Create a simple OkHttp client for the refresh call (without our interceptors to avoid loops)
        val client = if (securePrefs.getTrustAllCerts()) {
            UnsafeOkHttpClient.getUnsafeOkHttpClient().build()
        } else {
            OkHttpClient.Builder().build()
        }

        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val url = "${baseUrl}auth/v1/token?grant_type=refresh_token"

        val requestBody = json.encodeToString(
            RefreshTokenRequest.serializer(),
            RefreshTokenRequest(refreshToken)
        ).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("apikey", apiKey)
            .addHeader("Content-Type", "application/json")
            .build()

        val response = client.newCall(request).execute()

        return if (response.isSuccessful) {
            val body = response.body?.string()
            if (body != null) {
                try {
                    // Parse the response to get new tokens
                    val authResponse = json.decodeFromString<AuthResponse>(body)

                    logDebug("Parsed new tokens after refresh")
                    RefreshOutcome.Success(authResponse)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse refresh response", e)
                    RefreshOutcome.ParseError
                }
            } else {
                RefreshOutcome.ParseError
            }
        } else {
            Log.e(TAG, "Refresh request failed: ${response.code}")
            RefreshOutcome.AuthRejected
        }
    }
}
