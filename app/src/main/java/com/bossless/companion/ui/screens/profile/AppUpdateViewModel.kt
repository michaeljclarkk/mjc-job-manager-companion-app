package com.bossless.companion.ui.screens.profile

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bossless.companion.BuildConfig
import com.bossless.companion.data.models.AppUpdateManifest
import com.bossless.companion.data.repository.AppUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    application: Application,
    private val repository: AppUpdateRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        AppUpdateUiState(
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE,
            updateManifestUrl = BuildConfig.UPDATE_MANIFEST_URL.trim()
        )
    )
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppUpdateEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun loadCachedState() {
        val cached = repository.getCachedAvailableUpdate()
        _uiState.value = _uiState.value.copy(
            lastCheckedAtMs = repository.getLastUpdateCheckAtMs().takeIf { it > 0 },
            availableVersionCode = cached?.versionCode,
            availableVersionName = cached?.versionName,
            availableApkUrl = cached?.apkUrl
        )
    }

    fun checkForUpdatesIfDue(nowMs: Long = System.currentTimeMillis()) {
        val manifestUrl = _uiState.value.updateManifestUrl
        if (manifestUrl.isBlank()) return

        val lastChecked = repository.getLastUpdateCheckAtMs()
        val due = lastChecked <= 0L || (nowMs - lastChecked) >= CHECK_INTERVAL_MS
        if (!due) return

        checkForUpdates()
    }

    fun checkForUpdates() {
        val manifestUrl = _uiState.value.updateManifestUrl
        if (manifestUrl.isBlank()) return

        _uiState.value = _uiState.value.copy(isChecking = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val manifest = repository.fetchManifest(manifestUrl)
                repository.setLastUpdateCheckAtMs(System.currentTimeMillis())

                val isNewer = manifest.versionCode > BuildConfig.VERSION_CODE
                if (isNewer) {
                    repository.cacheAvailableUpdate(
                        AppUpdateRepository.CachedAvailableUpdate(
                            versionCode = manifest.versionCode,
                            versionName = manifest.versionName,
                            apkUrl = manifest.apkUrl
                        )
                    )
                    _uiState.value = _uiState.value.copy(
                        isChecking = false,
                        lastCheckedAtMs = repository.getLastUpdateCheckAtMs().takeIf { it > 0 },
                        availableVersionCode = manifest.versionCode,
                        availableVersionName = manifest.versionName,
                        availableApkUrl = manifest.apkUrl
                    )
                } else {
                    repository.cacheAvailableUpdate(null)
                    _uiState.value = _uiState.value.copy(
                        isChecking = false,
                        lastCheckedAtMs = repository.getLastUpdateCheckAtMs().takeIf { it > 0 },
                        availableVersionCode = null,
                        availableVersionName = null,
                        availableApkUrl = null
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    errorMessage = e.message ?: "Failed to check for updates"
                )
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val apkUrl = _uiState.value.availableApkUrl ?: return

        _uiState.value = _uiState.value.copy(
            isDownloading = true,
            downloadProgress = 0f,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val destination = File(context.cacheDir, "updates/mjc-jm-latest.apk")

                var lastEmitAt = 0L
                repository.downloadApk(
                    apkUrl = apkUrl,
                    destinationFile = destination,
                    expectedSha256Hex = null,
                    onProgress = { downloaded, total ->
                        val progress = if (total != null && total > 0) {
                            downloaded.toFloat() / total.toFloat()
                        } else {
                            null
                        }

                        val now = SystemClock.elapsedRealtime()
                        if (now - lastEmitAt >= PROGRESS_THROTTLE_MS) {
                            lastEmitAt = now
                            if (progress != null) {
                                _uiState.value = _uiState.value.copy(downloadProgress = progress)
                            }
                        }
                    }
                )

                _uiState.value = _uiState.value.copy(isDownloading = false, downloadProgress = 1f)
                _events.tryEmit(AppUpdateEvent.LaunchInstaller(destination))
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDownloading = false,
                    errorMessage = e.message ?: "Failed to download update"
                )
            }
        }
    }

    data class AppUpdateUiState(
        val currentVersionName: String,
        val currentVersionCode: Int,
        val updateManifestUrl: String,

        val lastCheckedAtMs: Long? = null,
        val isChecking: Boolean = false,

        val availableVersionCode: Int? = null,
        val availableVersionName: String? = null,
        val availableApkUrl: String? = null,

        val isDownloading: Boolean = false,
        val downloadProgress: Float = 0f,

        val errorMessage: String? = null
    ) {
        val isUpdateConfigured: Boolean get() = updateManifestUrl.isNotBlank()

        val isUpdateAvailable: Boolean get() {
            val available = availableVersionCode ?: return false
            return available > currentVersionCode
        }
    }

    sealed interface AppUpdateEvent {
        data class LaunchInstaller(val apkFile: File) : AppUpdateEvent
    }

    private companion object {
        // "Occasionally" — keep this conservative so Settings open doesn’t spam the server.
        private const val CHECK_INTERVAL_MS: Long = 24L * 60L * 60L * 1000L
        private const val PROGRESS_THROTTLE_MS: Long = 200L
    }
}
