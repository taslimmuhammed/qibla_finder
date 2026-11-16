package com.taslim.qiblafinder

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.batoulapps.adhan.*
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.util.*

class PrayerTimesNotificationService : Service() {
    
    companion object {
        const val CHANNEL_ID = "prayer_times_channel"
        const val NOTIFICATION_ID = 1001
        
        fun startService(context: Context) {
            val intent = Intent(context, PrayerTimesNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, PrayerTimesNotificationService::class.java)
            context.stopService(intent)
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var lastHighlightedPrayer: Prayer? = null
    
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshNotification()
            // Schedule next check in 60 seconds
            handler.postDelayed(this, 60_000L)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Reset tracked state so the notification is always re-posted with fresh data.
        // This is critical when called from PrayerAlarmReceiver at prayer time —
        // it ensures the notification reappears even if the user dismissed it.
        lastHighlightedPrayer = null
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Schedule exact alarms for each prayer time (replaces old inexact daily alarm)
        PrayerAlarmScheduler.scheduleAllPrayerAlarms(this)
        
        // Start periodic refresh to keep the highlight current
        handler.removeCallbacks(refreshRunnable)
        handler.post(refreshRunnable)
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Prayer Times",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Daily prayer times notification"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Refreshes the notification only if the highlighted prayer has changed,
     * to avoid unnecessary notification updates.
     */
    private fun refreshNotification() {
        val currentHighlight = getCurrentHighlightPrayer()
        if (currentHighlight != lastHighlightedPrayer) {
            lastHighlightedPrayer = currentHighlight
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }
    
    /**
     * Determines which prayer should currently be highlighted.
     */
    private fun getCurrentHighlightPrayer(): Prayer? {
        val locationPrefs = getSharedPreferences("location_data", Context.MODE_PRIVATE)
        val latitude = locationPrefs.getFloat("latitude", 0f).toDouble()
        val longitude = locationPrefs.getFloat("longitude", 0f).toDouble()
        
        if (latitude == 0.0 && longitude == 0.0) return null
        
        val sharedPreferences = getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
        val madhabName = sharedPreferences.getString("madhab_preference", Madhab.SHAFI.name) ?: Madhab.SHAFI.name
        val madhab = Madhab.valueOf(madhabName)
        
        val methodName = sharedPreferences.getString("method_preference", "MUSLIM_WORLD_LEAGUE") ?: "MUSLIM_WORLD_LEAGUE"
        val calculationMethod = resolveCalculationMethod(methodName)
        
        val coordinates = Coordinates(latitude, longitude)
        val now = Date()
        val params = calculationMethod.parameters
        params.madhab = madhab
        
        val todayDate = DateComponents.from(now)
        val todayPrayerTimes = PrayerTimes(coordinates, todayDate, params)
        val nextPrayerToday = todayPrayerTimes.nextPrayer()
        val showTomorrow = nextPrayerToday == Prayer.NONE || now.after(todayPrayerTimes.isha)
        
        return if (showTomorrow) {
            Prayer.FAJR
        } else if (nextPrayerToday == Prayer.SUNRISE) {
            Prayer.DHUHR
        } else {
            nextPrayerToday
        }
    }
    
    private fun resolveCalculationMethod(methodName: String): CalculationMethod {
        return CalculationOption.fromKey(methodName).method
    }
    
    private fun createNotification(): Notification {
        val sharedPreferences = getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
        val locationPrefs = getSharedPreferences("location_data", Context.MODE_PRIVATE)
        
        // Get saved preferences
        val madhabName = sharedPreferences.getString("madhab_preference", Madhab.SHAFI.name) ?: Madhab.SHAFI.name
        val madhab = Madhab.valueOf(madhabName)
        
        val methodName = sharedPreferences.getString("method_preference", "MUSLIM_WORLD_LEAGUE") ?: "MUSLIM_WORLD_LEAGUE"
        val calculationMethod = resolveCalculationMethod(methodName)
        
        // Get saved location
        val latitude = locationPrefs.getFloat("latitude", 0f).toDouble()
        val longitude = locationPrefs.getFloat("longitude", 0f).toDouble()
        
        // Intent to open app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create custom notification layout
        val notificationLayout = android.widget.RemoteViews(
            packageName,
            R.layout.notification_prayer_times
        )
        
        if (latitude != 0.0 && longitude != 0.0) {
            val location = Location("").apply {
                this.latitude = latitude
                this.longitude = longitude
            }
            updateNotificationLayout(notificationLayout, location, madhab, calculationMethod)
        } else {
            // Show placeholder
            notificationLayout.setTextViewText(R.id.fajr_time, "—")
            notificationLayout.setTextViewText(R.id.dhuhr_time, "—")
            notificationLayout.setTextViewText(R.id.asr_time, "—")
            notificationLayout.setTextViewText(R.id.maghrib_time, "—")
            notificationLayout.setTextViewText(R.id.isha_time, "—")
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(getColor(R.color.notification_accent))
            .setCustomContentView(notificationLayout)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setShowWhen(false)
            .build()
    }
    
    private fun updateNotificationLayout(
        layout: android.widget.RemoteViews,
        location: Location,
        madhab: Madhab,
        calculationMethod: CalculationMethod
    ) {
        val coordinates = Coordinates(location.latitude, location.longitude)
        val now = Date()
        val params = calculationMethod.parameters
        params.madhab = madhab
        
        // Calculate today's prayer times first to check if we're past Isha
        val todayDate = DateComponents.from(now)
        val todayPrayerTimes = PrayerTimes(coordinates, todayDate, params)
        
        // Determine if we should show tomorrow's prayers
        val nextPrayerToday = todayPrayerTimes.nextPrayer()
        val showTomorrow = nextPrayerToday == Prayer.NONE || now.after(todayPrayerTimes.isha)
        
        val prayerTimes: PrayerTimes
        val highlightPrayer: Prayer
        
        if (showTomorrow) {
            // Show tomorrow's prayer times
            val tomorrowCal = Calendar.getInstance()
            tomorrowCal.add(Calendar.DAY_OF_MONTH, 1)
            val tomorrowDate = DateComponents.from(tomorrowCal.time)
            prayerTimes = PrayerTimes(coordinates, tomorrowDate, params)
            highlightPrayer = Prayer.FAJR
        } else {
            prayerTimes = todayPrayerTimes
            highlightPrayer = if (nextPrayerToday == Prayer.SUNRISE) Prayer.DHUHR else nextPrayerToday
        }
        
        // Update tracked state
        lastHighlightedPrayer = highlightPrayer
        
        // Set prayer times
        val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())
        layout.setTextViewText(R.id.fajr_time, timeFormat.format(prayerTimes.fajr))
        layout.setTextViewText(R.id.dhuhr_time, timeFormat.format(prayerTimes.dhuhr))
        layout.setTextViewText(R.id.asr_time, timeFormat.format(prayerTimes.asr))
        layout.setTextViewText(R.id.maghrib_time, timeFormat.format(prayerTimes.maghrib))
        layout.setTextViewText(R.id.isha_time, timeFormat.format(prayerTimes.isha))
        
        // Prayer data: container ID, label ID, time ID
        data class PrayerViewIds(val container: Int, val label: Int, val time: Int)
        val prayerViews = mapOf(
            Prayer.FAJR to PrayerViewIds(R.id.fajr_container, R.id.fajr_label, R.id.fajr_time),
            Prayer.DHUHR to PrayerViewIds(R.id.dhuhr_container, R.id.dhuhr_label, R.id.dhuhr_time),
            Prayer.ASR to PrayerViewIds(R.id.asr_container, R.id.asr_label, R.id.asr_time),
            Prayer.MAGHRIB to PrayerViewIds(R.id.maghrib_container, R.id.maghrib_label, R.id.maghrib_time),
            Prayer.ISHA to PrayerViewIds(R.id.isha_container, R.id.isha_label, R.id.isha_time)
        )
        
        val nameColor = getColor(R.color.notification_name)
        val timeColor = getColor(R.color.notification_time)
        val highlightNameColor = getColor(R.color.notification_highlight_name)
        val highlightTimeColor = getColor(R.color.notification_highlight_time)

        // Apply highlight to the next prayer
        for ((prayer, ids) in prayerViews) {
            val isHighlighted = prayer == highlightPrayer

            if (isHighlighted) {
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
    
    fun updateNotification() {
        lastHighlightedPrayer = null // Force refresh
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
}