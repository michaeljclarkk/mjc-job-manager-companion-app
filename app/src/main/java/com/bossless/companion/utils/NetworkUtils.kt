package com.bossless.companion.utils

import android.util.Log
import com.bossless.companion.BuildConfig
import com.bossless.companion.data.local.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Utility class for checking network/server availability.
 * Uses a simple HTTP HEAD request to verify server reachability.
 */
@Singleton
class NetworkUtils @Inject constructor(
    private val securePrefs: SecurePrefs
) {
    companion object {
        private const val TAG = "NetworkUtils"
    }

    /**
     * Check if the configured server is reachable.
     * @return true if server responds, false on timeout or error
     */
    suspend fun isServerReachable(): Boolean = withContext(Dispatchers.IO) {
        val serverUrl = securePrefs.getServerUrl() ?: return@withContext false
        try {
            val url = URL("$serverUrl/rest/v1/")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 8000  // Increased timeout
            connection.readTimeout = 8000
            
            // Handle SSL trust for self-signed certs
            if (connection is HttpsURLConnection && securePrefs.getTrustAllCerts()) {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())
                connection.sslSocketFactory = sslContext.socketFactory
                connection.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            }
            
            // Add API key header if available
            securePrefs.getApiKey()?.let { apiKey ->
                connection.setRequestProperty("apikey", apiKey)
            }
            
            // Add auth token to avoid potential 401 issues
            securePrefs.getAccessToken()?.let { token ->
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            
            val responseCode = connection.responseCode
            connection.disconnect()

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Server check response: $responseCode")
            }
            
            // Any response (even 401/403) means server is reachable
            responseCode in 200..599
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Server unreachable: ${e.javaClass.simpleName} - ${e.message}")
            } else {
                Log.w(TAG, "Server unreachable: ${e.javaClass.simpleName}")
            }
            false
        }
    }
}
