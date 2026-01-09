package com.bossless.companion.data.repository

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.local.db.PendingUserLocationDao
import com.bossless.companion.data.local.db.PendingUserLocationEntity
import com.bossless.companion.data.models.CreateUserLocationRequest
import com.bossless.companion.data.models.UserLocation
import com.bossless.companion.utils.ErrorReporter
import javax.inject.Inject
import javax.inject.Singleton
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

@Singleton
class LocationRepository @Inject constructor(
    private val apiService: ApiService,
    private val securePrefs: SecurePrefs,
    private val pendingUserLocationDao: PendingUserLocationDao
) {

    class TransientLocationSendException(cause: Throwable) : Exception(cause.message, cause)
    class AuthLocationSendException(message: String) : Exception(message)

    companion object {
        private const val MAX_BUFFERED_LOCATIONS = 500
        private const val FLUSH_BATCH_SIZE = 25
        private const val ERROR_EMAIL_THROTTLE_MS = 30 * 60 * 1000L // 30 minutes
    }

    private fun isTransientNetworkFailure(error: Throwable): Boolean {
        // Unwrap common wrappers
        val chain = generateSequence(error) { it.cause }.take(8).toList()
        val all = chain + error.suppressed

        // SSL issues are usually configuration/cert problems, not "tunnel" failures
        if (all.any { it is SSLHandshakeException || it is SSLPeerUnverifiedException }) return false

        return all.any {
            it is SocketTimeoutException ||
                it is UnknownHostException ||
                it is ConnectException ||
                it is SocketException ||
                it is InterruptedIOException
        }
    }

    private fun isTransientHttpStatus(code: Int): Boolean {
        // Don't email for transient/gateway/backoff type responses
        return code == 408 || code == 429 || code == 502 || code == 503 || code == 504
    }

    suspend fun sendLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float? = null,
        speed: Float? = null,
        heading: Float? = null,
        altitude: Float? = null,
        distanceDeltaMeters: Float = 0f,
        recordedAt: String
    ): Result<UserLocation> {
        val userId = securePrefs.getUserId()
            ?: return Result.failure(Exception("User not logged in"))

        val hasApiKey = !securePrefs.getApiKey().isNullOrBlank()
        val hasAccessToken = !securePrefs.getAccessToken().isNullOrBlank()
        val hasRefreshToken = !securePrefs.getRefreshToken().isNullOrBlank()
        val serverUrl = securePrefs.getServerUrl()
        
        return try {
            val request = CreateUserLocationRequest(
                user_id = userId,
                latitude = latitude,
                longitude = longitude,
                accuracy = accuracy,
                speed = speed,
                heading = heading,
                altitude = altitude,
                distance_delta_meters = distanceDeltaMeters,
                recorded_at = recordedAt
            )
            
            val response = apiService.createUserLocation(request = request)
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                Result.success(response.body()!!.first())
            } else {
                if (response.code() == 401 || response.code() == 403) {
                    // Background services can easily hit expired tokens; do not email-spam for auth failures.
                    return Result.failure(AuthLocationSendException("Auth failed HTTP ${response.code()}"))
                }
                if (isTransientHttpStatus(response.code())) {
                    return Result.failure(TransientLocationSendException(Exception("Transient HTTP ${response.code()}")))
                }

                val errorBodyPreview = try {
                    response.errorBody()?.string()?.take(1000)
                } catch (_: Exception) {
                    null
                }

                val error = Exception("Failed to send location: ${response.code()}")
                val signature = "http_${response.code()}_${(errorBodyPreview ?: "").take(64)}"
                if (securePrefs.shouldSendLocationErrorReport(signature, ERROR_EMAIL_THROTTLE_MS)) {
                    ErrorReporter.logAndEmailError(
                        context = "LocationRepository.sendLocation",
                        error = error,
                        userId = userId,
                        additionalInfo = mapOf(
                            "lat" to latitude.toString(),
                            "lng" to longitude.toString(),
                            "httpStatus" to response.code().toString(),
                            "errorBody" to (errorBodyPreview ?: "<empty>"),
                            "hasApiKey" to hasApiKey.toString(),
                            "hasAccessToken" to hasAccessToken.toString(),
                            "hasRefreshToken" to hasRefreshToken.toString(),
                            "serverUrl" to (serverUrl ?: "<null>")
                        )
                    )
                }
                Result.failure(error)
            }
        } catch (e: Exception) {
            if (isTransientNetworkFailure(e)) {
                return Result.failure(TransientLocationSendException(e))
            }
            val signature = "ex_${e.javaClass.simpleName}_${(e.message ?: "").take(64)}"
            if (securePrefs.shouldSendLocationErrorReport(signature, ERROR_EMAIL_THROTTLE_MS)) {
                ErrorReporter.logAndEmailError(
                    context = "LocationRepository.sendLocation",
                    error = e,
                    userId = userId,
                    additionalInfo = mapOf(
                        "lat" to latitude.toString(),
                        "lng" to longitude.toString(),
                        "hasApiKey" to hasApiKey.toString(),
                        "hasAccessToken" to hasAccessToken.toString(),
                        "hasRefreshToken" to hasRefreshToken.toString(),
                        "serverUrl" to (serverUrl ?: "<null>")
                    )
                )
            }
            Result.failure(e)
        }
    }

    suspend fun bufferLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float? = null,
        speed: Float? = null,
        heading: Float? = null,
        altitude: Float? = null,
        distanceDeltaMeters: Float = 0f,
        recordedAt: String
    ): Result<Long> {
        val userId = securePrefs.getUserId()
            ?: return Result.failure(Exception("User not logged in"))

        val entity = PendingUserLocationEntity(
            user_id = userId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            speed = speed,
            heading = heading,
            altitude = altitude,
            distance_delta_meters = distanceDeltaMeters,
            recorded_at = recordedAt,
            created_at_epoch_ms = System.currentTimeMillis()
        )

        return try {
            val id = pendingUserLocationDao.insert(entity)
            pendingUserLocationDao.trimToMaxRows(MAX_BUFFERED_LOCATIONS)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun flushBufferedLocations(maxBatchSize: Int = FLUSH_BATCH_SIZE): Result<Int> {
        val currentUserId = securePrefs.getUserId()
            ?: return Result.failure(Exception("User not logged in"))

        val pending = try {
            pendingUserLocationDao.getOldest(maxBatchSize)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        if (pending.isEmpty()) return Result.success(0)

        val successfullySentIds = mutableListOf<Long>()
        for (item in pending) {
            if (item.user_id != currentUserId) {
                // Safety: don't send location points that belong to a different session/user.
                // This can happen if the user logs out/in before the buffer is drained.
                try {
                    pendingUserLocationDao.deleteByIds(listOf(item.id))
                } catch (_: Exception) {
                    // Best-effort
                }
                continue
            }

            val sendResult = sendLocation(
                latitude = item.latitude,
                longitude = item.longitude,
                accuracy = item.accuracy,
                speed = item.speed,
                heading = item.heading,
                altitude = item.altitude,
                distanceDeltaMeters = item.distance_delta_meters,
                recordedAt = item.recorded_at
            )

            if (sendResult.isSuccess) {
                successfullySentIds.add(item.id)
                continue
            }

            val error = sendResult.exceptionOrNull()
            try {
                pendingUserLocationDao.markAttempt(
                    id = item.id,
                    attemptedAtEpochMs = System.currentTimeMillis(),
                    lastError = error?.message
                )
            } catch (_: Exception) {
                // Best-effort; do not crash background service.
            }

            // Stop flushing on first failure to avoid tight loops / battery burn.
            // - Auth failures require user action (PIN/unlock/login)
            // - Transient failures should be retried later
            return when (error) {
                is AuthLocationSendException -> Result.failure(error)
                is TransientLocationSendException -> Result.failure(error)
                else -> Result.failure(error ?: Exception("Failed to flush location"))
            }
        }

        if (successfullySentIds.isNotEmpty()) {
            try {
                pendingUserLocationDao.deleteByIds(successfullySentIds)
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }
        return Result.success(successfullySentIds.size)
    }
}
