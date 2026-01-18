package com.bossless.companion.utils

import android.os.Build
import android.util.Log
import com.bossless.companion.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility for logging errors and sending error reports to GitHub via edge function.
 * 
 * Usage:
 * ```
 * // With exception
 * ErrorReporter.logAndReportError(
 *     context = "PurchaseOrderRepository.createPO",
 *     error = exception,
 *     userId = securePrefs.getUserId()
 * )
 * 
 * // With message only
 * ErrorReporter.logAndReportError(
 *     context = "API.unexpectedResponse",
 *     message = "Server returned 500",
 *     userId = securePrefs.getUserId()
 * )
 * ```
 */
object ErrorReporter {
    
    private const val TAG = "ErrorReporter"
    private const val APP_VERSION = "1.0"
    
    // This will be set from SecurePrefs when the app initializes
    private var serverUrl: String? = null
    private var currentUserId: String? = null
    
    /**
     * Initialize the error reporter with server URL.
     * Call this from MainActivity after login or when server URL is available.
     */
    fun init(serverUrl: String, userId: String? = null) {
        this.serverUrl = serverUrl.trimEnd('/')
        this.currentUserId = userId
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "ErrorReporter initialized")
        }
    }
    
    /**
     * Update the current user ID (call after login/logout)
     */
    fun setUserId(userId: String?) {
        this.currentUserId = userId
    }
    
    /**
     * Log an error and send a report to GitHub.
     * 
     * @param context Where the error occurred (e.g., "JobsRepository.fetchJobs")
     * @param error The exception that was caught
     * @param userId Optional user ID (uses cached value if not provided)
     * @param additionalInfo Optional extra context as key-value pairs
     */
    fun logAndReportError(
        context: String,
        error: Throwable,
        userId: String? = null,
        additionalInfo: Map<String, String> = emptyMap()
    ) {
        val message = error.message ?: error.javaClass.simpleName
        val stackTrace = error.stackTraceToString()
        
        // Always log locally first
        Log.e(TAG, "[$context] $message", error)
        
        // Send report asynchronously
        sendErrorReport(
            context = context,
            message = message,
            stackTrace = stackTrace,
            userId = userId ?: currentUserId,
            additionalInfo = additionalInfo
        )
    }
    
    /**
     * Log a message-based error (no exception) and send a report.
     * 
     * @param context Where the error occurred
     * @param message Description of the error
     * @param userId Optional user ID
     * @param additionalInfo Optional extra context
     */
    fun logAndReportError(
        context: String,
        message: String,
        userId: String? = null,
        additionalInfo: Map<String, String> = emptyMap()
    ) {
        // Always log locally first
        Log.e(TAG, "[$context] $message")
        
        // Send report asynchronously
        sendErrorReport(
            context = context,
            message = message,
            stackTrace = null,
            userId = userId ?: currentUserId,
            additionalInfo = additionalInfo
        )
    }
    
    private fun sendErrorReport(
        context: String,
        message: String,
        stackTrace: String?,
        userId: String?,
        additionalInfo: Map<String, String>
    ) {
        val url = serverUrl ?: run {
            Log.w(TAG, "ErrorReporter not initialized - cannot send report")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    .format(Date())
                
                val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
                
                val json = JSONObject().apply {
                    put("source", "companion-app")
                    put("context", context)
                    put("message", message)
                    put("timestamp", timestamp)
                    put("deviceInfo", deviceInfo)
                    put("appVersion", APP_VERSION)
                    
                    if (stackTrace != null) {
                        put("stackTrace", stackTrace)
                    }
                    if (userId != null) {
                        put("userId", userId)
                    }
                    if (additionalInfo.isNotEmpty()) {
                        put("additionalInfo", JSONObject(additionalInfo as Map<*, *>))
                    }
                }
                
                val endpoint = URL("$url/functions/v1/send-error-report")
                val connection = endpoint.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 10000
                    readTimeout = 10000
                    doOutput = true
                }
                
                connection.outputStream.use { 
                    it.write(json.toString().toByteArray(Charsets.UTF_8)) 
                }
                
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Error report sent successfully for: $context")
                    }
                } else {
                    Log.w(TAG, "Error report failed with code: $responseCode")
                }
                
                connection.disconnect()
                
            } catch (e: Exception) {
                // Don't let error reporting cause more errors
                Log.w(TAG, "Failed to send error report: ${e.message}")
            }
        }
    }
}
