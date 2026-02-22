package com.taslim.qiblafinder

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.batoulapps.adhan.Prayer

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PRAYER_NAME = "prayer_name"

        // Request codes for each prayer alarm (must be unique)
        const val RC_FAJR = 100
        const val RC_DHUHR = 101
        const val RC_ASR = 102
        const val RC_MAGHRIB = 103
        const val RC_ISHA = 104

        fun getRequestCode(prayer: Prayer): Int {
            return when (prayer) {
                Prayer.FAJR -> RC_FAJR
                Prayer.DHUHR -> RC_DHUHR
                Prayer.ASR -> RC_ASR
                Prayer.MAGHRIB -> RC_MAGHRIB
                Prayer.ISHA -> RC_ISHA
                else -> -1
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return

        // Don't process SUNRISE
        if (prayerName == "SUNRISE") return

        // Ensure the persistent notification service is running and update its notification.
        // This will re-post the notification if the user dismissed it, and refresh the
        // highlighted prayer to reflect the current time.
        PrayerTimesNotificationService.startService(context)

        // Reschedule alarms for remaining prayers today + tomorrow's alarms
        PrayerAlarmScheduler.scheduleAllPrayerAlarms(context)
    }
}
