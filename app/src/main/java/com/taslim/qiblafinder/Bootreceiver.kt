package com.taslim.qiblafinder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Check if user has configured prayer times
            val sharedPreferences = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
            val hasConfigured = sharedPreferences.contains("madhab_preference")

            if (hasConfigured && PrayerSettingsStore.needsPrayerAlarms(context)) {
                // Restart the notification service
                if (PrayerSettingsStore.isNotificationsEnabled(context)) {
                    PrayerTimesNotificationService.startService(context)
                }

                // Reschedule all prayer alarms (they are lost on reboot)
                PrayerAlarmScheduler.scheduleAllPrayerAlarms(context)
            }
        }
    }
}