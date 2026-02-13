package com.example.qiblafinder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if user has configured prayer times
            val sharedPreferences = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
            val hasConfigured = sharedPreferences.contains("madhab_preference")
            
            if (hasConfigured) {
                // Restart the notification service
                PrayerTimesNotificationService.startService(context)
            }
        }
    }
}