package com.bossless.companion.di

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class RetrofitBuilderProvider(
    private val securePrefs: SecurePrefs
) {
    fun createApiService(okHttpClient: OkHttpClient): ApiService {
        val url = securePrefs.getServerUrl() ?: "http://localhost/"

        // Ensure URL ends with /
        val baseUrl = if (url.endsWith("/")) url else "$url/"

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
