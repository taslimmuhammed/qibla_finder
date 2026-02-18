package com.taslim.qiblafinder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailyUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Restart the service to update the persistent notification for the new day
        PrayerTimesNotificationService.startService(context)

        // Schedule exact alarms for today's prayer times
        PrayerAlarmScheduler.scheduleAllPrayerAlarms(context)
    }
}