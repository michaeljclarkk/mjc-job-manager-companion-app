package com.bossless.companion.di

import com.bossless.companion.data.api.AuthInterceptor
import com.bossless.companion.data.api.DynamicUrlInterceptor
import com.bossless.companion.data.api.TokenAuthenticator
import com.bossless.companion.data.api.UnsafeOkHttpClient
import com.bossless.companion.data.local.SecurePrefs
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

class OkHttpClientFactory(
    private val securePrefs: SecurePrefs,
    private val authInterceptor: AuthInterceptor,
    private val dynamicUrlInterceptor: DynamicUrlInterceptor,
    private val tokenAuthenticator: TokenAuthenticator
) {
    fun createClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val builder = if (securePrefs.getTrustAllCerts()) {
            UnsafeOkHttpClient.getUnsafeOkHttpClient()
        } else {
            OkHttpClient.Builder()
        }

        return builder
            .addInterceptor(dynamicUrlInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .authenticator(tokenAuthenticator)
            .build()
    }
}
