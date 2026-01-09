package com.bossless.companion.ui.navigation

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bossless.companion.data.auth.AuthGateEvent
import com.bossless.companion.data.auth.AuthGateEventBus
import com.bossless.companion.data.local.SecurePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SessionGateState(
    val isLoggedIn: Boolean,
    val hasPin: Boolean,
    val tokenExpiresAtEpochSeconds: Long?,
    val pinUnlockRequired: Boolean
) {
    fun isTokenExpired(nowEpochSeconds: Long): Boolean {
        val expiresAt = tokenExpiresAtEpochSeconds ?: return true
        return nowEpochSeconds >= expiresAt
    }

    fun needsPinSetup(): Boolean = isLoggedIn && !hasPin

    fun requiresPinUnlock(nowEpochSeconds: Long): Boolean {
        return isLoggedIn && hasPin && (pinUnlockRequired || isTokenExpired(nowEpochSeconds))
    }
}

@HiltViewModel
class SessionGateViewModel @Inject constructor(
    private val securePrefs: SecurePrefs,
    authGateEventBus: AuthGateEventBus
) : ViewModel() {

    val events: SharedFlow<AuthGateEvent> = authGateEventBus.events

    val state: StateFlow<SessionGateState> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(Unit)
        }

        securePrefs.registerOnChangeListener(listener)
        trySend(Unit)

        awaitClose {
            securePrefs.unregisterOnChangeListener(listener)
        }
    }
        .map {
            val userId = securePrefs.getUserId()
            val email = securePrefs.getUserEmail()
            val refreshToken = securePrefs.getRefreshToken()

            SessionGateState(
                isLoggedIn = !userId.isNullOrBlank() && !email.isNullOrBlank() && !refreshToken.isNullOrBlank(),
                hasPin = securePrefs.hasPin(),
                tokenExpiresAtEpochSeconds = securePrefs.getTokenExpiresAtEpochSeconds(),
                pinUnlockRequired = securePrefs.isPinUnlockRequired()
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionGateState(false, false, null, false))
}
