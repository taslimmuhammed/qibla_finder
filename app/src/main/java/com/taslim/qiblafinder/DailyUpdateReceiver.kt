package com.taslim.qiblafinder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailyUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Both the times notification and every reminder are turned off
        if (!PrayerSettingsStore.needsPrayerAlarms(context)) return

        // Restart the service to update the persistent notification for the new day
        if (PrayerSettingsStore.isNotificationsEnabled(context)) {
            runCatching { PrayerTimesNotificationService.startService(context) }
        }

        // Schedule exact alarms for today's prayer times
        PrayerAlarmScheduler.scheduleAllPrayerAlarms(context)
    }
}