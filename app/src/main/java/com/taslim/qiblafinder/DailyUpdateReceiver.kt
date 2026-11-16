package com.taslim.qiblafinder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailyUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Both the times notification and every reminder are turned off
        if (!PrayerSettingsStore.needsPrayerAlarms(context)) return

        // Refresh the notification for the new day and set the day's alarms again
        PrayerTimesNotification.update(context)
        PrayerAlarmScheduler.scheduleAllPrayerAlarms(context)
    }
}
