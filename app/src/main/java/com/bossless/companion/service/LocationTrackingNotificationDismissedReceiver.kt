package com.bossless.companion.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LocationTrackingNotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // If the user dismisses the foreground notification while tracking is enabled,
        // immediately restore it by pinging the running service (or starting it if needed).
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_RESTORE_NOTIFICATION
        }
        context.startService(serviceIntent)
    }
}
