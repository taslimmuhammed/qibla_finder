package com.taslim.qiblafinder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.batoulapps.adhan.Prayer

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val EXTRA_PRAYER_TIME = "prayer_time"
        const val EXTRA_PRAYER_ENDS = "prayer_ends"

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

        // Both the times notification and every reminder are turned off
        if (!PrayerSettingsStore.needsPrayerAlarms(context)) return

        val prayer = runCatching { Prayer.valueOf(prayerName) }.getOrNull() ?: return
        PrayerReminders.notifyIfEnabled(
            context,
            prayer,
            scheduledAt = intent.getLongExtra(EXTRA_PRAYER_TIME, 0L),
            endsAt = intent.getLongExtra(EXTRA_PRAYER_ENDS, 0L)
        )

        // Move the highlight on to the next prayer, re-posting the notification if it was dismissed
        PrayerTimesNotification.update(context)

        // Set this prayer's alarm for tomorrow, and refresh the others
        PrayerAlarmScheduler.scheduleAllPrayerAlarms(context)
    }
}
