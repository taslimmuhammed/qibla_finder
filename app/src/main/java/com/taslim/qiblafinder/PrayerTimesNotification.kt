package com.taslim.qiblafinder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.Prayer
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Today's prayer times in the notification bar.
 *
 * A plain ongoing notification rather than a foreground service, so nothing runs between
 * updates. It's re-posted when the app opens, at each prayer time by [PrayerAlarmReceiver],
 * after midnight by [DailyUpdateReceiver], and on reboot, app update or a clock change by
 * [BootReceiver].
 */
object PrayerTimesNotification {

    const val CHANNEL_ID = "prayer_times_channel"
    const val NOTIFICATION_ID = 1001

    /** Posts or refreshes the notification, if the user wants it and allows notifications. */
    @SuppressLint("MissingPermission")
    fun update(context: Context) {
        if (!PrayerSettingsStore.isNotificationsEnabled(context) ||
            !PrayerSettingsStore.hasNotificationPermission(context)
        ) {
            return
        }
        createChannel(context)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, build(context))
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Prayer Times",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Daily prayer times notification"
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(null, null)
            enableVibration(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun build(context: Context): android.app.Notification {
        val sharedPreferences = context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
        val locationPrefs = context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)

        val madhabName = sharedPreferences.getString("madhab_preference", Madhab.SHAFI.name) ?: Madhab.SHAFI.name
        val madhab = Madhab.valueOf(madhabName)
        val methodName = sharedPreferences.getString("method_preference", "MUSLIM_WORLD_LEAGUE") ?: "MUSLIM_WORLD_LEAGUE"
        val calculationMethod = CalculationOption.fromKey(methodName).method

        val latitude = locationPrefs.getFloat("latitude", 0f).toDouble()
        val longitude = locationPrefs.getFloat("longitude", 0f).toDouble()

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val layout = RemoteViews(context.packageName, R.layout.notification_prayer_times)
        if (latitude != 0.0 || longitude != 0.0) {
            fillTimes(context, layout, Coordinates(latitude, longitude), madhab, calculationMethod)
        } else {
            listOf(R.id.fajr_time, R.id.dhuhr_time, R.id.asr_time, R.id.maghrib_time, R.id.isha_time)
                .forEach { layout.setTextViewText(it, "—") }
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setCustomContentView(layout)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            // The times were worked out for another time zone, so the user has likely travelled
            .setSubText(if (hasChangedTimeZone(context)) "Moved? Tap to update" else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setShowWhen(false)
            .build()
    }

    private fun fillTimes(
        context: Context,
        layout: RemoteViews,
        coordinates: Coordinates,
        madhab: Madhab,
        calculationMethod: CalculationMethod
    ) {
        val now = Date()
        val params = calculationMethod.parameters
        params.madhab = madhab

        val todayPrayerTimes = PrayerTimes(coordinates, DateComponents.from(now), params)
        val nextPrayerToday = todayPrayerTimes.nextPrayer()

        // After Isha the notification moves on to tomorrow, with Fajr next
        val showTomorrow = nextPrayerToday == Prayer.NONE || now.after(todayPrayerTimes.isha)
        val prayerTimes: PrayerTimes
        val highlightPrayer: Prayer
        if (showTomorrow) {
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
            prayerTimes = PrayerTimes(coordinates, DateComponents.from(tomorrow.time), params)
            highlightPrayer = Prayer.FAJR
        } else {
            prayerTimes = todayPrayerTimes
            highlightPrayer = if (nextPrayerToday == Prayer.SUNRISE) Prayer.DHUHR else nextPrayerToday
        }

        val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())
        layout.setTextViewText(R.id.fajr_time, timeFormat.format(prayerTimes.fajr))
        layout.setTextViewText(R.id.dhuhr_time, timeFormat.format(prayerTimes.dhuhr))
        layout.setTextViewText(R.id.asr_time, timeFormat.format(prayerTimes.asr))
        layout.setTextViewText(R.id.maghrib_time, timeFormat.format(prayerTimes.maghrib))
        layout.setTextViewText(R.id.isha_time, timeFormat.format(prayerTimes.isha))

        data class PrayerViewIds(val container: Int, val label: Int, val time: Int)
        val prayerViews = mapOf(
            Prayer.FAJR to PrayerViewIds(R.id.fajr_container, R.id.fajr_label, R.id.fajr_time),
            Prayer.DHUHR to PrayerViewIds(R.id.dhuhr_container, R.id.dhuhr_label, R.id.dhuhr_time),
            Prayer.ASR to PrayerViewIds(R.id.asr_container, R.id.asr_label, R.id.asr_time),
            Prayer.MAGHRIB to PrayerViewIds(R.id.maghrib_container, R.id.maghrib_label, R.id.maghrib_time),
            Prayer.ISHA to PrayerViewIds(R.id.isha_container, R.id.isha_label, R.id.isha_time)
        )

        val nameColor = context.getColor(R.color.notification_name)
        val timeColor = context.getColor(R.color.notification_time)
        val highlightNameColor = context.getColor(R.color.notification_highlight_name)
        val highlightTimeColor = context.getColor(R.color.notification_highlight_time)

        for ((prayer, ids) in prayerViews) {
            if (prayer == highlightPrayer) {
                layout.setInt(ids.container, "setBackgroundResource", R.drawable.notification_highlight_bg)
                layout.setTextColor(ids.label, highlightNameColor)
                layout.setTextColor(ids.time, highlightTimeColor)
            } else {
                layout.setInt(ids.container, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
                layout.setTextColor(ids.label, nameColor)
                layout.setTextColor(ids.time, timeColor)
            }
        }
    }

    // region Travel

    private const val LOCATION_PREFS = "location_data"
    private const val KEY_TIME_ZONE = "time_zone"

    /** Remembers the time zone a location was saved in, to notice later when the user travels. */
    fun rememberTimeZone(context: Context) {
        context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_TIME_ZONE, TimeZone.getDefault().id)
        }
    }

    /** True when the phone's time zone has changed since the location was saved. */
    fun hasChangedTimeZone(context: Context): Boolean {
        val saved = context.getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TIME_ZONE, null) ?: return false
        return saved != TimeZone.getDefault().id
    }

    // endregion
}
