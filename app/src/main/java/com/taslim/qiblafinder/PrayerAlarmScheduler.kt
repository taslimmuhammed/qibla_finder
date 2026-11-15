package com.taslim.qiblafinder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.batoulapps.adhan.*
import com.batoulapps.adhan.data.DateComponents
import java.util.*

/**
 * Utility object to schedule exact alarms for each prayer time.
 * Called from the service, boot receiver, and daily update receiver.
 */
object PrayerAlarmScheduler {

    private const val TAG = "PrayerAlarmScheduler"

    // Prayers that should trigger alert notifications (excludes SUNRISE)
    private val ALERT_PRAYERS = listOf(
        Prayer.FAJR,
        Prayer.DHUHR,
        Prayer.ASR,
        Prayer.MAGHRIB,
        Prayer.ISHA
    )

    /**
     * Schedule exact alarms for all remaining prayer times today.
     * Any prayer whose time has already passed is skipped.
     * If all today's prayers have passed, schedules tomorrow's.
     */
    fun scheduleAllPrayerAlarms(context: Context) {
        val locationPrefs = context.getSharedPreferences("location_data", Context.MODE_PRIVATE)
        val latitude = locationPrefs.getFloat("latitude", 0f).toDouble()
        val longitude = locationPrefs.getFloat("longitude", 0f).toDouble()

        if (latitude == 0.0 && longitude == 0.0) {
            Log.w(TAG, "No location data available, cannot schedule prayer alarms")
            return
        }

        val sharedPreferences = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
        val madhabName = sharedPreferences.getString("madhab_preference", Madhab.SHAFI.name) ?: Madhab.SHAFI.name
        val madhab = Madhab.valueOf(madhabName)

        val methodName = sharedPreferences.getString("method_preference", "MUSLIM_WORLD_LEAGUE") ?: "MUSLIM_WORLD_LEAGUE"
        val calculationMethod = resolveCalculationMethod(methodName)

        val coordinates = Coordinates(latitude, longitude)
        val now = Date()
        val params = calculationMethod.parameters
        params.madhab = madhab

        // Calculate today's prayer times
        val todayDate = DateComponents.from(now)
        val todayPrayerTimes = PrayerTimes(coordinates, todayDate, params)

        var anyScheduledToday = false

        for (prayer in ALERT_PRAYERS) {
            val prayerTime = getPrayerTime(todayPrayerTimes, prayer)

            if (prayerTime.after(now)) {
                // This prayer is still upcoming — schedule it
                scheduleExactAlarm(context, prayer, prayerTime, getPrayerEnd(todayPrayerTimes, prayer))
                anyScheduledToday = true
                Log.d(TAG, "Scheduled alarm for ${prayer.name} at $prayerTime")
            } else {
                // Cancel any stale alarm for this prayer
                cancelAlarm(context, prayer)
            }
        }

        // If no prayers left today, schedule all of tomorrow's
        if (!anyScheduledToday) {
            val tomorrowCal = Calendar.getInstance()
            tomorrowCal.add(Calendar.DAY_OF_MONTH, 1)
            val tomorrowDate = DateComponents.from(tomorrowCal.time)
            val tomorrowPrayerTimes = PrayerTimes(coordinates, tomorrowDate, params)

            for (prayer in ALERT_PRAYERS) {
                val prayerTime = getPrayerTime(tomorrowPrayerTimes, prayer)
                scheduleExactAlarm(context, prayer, prayerTime, getPrayerEnd(tomorrowPrayerTimes, prayer))
                Log.d(TAG, "Scheduled tomorrow's alarm for ${prayer.name} at $prayerTime")
            }
        }

        // Schedule midnight daily reschedule alarm
        scheduleMidnightAlarm(context)
    }

    private fun scheduleExactAlarm(context: Context, prayer: Prayer, time: Date, endsAt: Date?) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = PrayerAlarmReceiver.getRequestCode(prayer)
        if (requestCode == -1) return

        val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_NAME, prayer.name)
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_TIME, time.time)
            putExtra(PrayerAlarmReceiver.EXTRA_PRAYER_ENDS, endsAt?.time ?: 0L)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        time.time,
                        pendingIntent
                    )
                } else {
                    // Fallback: use inexact alarm (better than nothing)
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        time.time,
                        pendingIntent
                    )
                    Log.w(TAG, "Exact alarms not permitted, using inexact alarm for ${prayer.name}")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    time.time,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm for ${prayer.name}: ${e.message}")
            // Fallback to inexact
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                time.time,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm(context: Context, prayer: Prayer) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = PrayerAlarmReceiver.getRequestCode(prayer)
        if (requestCode == -1) return

        val intent = Intent(context, PrayerAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Schedule a midnight alarm to recalculate and reschedule prayer alarms for the new day.
     */
    private fun scheduleMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If it's already past midnight, schedule for next day
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val intent = Intent(context, DailyUpdateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling midnight alarm: ${e.message}")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }

        Log.d(TAG, "Scheduled midnight alarm at ${calendar.time}")
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

    private fun resolveCalculationMethod(methodName: String): CalculationMethod {
        return CalculationOption.fromKey(methodName).method
    }
}
