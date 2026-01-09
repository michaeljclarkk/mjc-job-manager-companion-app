package com.bossless.companion.data.api

import com.bossless.companion.data.local.SecurePrefs
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val securePrefs: SecurePrefs
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        // Add apikey if available (needed for all requests except maybe some public ones, but prompt says all)
        // The prompt says: "All API requests (except login) must include: apikey: {stored_anon_key}, Authorization: Bearer {stored_access_token}"
        // Login request handles apikey manually in ApiService definition because it might be different or initial.
        // However, for simplicity, if we have it stored, we can add it.
        // But wait, the login request in ApiService takes apikey as a parameter.
        // Let's check if the request already has apikey header.
        
        val apiKey = securePrefs.getApiKey()
        if (original.header("apikey") == null && !apiKey.isNullOrBlank()) {
            builder.addHeader("apikey", apiKey)
        }

        // Add Authorization header if we have a token
        val token = securePrefs.getAccessToken()
        if (original.header("Authorization") == null && !token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }

        return chain.proceed(builder.build())
    }
}
