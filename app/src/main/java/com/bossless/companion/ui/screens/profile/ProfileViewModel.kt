package com.bossless.companion.ui.screens.profile

import androidx.lifecycle.ViewModel
import android.content.Context
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.local.ThemeMode
import com.bossless.companion.data.repository.AuthRepository
import com.bossless.companion.service.LocationTrackingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val securePrefs: SecurePrefs,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    
    // Expose theme mode as a separate flow for MainActivity to observe
    private val _themeMode = MutableStateFlow(securePrefs.getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        _uiState.value = ProfileUiState(
            email = securePrefs.getUserEmail() ?: "",
            serverUrl = securePrefs.getServerUrl() ?: "",
            trustAllCerts = securePrefs.getTrustAllCerts(),
            backgroundNotifications = securePrefs.getBackgroundNotifications(),
            locationTrackingEnabled = securePrefs.getLocationTrackingEnabled(),
            locationTrackingBusinessHoursOnly = securePrefs.getLocationTrackingBusinessHoursOnly(),
            businessLogoUrl = securePrefs.getBusinessLogoUrl(),
            themeMode = securePrefs.getThemeMode(),
            lastSyncTimestamp = securePrefs.getLastSyncTimestamp()
        )
    }

    fun toggleTrustAllCerts(enabled: Boolean) {
        securePrefs.setTrustAllCerts(enabled)
        _uiState.value = _uiState.value.copy(trustAllCerts = enabled)
    }

    fun toggleBackgroundNotifications(enabled: Boolean) {
        securePrefs.setBackgroundNotifications(enabled)
        _uiState.value = _uiState.value.copy(backgroundNotifications = enabled)
    }
    
    fun toggleLocationTracking(enabled: Boolean) {
        securePrefs.setLocationTrackingEnabled(enabled)
        _uiState.value = _uiState.value.copy(locationTrackingEnabled = enabled)
        
        // Start or stop location tracking service
        if (enabled) {
            LocationTrackingService.startService(context)
        } else {
            LocationTrackingService.stopService(context)
        }
    }

    fun toggleLocationTrackingBusinessHoursOnly(enabled: Boolean) {
        securePrefs.setLocationTrackingBusinessHoursOnly(enabled)
        _uiState.value = _uiState.value.copy(locationTrackingBusinessHoursOnly = enabled)

        // If tracking is enabled, restart the service to re-evaluate in/out-of-hours immediately
        if (_uiState.value.locationTrackingEnabled) {
            LocationTrackingService.stopService(context)
            LocationTrackingService.startService(context)
        }
    }
    
    fun setThemeMode(mode: ThemeMode) {
        securePrefs.saveThemeMode(mode)
        _uiState.value = _uiState.value.copy(themeMode = mode)
        _themeMode.value = mode
    }

    fun logout() {
        authRepository.logout()
    }
}

data class ProfileUiState(
    val email: String = "",
    val serverUrl: String = "",
    val trustAllCerts: Boolean = false,
    val backgroundNotifications: Boolean = true,
    val locationTrackingEnabled: Boolean = true,
    val locationTrackingBusinessHoursOnly: Boolean = false,
    val businessLogoUrl: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lastSyncTimestamp: Long? = null
)
