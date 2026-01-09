package com.bossless.companion.data.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthGateEvent {
    data object RequirePinUnlock : AuthGateEvent
}

@Singleton
class AuthGateEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AuthGateEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AuthGateEvent> = _events

    fun tryEmit(event: AuthGateEvent) {
        _events.tryEmit(event)
    }
}
