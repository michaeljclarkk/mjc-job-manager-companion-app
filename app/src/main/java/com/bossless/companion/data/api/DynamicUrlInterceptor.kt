package com.bossless.companion.data.api

import com.bossless.companion.BuildConfig
import com.bossless.companion.data.local.SecurePrefs
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class DynamicUrlInterceptor @Inject constructor(
    private val securePrefs: SecurePrefs
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        
        // Don't rewrite URLs for external update manifest requests
        val updateManifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim().toHttpUrlOrNull()
        if (updateManifestUrl != null && request.url.host == updateManifestUrl.host) {
            return chain.proceed(request)
        }
        
        val savedUrlStr = securePrefs.getServerUrl()

        if (!savedUrlStr.isNullOrBlank()) {
            val savedUrl = savedUrlStr.toHttpUrlOrNull()
            if (savedUrl != null) {
                val newUrl = request.url.newBuilder()
                    .scheme(savedUrl.scheme)
                    .host(savedUrl.host)
                    .port(savedUrl.port)
                    .build()
                
                request = request.newBuilder()
                    .url(newUrl)
                    .build()
            }
        }
        
        return chain.proceed(request)
    }
}
