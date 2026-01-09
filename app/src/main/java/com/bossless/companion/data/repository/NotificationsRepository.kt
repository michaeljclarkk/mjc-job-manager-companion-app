package com.bossless.companion.data.repository

import com.bossless.companion.data.api.ApiService
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.data.local.db.NotificationDao
import com.bossless.companion.data.local.db.toEntity
import com.bossless.companion.data.local.db.toModel
import com.bossless.companion.data.models.Notification
import com.bossless.companion.data.models.UpdateNotificationRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationsRepository @Inject constructor(
    private val apiService: ApiService,
    private val securePrefs: SecurePrefs,
    private val notificationDao: NotificationDao
) {
    fun getNotificationsFlow(): Flow<List<Notification>> =
        notificationDao.getAllNotifications().map { list -> list.map { it.toModel() } }

    suspend fun getNotifications(): Result<List<Notification>> {
        val userId = securePrefs.getUserId() ?: return Result.failure(Exception("User not logged in"))
        return try {
            val response = apiService.getNotifications(userId = "eq.$userId")
            if (response.isSuccessful) {
                val notifications = response.body() ?: emptyList()
                // Update cache
                notificationDao.clearNotifications()
                notificationDao.insertNotifications(notifications.map { it.toEntity() })
                Result.success(notifications)
            } else {
                // On failure, return cached notifications
                Result.failure(Exception("Failed to fetch notifications: ${response.code()}"))
            }
        } catch (e: Exception) {
            // On exception, return cached notifications
            Result.failure(e)
        }
    }

    suspend fun markAsRead(notificationId: String): Result<Unit> {
        return try {
            val response = apiService.markNotificationRead(
                id = "eq.$notificationId",
                request = UpdateNotificationRequest(read = true)
            )
            if (response.isSuccessful) {
                notificationDao.markAsRead(notificationId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark as read: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun markAllAsRead(): Result<Unit> {
        val userId = securePrefs.getUserId() ?: return Result.failure(Exception("User not logged in"))
        return try {
            val response = apiService.markAllNotificationsRead(
                userId = "eq.$userId",
                request = UpdateNotificationRequest(read = true)
            )
            if (response.isSuccessful) {
                notificationDao.markAllAsRead()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to mark all as read: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteNotification(notificationId: String): Result<Unit> {
        return try {
            val response = apiService.deleteNotification(id = "eq.$notificationId")
            if (response.isSuccessful) {
                notificationDao.deleteNotificationById(notificationId)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete notification: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteAllNotifications(): Result<Unit> {
        val userId = securePrefs.getUserId() ?: return Result.failure(Exception("User not logged in"))
        return try {
            val response = apiService.deleteAllNotifications(userId = "eq.$userId")
            if (response.isSuccessful) {
                notificationDao.clearNotifications()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete all notifications: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
