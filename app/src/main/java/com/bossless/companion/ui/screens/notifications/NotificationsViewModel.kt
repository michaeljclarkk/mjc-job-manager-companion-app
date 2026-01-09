package com.bossless.companion.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.models.Notification
import com.bossless.companion.data.repository.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val notificationsRepository: NotificationsRepository,
    private val securePrefs: SecurePrefs
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState(
        businessLogoUrl = securePrefs.getBusinessLogoUrl()
    ))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    private var lastUpdated: Long? = null

    init {
        observeNotifications()
        loadNotifications()
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            notificationsRepository.getNotificationsFlow().collectLatest { notifications ->
                _uiState.value = _uiState.value.copy(notifications = notifications)
            }
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = notificationsRepository.getNotifications()
            if (result.isSuccess) {
                lastUpdated = Instant.now().epochSecond
                _uiState.value = _uiState.value.copy(
                    notifications = result.getOrDefault(emptyList()),
                    isLoading = false,
                    lastUpdated = lastUpdated
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, lastUpdated = lastUpdated)
            }
        }
    }

    fun markAsRead(notification: Notification) {
        if (notification.read) return
        
        viewModelScope.launch {
            val result = notificationsRepository.markAsRead(notification.id)
            if (result.isSuccess) {
                // Update local list
                val updatedList = _uiState.value.notifications.map {
                    if (it.id == notification.id) it.copy(read = true) else it
                }
                _uiState.value = _uiState.value.copy(notifications = updatedList)
            }
        }
    }
    
    fun markAllAsRead() {
        viewModelScope.launch {
            val result = notificationsRepository.markAllAsRead()
            if (result.isSuccess) {
                val updatedList = _uiState.value.notifications.map { it.copy(read = true) }
                _uiState.value = _uiState.value.copy(notifications = updatedList)
            }
        }
    }
    
    fun deleteNotification(notification: Notification) {
        viewModelScope.launch {
            val result = notificationsRepository.deleteNotification(notification.id)
            if (result.isSuccess) {
                val updatedList = _uiState.value.notifications.filter { it.id != notification.id }
                _uiState.value = _uiState.value.copy(notifications = updatedList)
            }
        }
    }
    
    fun deleteAllNotifications() {
        viewModelScope.launch {
            val result = notificationsRepository.deleteAllNotifications()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(notifications = emptyList())
            }
        }
    }
    
    fun getJobIdFromNotification(notification: Notification): String? {
        return if (notification.reference_type == "job" && notification.reference_id != null) {
            notification.reference_id
        } else {
            null
        }
    }
}

data class NotificationsUiState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = false,
    val lastUpdated: Long? = null,
    val businessLogoUrl: String? = null
)
