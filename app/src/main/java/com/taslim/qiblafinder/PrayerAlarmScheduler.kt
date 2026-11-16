package com.taslim.qiblafinder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.Prayer
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.util.Calendar
import java.util.Date

/**
 * Keeps one alarm per prayer, always set for its next occurrence, plus a daily refresh just
 * after midnight. Each alarm reposts the times notification and fires any reminder, then
 * schedules the rest again, so the chain never depends on a single alarm arriving.
 *
 * Only prayers with a reminder wake the phone. The others just refresh the notification,
 * which can wait until the phone is next awake, so with reminders off the app never wakes it.
 */
object PrayerAlarmScheduler {

    private const val TAG = "PrayerAlarmScheduler"

    private val ALERT_PRAYERS = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

    /** Schedules the alarms when the times notification or a reminder needs them, and clears them otherwise. */
    fun sync(context: Context) {
        if (PrayerSettingsStore.needsPrayerAlarms(context)) scheduleAllPrayerAlarms(context) else cancelAll(context)
    }

    fun scheduleAllPrayerAlarms(context: Context) {
        val locationPrefs = context.getSharedPreferences("location_data", Context.MODE_PRIVATE)
        val latitude = locationPrefs.getFloat("latitude", 0f).toDouble()
        val longitude = locationPrefs.getFloat("longitude", 0f).toDouble()
        if (latitude == 0.0 && longitude == 0.0) {
            Log.w(TAG, "No location data available, cannot schedule prayer alarms")
            return
        }

        val sharedPreferences = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
        val madhab = runCatching {
            Madhab.valueOf(sharedPreferences.getString("madhab_preference", null) ?: Madhab.SHAFI.name)
        }.getOrDefault(Madhab.SHAFI)
        val methodName = sharedPreferences.getString("method_preference", "MUSLIM_WORLD_LEAGUE") ?: "MUSLIM_WORLD_LEAGUE"
        val params = CalculationOption.fromKey(methodName).method.parameters
        params.madhab = madhab

        val coordinates = Coordinates(latitude, longitude)
        val now = Date()
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }.time
        val today = PrayerTimes(coordinates, DateComponents.from(now), params)
        val next = PrayerTimes(coordinates, DateComponents.from(tomorrow), params)
        val reminders = PrayerSettingsStore.reminderPrayers(context)

        for (prayer in ALERT_PRAYERS) {
            val times = if (getPrayerTime(today, prayer).after(now)) today else next
            scheduleAlarm(
                context,
                prayer,
                getPrayerTime(times, prayer),
                getPrayerEnd(times, prayer),
                wakeUp = prayer in reminders
            )
        }
        scheduleMidnightAlarm(context)
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        ALERT_PRAYERS.forEach { alarmManager.cancel(prayerIntent(context, it)) }
        alarmManager.cancel(midnightIntent(context))
    }

    private fun scheduleAlarm(context: Context, prayer: Prayer, time: Date, endsAt: Date?, wakeUp: Boolean) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = prayerIntent(context, prayer, time, endsAt)

        if (!wakeUp) {
            alarmManager.set(AlarmManager.RTC, time.time, pendingIntent)
            return
        }
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.time, pendingIntent)
            } else {
                Log.w(TAG, "Exact alarms not permitted, using inexact alarm for ${prayer.name}")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.time, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm for ${prayer.name}: ${e.message}")
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time.time, pendingIntent)
        }
    }

    private fun prayerIntent(context: Context, prayer: Prayer, time: Date? = null, endsAt: Date? = null): PendingIntent {
        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME, prayer.name)
            time?.let { putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_TIME, it.time) }
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_ENDS, endsAt?.time ?: 0L)
        }
        return PendingIntent.getBroadcast(
            context,
            PrayerAlarmReceiver.getRequestCode(prayer),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, DailyUpdateReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** A safety net that refreshes everything once a day; it never needs to wake the phone. */
    private fun scheduleMidnightAlarm(context: Context) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_MONTH, 1)
        }
        context.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.RTC, calendar.timeInMillis, midnightIntent(context))
    }

    private fun getPrayerTime(prayerTimes: PrayerTimes, prayer: Prayer): Date {
        return when (prayer) {
            Prayer.FAJR -> prayerTimes.fajr
            Prayer.SUNRISE -> prayerTimes.sunrise
            Prayer.DHUHR -> prayerTimes.dhuhr
            Prayer.ASR -> prayerTimes.asr
            Prayer.MAGHRIB -> prayerTimes.maghrib
            Prayer.ISHA -> prayerTimes.isha
            else -> Date()
        }
    }

    /** When the next prayer begins, after which a reminder for this one is no longer useful. */
    private fun getPrayerEnd(prayerTimes: PrayerTimes, prayer: Prayer): Date? {
        return when (prayer) {
            Prayer.FAJR -> prayerTimes.sunrise
            Prayer.DHUHR -> prayerTimes.asr
            Prayer.ASR -> prayerTimes.maghrib
            Prayer.MAGHRIB -> prayerTimes.isha
            else -> null
        }
    }
}
