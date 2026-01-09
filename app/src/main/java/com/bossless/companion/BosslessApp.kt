package com.bossless.companion

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import com.bossless.companion.data.api.UnsafeOkHttpClient
import com.bossless.companion.data.local.SecurePrefs
import com.bossless.companion.worker.NotificationPollWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class BosslessApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var securePrefs: SecurePrefs

    override fun onCreate() {
        super.onCreate()
        
        // Configure Coil to use unsafe OkHttpClient for self-signed certificates
        if (securePrefs.getTrustAllCerts()) {
            val httpClient = UnsafeOkHttpClient.getUnsafeOkHttpClient().build()
            val imageLoader = ImageLoader.Builder(this)
                .okHttpClient(httpClient)
                .build()
            Coil.setImageLoader(imageLoader)
        }
        
        // Schedule notification polling worker
        scheduleNotificationPolling()
    }

    private fun logDebug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }
    
    private fun scheduleNotificationPolling() {
        logDebug("Scheduling notification polling worker")
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        
        val notificationPollRequest = PeriodicWorkRequestBuilder<NotificationPollWorker>(
            15, TimeUnit.MINUTES // Poll every 15 minutes (background work is unreliable on Android)
        )
            .setConstraints(constraints)
            .build()
        
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "notification_polling",
            ExistingPeriodicWorkPolicy.KEEP,
            notificationPollRequest
        )
        logDebug("Notification polling worker scheduled with 15-minute interval")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    companion object {
        private const val TAG = "BosslessApp"
    }
}
