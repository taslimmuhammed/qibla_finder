package com.taslim.qiblafinder

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.batoulapps.adhan.Prayer
import java.util.Date

/** Posts the alert that a prayer's time has begun, for prayers the user turned the bell on for. */
object PrayerReminders {

    private const val CHANNEL_ID = "prayer_reminders"
    private const val KEY_LAST_REMINDER = "last_reminder"

    // Some phones (OnePlus and Oppo among them) stretch exact alarms into an hour-long window,
    // so a reminder can arrive well after the prayer begins. It still helps until the next
    // prayer starts; this cap covers Isha, which has no next prayer the same evening.
    private const val MAX_LATENESS_MS = 90 * 60_000L

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Prayer reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "An alert when each prayer time begins"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun notifyIfEnabled(context: Context, prayer: Prayer, scheduledAt: Long, endsAt: Long) {
        if (prayer !in PrayerSettingsStore.reminderPrayers(context)) return
        if (!PrayerSettingsStore.hasNotificationPermission(context)) return
        val now = System.currentTimeMillis()
        if (endsAt > 0 && now >= endsAt) return
        if (scheduledAt > 0 && now - scheduledAt > MAX_LATENESS_MS) return

        // The receiver can run twice for one alarm if it is rescheduled at the exact boundary
        val prefs = context.getSharedPreferences(PrayerSettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
        val token = "${prayer.name}@${startOfDay(Date())}"
        if (prefs.getString(KEY_LAST_REMINDER, null) == token) return
        prefs.edit { putString(KEY_LAST_REMINDER, token) }

        createChannel(context)
        val city = context.getSharedPreferences("location_data", Context.MODE_PRIVATE)
            .getString("place_name", null)
            ?.substringBefore(",")
            ?.takeIf { it.isNotBlank() }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle("${prayer.displayName} · ${prayer.arabicName}")
            .setContentText(
                if (city != null) "It's time for ${prayer.displayName} in $city." else "It's time for ${prayer.displayName}."
            )
            // Shows the prayer's start time, not when a delayed alarm happened to arrive
            .setWhen(if (scheduledAt > 0) scheduledAt else now)
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        NotificationManagerCompat.from(context)
            .notify(REMINDER_ID_BASE + PrayerAlarmReceiver.getRequestCode(prayer), notification)
    }

    private const val REMINDER_ID_BASE = 2000
}
