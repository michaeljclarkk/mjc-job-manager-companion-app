package com.bossless.companion

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.local.ThemeMode
import com.bossless.companion.data.repository.AuthRepository
import com.bossless.companion.service.TimerService
import com.bossless.companion.service.LocationTrackingService
import com.bossless.companion.ui.navigation.NavGraph
import com.bossless.companion.ui.navigation.Screen
import com.bossless.companion.ui.screens.permissions.PermissionsGateScreen
import com.bossless.companion.ui.theme.BosslessCompanionTheme
import com.bossless.companion.utils.ErrorReporter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d("MainActivity", message)
    }

    @Inject
    lateinit var authRepository: AuthRepository
    
    @Inject
    lateinit var securePrefs: SecurePrefs
    
    // Theme state that can be updated from ProfileScreen
    private var currentThemeMode by mutableStateOf(ThemeMode.SYSTEM)

    private var showPermissionsGate by mutableStateOf(false)

    // Request notification permission launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            logDebug("POST_NOTIFICATIONS permission granted")
            maybeStartLocationTracking()
        } else {
            logDebug("POST_NOTIFICATIONS permission denied")
        }
    }

    // Request background location permission launcher (must be registered once)
    private val backgroundLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        logDebug("Background location permission: $granted")
        showPermissionsGate = shouldShowPermissionsGate()
        if (!showPermissionsGate) {
            maybeStartLocationTracking()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        logDebug("Camera permission: $granted")
        showPermissionsGate = shouldShowPermissionsGate()
    }

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        logDebug("Storage/media permission: $granted")
        showPermissionsGate = shouldShowPermissionsGate()
    }

    // Request location permissions launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            logDebug("Location permission granted")
            maybeStartLocationTracking()

            showPermissionsGate = shouldShowPermissionsGate()
            
            // Request background location permission on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    // Background location requires a separate request after foreground permission
                    backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        } else {
            logDebug("Location permission denied")
            showPermissionsGate = shouldShowPermissionsGate()
        }
    }

    private fun hasAnyLocationPermission(): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isSystemLocationEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            runCatching {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
        }
    }

    private fun shouldShowPermissionsGate(): Boolean {
        if (!securePrefs.getLocationTrackingEnabled()) return false

        // Location tracking is enabled by default; enforce required access.
        if (!hasNotificationPermission()) return true
        if (!hasAnyLocationPermission()) return true
        if (!hasBackgroundLocationPermission()) return true
        if (!hasCameraPermission()) return true
        if (!hasStoragePermission()) return true
        if (!isSystemLocationEnabled()) return true
        return false
    }

    private fun requestNextRequiredAccess() {
        if (!securePrefs.getLocationTrackingEnabled()) return

        if (!hasNotificationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        if (!hasAnyLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        if (!hasBackgroundLocationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            return
        }

        if (!hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        if (!hasStoragePermission()) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            storagePermissionLauncher.launch(permission)
            return
        }

        if (!isSystemLocationEnabled()) {
            openLocationSettings()
            return
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun maybeStartLocationTracking() {
        if (!securePrefs.getLocationTrackingEnabled()) return
        if (!hasAnyLocationPermission()) return
        if (!hasNotificationPermission()) return
        LocationTrackingService.startService(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load saved theme preference
        currentThemeMode = securePrefs.getThemeMode()
        
        // Initialize ErrorReporter for critical error tracking
        securePrefs.getServerUrl()?.let { serverUrl ->
            ErrorReporter.init(serverUrl, securePrefs.getUserId())
        }
        
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Request location permissions and start location tracking if enabled
        if (securePrefs.getLocationTrackingEnabled()) {
            val hasFineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (!hasFineLocation && !hasCoarseLocation) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                // Permission already granted, start service if allowed
                maybeStartLocationTracking()
            }
        }
        
        // Start timer service
        startService(Intent(this, TimerService::class.java))

        showPermissionsGate = shouldShowPermissionsGate()

        setContent {
            BosslessCompanionTheme(themeMode = currentThemeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showPermissionsGate) {
                        PermissionsGateScreen(
                            notificationsGranted = hasNotificationPermission(),
                            foregroundLocationGranted = hasAnyLocationPermission(),
                            backgroundLocationGranted = hasBackgroundLocationPermission(),
                            cameraGranted = hasCameraPermission(),
                            storageGranted = hasStoragePermission(),
                            systemLocationEnabled = isSystemLocationEnabled(),
                            onRequestNext = { requestNextRequiredAccess() },
                            onOpenAppSettings = { openAppSettings() },
                            onOpenLocationSettings = { openLocationSettings() }
                        )
                        return@Surface
                    }

                    val isLoggedIn = !securePrefs.getUserId().isNullOrBlank() &&
                        !securePrefs.getUserEmail().isNullOrBlank() &&
                        !securePrefs.getRefreshToken().isNullOrBlank()

                    val hasPin = securePrefs.hasPin()
                    val requiresPinSetup = isLoggedIn && !hasPin
                    val requiresPinUnlock = isLoggedIn && hasPin &&
                        (securePrefs.isPinUnlockRequired() || securePrefs.isTokenExpired())

                    val startDestination = when {
                        !isLoggedIn -> Screen.Login.route
                        requiresPinSetup -> Screen.PinSetup.route
                        requiresPinUnlock -> Screen.PinUnlock.route
                        else -> Screen.Jobs.route
                    }
                    
                    val navigateToTimer = intent?.getBooleanExtra("navigate_to_timer", false) ?: false
                    val navigateToNotifications = intent?.getBooleanExtra("navigate_to_notifications", false) ?: false
                    val finalStartDestination = when {
                        navigateToTimer && isLoggedIn && !requiresPinSetup && !requiresPinUnlock -> Screen.Timer.route
                        navigateToNotifications && isLoggedIn && !requiresPinSetup && !requiresPinUnlock -> Screen.Notifications.route
                        else -> startDestination
                    }
                    
                    NavGraph(
                        startDestination = finalStartDestination,
                        onThemeChanged = { mode ->
                            currentThemeMode = mode
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        showPermissionsGate = shouldShowPermissionsGate()
        if (!showPermissionsGate) {
            maybeStartLocationTracking()
        }
    }
}
