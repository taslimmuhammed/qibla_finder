package com.taslim.qiblafinder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.batoulapps.adhan.Prayer

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ALERT_CHANNEL_ID = "prayer_alert_channel"
        const val EXTRA_PRAYER_NAME = "prayer_name"
        const val ALERT_NOTIFICATION_ID_BASE = 2000

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

        fun getPrayerArabicName(prayerName: String): String {
            return when (prayerName) {
                "FAJR" -> "الفجر"
                "DHUHR" -> "الظهر"
                "ASR" -> "العصر"
                "MAGHRIB" -> "المغرب"
                "ISHA" -> "العشاء"
                else -> ""
            }
        }

        fun getPrayerDisplayName(prayerName: String): String {
            return when (prayerName) {
                "FAJR" -> "Fajr"
                "DHUHR" -> "Dhuhr"
                "ASR" -> "Asr"
                "MAGHRIB" -> "Maghrib"
                "ISHA" -> "Isha"
                else -> prayerName
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra(EXTRA_PRAYER_NAME) ?: return

        // Don't send notification for Sunrise
        if (prayerName == "SUNRISE") return

        createAlertChannel(context)
        showPrayerAlert(context, prayerName)

        // Reschedule alarms for remaining prayers today + tomorrow's alarms
        PrayerAlarmScheduler.scheduleAllPrayerAlarms(context)
    }

    private fun createAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Prayer Time Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when prayer time arrives"
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showPrayerAlert(context: Context, prayerName: String) {
        val displayName = getPrayerDisplayName(prayerName)
        val arabicName = getPrayerArabicName(prayerName)

        // Intent to open app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$displayName — $arabicName")
            .setContentText("It's time for $displayName prayer")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("It's time for $displayName prayer\n$arabicName"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        // Use a unique ID per prayer so they don't overwrite each other
        val notificationId = ALERT_NOTIFICATION_ID_BASE + getRequestCode(
            Prayer.valueOf(prayerName)
        )
        notificationManager.notify(notificationId, notification)
    }
}
