package com.taslim.qiblafinder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailyUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Restart the service to update prayer times for the new day
        PrayerTimesNotificationService.startService(context)
    }
}