package com.bossless.companion.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.app.Notification
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.bossless.companion.BuildConfig
import com.bossless.companion.MainActivity
import com.bossless.companion.R
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.repository.BusinessProfileRepository
import com.bossless.companion.data.repository.LocationRepository
import com.bossless.companion.utils.BusinessHours
import com.bossless.companion.utils.BusinessHoursUtils
import com.bossless.companion.utils.ErrorReporter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var securePrefs: SecurePrefs

    @Inject
    lateinit var businessProfileRepository: BusinessProfileRepository

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var notificationManager: NotificationManager
    
    private var lastLocation: Location? = null
    private var lastRecordedTimeMs: Long = 0L  // ADD THIS
    private var locationCallback: LocationCallback? = null

    @Volatile
    private var businessHoursOnlyEnabled: Boolean = false

    @Volatile
    private var cachedBusinessHours: BusinessHours? = null

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Check if location tracking is enabled in preferences
                val isEnabled = securePrefs.getBoolean(PREF_LOCATION_TRACKING_ENABLED, true)
                if (!isEnabled) {
                    logDebug("Location tracking disabled in preferences")
                    stopSelf()
                    return START_NOT_STICKY
                }

                // On Android 13+, a foreground service notification requires POST_NOTIFICATIONS.
                // If we can't post it, we must not start via startForegroundService (would crash).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hasNotifPermission = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!hasNotifPermission) {
                        android.util.Log.e(TAG, "Cannot start location tracking without POST_NOTIFICATIONS permission")
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }

                businessHoursOnlyEnabled = securePrefs.getLocationTrackingBusinessHoursOnly()

                if (hasLocationPermission()) {
                    startForegroundService()

                    if (businessHoursOnlyEnabled) {
                        // Fetch business hours once on start, then decide whether to run
                        serviceScope.launch {
                            val profileResult = businessProfileRepository.fetchAndCacheBusinessProfile()
                            val profile = profileResult.getOrNull()
                            cachedBusinessHours = BusinessHoursUtils.decode(profile?.business_hours)

                            val withinHours = BusinessHoursUtils.isWithinBusinessHours(
                                cachedBusinessHours,
                                ZonedDateTime.now()
                            )

                            if (!withinHours) {
                                logDebug("Outside business hours - stopping tracking")
                                stopDueToOutOfHours()
                            } else {
                                startLocationUpdates()
                            }
                        }
                    } else {
                        startLocationUpdates()
                    }
                } else {
                    android.util.Log.e(TAG, "Location permission not granted")
                    stopSelf()
                }
            }
            ACTION_RESTORE_NOTIFICATION -> {
                val isEnabled = securePrefs.getBoolean(PREF_LOCATION_TRACKING_ENABLED, true)
                if (!isEnabled) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (!hasLocationPermission()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hasNotifPermission = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasNotifPermission) {
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }

                // Re-post the foreground notification (service may already be running)
                startForegroundService()
            }
            ACTION_STOP -> {
                stopLocationUpdates()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracks your location for job management"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dismissIntent = Intent(this, LocationTrackingNotificationDismissedReceiver::class.java)
        val dismissPendingIntent = PendingIntent.getBroadcast(
            this,
            NOTIFICATION_ID,
            dismissIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Location Tracking Active")
            .setContentText("Your location is being shared")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setDeleteIntent(dismissPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Enforce non-dismissible notification while tracking.
        notification.flags = notification.flags or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            logDebug("Foreground service started")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            ErrorReporter.logAndReportError(
                context = "LocationTrackingService.startForegroundService",
                error = e,
                userId = securePrefs.getUserId()
            )

            // If we were started as a foreground service and couldn't enter the foreground,
            // continuing would trigger a system kill/crash.
            stopSelf()
        }
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            android.util.Log.e(TAG, "Cannot start location updates without permission")
            return
        }

        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val priority = if (hasFine) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            // Coarse-only permission: allow network/Wi‑Fi based fixes (important for Wi‑Fi tablets)
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val locationRequest = LocationRequest.Builder(
            priority,
            LOCATION_UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(LOCATION_UPDATE_INTERVAL_MS)
            setWaitForAccurateLocation(false)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    if (businessHoursOnlyEnabled) {
                        val withinHours = BusinessHoursUtils.isWithinBusinessHours(
                            cachedBusinessHours,
                            ZonedDateTime.now()
                        )
                        if (!withinHours) {
                            logDebug("Outside business hours - stopping tracking")
                            stopDueToOutOfHours()
                            return
                        }
                    }
                    handleNewLocation(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            logDebug("Location updates started")
        } catch (e: SecurityException) {
            android.util.Log.e(TAG, "Location permission missing: ${e.message}")
            ErrorReporter.logAndReportError(
                context = "LocationTrackingService.startLocationUpdates",
                error = e,
                userId = securePrefs.getUserId()
            )
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            logDebug("Location updates stopped")
        }
    }

    private fun stopDueToOutOfHours() {
        stopLocationUpdates()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Ignore
        }
        stopSelf()
    }

    private fun handleNewLocation(location: Location) {
        // Throttle: ignore if less than 25 seconds since last recorded location
        val now = System.currentTimeMillis()
        val MIN_INTERVAL_MS = 25_000L
        if (now - lastRecordedTimeMs < MIN_INTERVAL_MS) {
            logDebug("Throttling: only ${(now - lastRecordedTimeMs) / 1000}s since last ping")
            return
        }

        // Reject inaccurate locations (> 50m accuracy is unreliable)
        val MAX_ACCURACY_METERS = 50f
        if (location.accuracy > MAX_ACCURACY_METERS) {
            logDebug("Ignoring inaccurate location: ${location.accuracy}m accuracy")
            return
        }

        var distanceDelta = 0f
        lastLocation?.let { prevLocation ->
            val rawDistance = prevLocation.distanceTo(location)
            val timeDeltaSeconds = (location.time - prevLocation.time) / 1000f
            
            // Reject impossible movements (> 50 m/s = 180 km/h)
            if (timeDeltaSeconds > 0) {
                val impliedSpeedMps = rawDistance / timeDeltaSeconds
                if (impliedSpeedMps <= 50f) {
                    distanceDelta = rawDistance
                } else {
                    logDebug("Ignoring GPS jump: ${impliedSpeedMps}m/s implied speed")
                }
            }
        }

        lastLocation = location
        lastRecordedTimeMs = now  // UPDATE THIS

        // Send location to server
        serviceScope.launch {
            val recordedAt = Instant.now().toString()
            val buffered = locationRepository.bufferLocation(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                speed = if (location.hasSpeed()) location.speed else null,
                heading = if (location.hasBearing()) location.bearing else null,
                altitude = if (location.hasAltitude()) location.altitude.toFloat() else null,
                distanceDeltaMeters = distanceDelta,
                recordedAt = recordedAt
            )

            if (buffered.isFailure) {
                android.util.Log.w(TAG, "Failed to buffer location locally")
                // Intentionally no ErrorReporter here (avoid spam). If DB is broken, other parts of app will show it.
                return@launch
            }

            val result = locationRepository.flushBufferedLocations()

            if (result.isSuccess) {
                val flushed = result.getOrNull() ?: 0
                if (flushed > 0) {
                    logDebug("Flushed $flushed buffered location(s)")
                }
            } else {
                val error = result.exceptionOrNull()
                when (error) {
                    is LocationRepository.TransientLocationSendException -> {
                        // Expected during tunnels/poor reception; keep logs quiet.
                        logDebug("Location send skipped (transient network issue)")
                    }
                    is LocationRepository.AuthLocationSendException -> {
                        // Expected if token expired/rejected; will resume after user unlock/login.
                        logDebug("Location flush paused (auth required)")
                    }
                    else -> {
                        android.util.Log.w(TAG, "Failed to flush location: ${error?.message}")
                    }
                }
                // Intentionally do not call ErrorReporter here to avoid duplicate reports.
                // LocationRepository.sendLocation decides which failures are report-worthy.
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        serviceJob.cancel()
        logDebug("Service destroyed")
    }

    companion object {
        private const val TAG = "LocationTrackingService"
        const val NOTIFICATION_ID = 2
        const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
        const val LOCATION_UPDATE_INTERVAL_MS = 30_000L // 30 seconds

        const val ACTION_START = "ACTION_START_LOCATION_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_LOCATION_TRACKING"
        const val ACTION_RESTORE_NOTIFICATION = "ACTION_RESTORE_LOCATION_TRACKING_NOTIFICATION"
        
        const val PREF_LOCATION_TRACKING_ENABLED = "location_tracking_enabled"

        fun startService(context: Context) {
            // Avoid starting a foreground service we can't present (Android 13+).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNotifPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasNotifPermission) {
                    android.util.Log.w(TAG, "Not starting LocationTrackingService: POST_NOTIFICATIONS not granted")
                    return
                }
            }

            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
