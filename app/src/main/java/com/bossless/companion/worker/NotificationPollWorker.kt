package com.bossless.companion.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bossless.companion.BuildConfig
import com.bossless.companion.MainActivity
import com.bossless.companion.R
import com.bossless.companion.data.repository.NotificationsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class NotificationPollWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val notificationsRepository: NotificationsRepository
) : CoroutineWorker(appContext, workerParams) {
    
    private val appCtx = appContext

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null && BuildConfig.DEBUG) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            logDebug("Starting notification poll work")
            val result = notificationsRepository.getNotifications()
            if (result.isSuccess) {
                val notifications = result.getOrDefault(emptyList())
                logDebug("Fetched ${notifications.size} notifications")
                val unreadNotifications = notifications.filter { !it.read }
                logDebug("Found ${unreadNotifications.size} unread notifications")
                
                // Show push notifications for unread items
                if (unreadNotifications.isNotEmpty()) {
                    logDebug("Showing notifications")
                    showNotifications(unreadNotifications)
                } else {
                    logDebug("No unread notifications to show")
                }
                
                Result.success()
            } else {
                logError("Failed to fetch notifications", result.exceptionOrNull())
                Result.retry()
            }
        } catch (e: Exception) {
            logError("Worker exception", e)
            Result.failure()
        }
    }
    
    private fun showNotifications(notifications: List<com.bossless.companion.data.models.Notification>) {
        logDebug("showNotifications called with ${notifications.size} notifications")
        createNotificationChannel()
        val notificationManager = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Show summary notification if multiple
        if (notifications.size > 1) {
            logDebug("Showing summary notification for ${notifications.size} notifications")
            val intent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                appCtx,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val summaryNotification = NotificationCompat.Builder(appCtx, NOTIFICATIONS_CHANNEL_ID)
                .setContentTitle("New Notifications")
                .setContentText("You have ${notifications.size} new notifications")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            
            try {
                notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
                logDebug("Summary notification shown")
            } catch (e: SecurityException) {
                logError("Failed to show summary notification", e)
            }
        } else if (notifications.size == 1) {
            // Show individual notification
            val notification = notifications[0]
            logDebug("Showing individual notification")
            val intent = Intent(appCtx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to_notifications", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                appCtx,
                notification.id.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val systemNotification = NotificationCompat.Builder(appCtx, NOTIFICATIONS_CHANNEL_ID)
                .setContentTitle(notification.title ?: "New Notification")
                .setContentText(notification.message?.take(100) ?: "")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            
            try {
                notificationManager.notify(notification.id.hashCode(), systemNotification)
                logDebug("Individual notification shown")
            } catch (e: SecurityException) {
                logError("Failed to show individual notification", e)
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            logDebug("Creating notification channel")
            val channel = NotificationChannel(
                NOTIFICATIONS_CHANNEL_ID,
                "Job Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications about job assignments and updates"
            }
            val notificationManager = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            logDebug("Notification channel created")
        }
    }
    
    companion object {
        private const val TAG = "NotificationPollWorker"
        const val NOTIFICATIONS_CHANNEL_ID = "job_notifications"
        const val SUMMARY_NOTIFICATION_ID = 2
    }
}
