package com.example.qiblafinder

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
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
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        scheduleDailyUpdate()
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Prayer Times",
                NotificationManager.IMPORTANCE_DEFAULT
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
    
    private fun createNotification(): Notification {
        val sharedPreferences = getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
        val locationPrefs = getSharedPreferences("location_data", Context.MODE_PRIVATE)
        
        // Get saved preferences
        val madhabName = sharedPreferences.getString("madhab_preference", Madhab.SHAFI.name) ?: Madhab.SHAFI.name
        val madhab = Madhab.valueOf(madhabName)
        
        val methodName = sharedPreferences.getString("method_preference", "MUSLIM_WORLD_LEAGUE") ?: "MUSLIM_WORLD_LEAGUE"
        val calculationMethod = when (methodName) {
            "MUSLIM_WORLD_LEAGUE" -> CalculationMethod.MUSLIM_WORLD_LEAGUE
            "NORTH_AMERICA" -> CalculationMethod.NORTH_AMERICA
            "EGYPTIAN" -> CalculationMethod.EGYPTIAN
            "UMM_AL_QURA" -> CalculationMethod.UMM_AL_QURA
            "KARACHI" -> CalculationMethod.KARACHI
            "MOON_SIGHTING_COMMITTEE" -> CalculationMethod.MOON_SIGHTING_COMMITTEE
            else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
        }
        
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
            notificationLayout.setTextViewText(R.id.notification_title, "Prayer Times")
            notificationLayout.setTextViewText(R.id.fajr_time, "—")
            notificationLayout.setTextViewText(R.id.dhuhr_time, "—")
            notificationLayout.setTextViewText(R.id.asr_time, "—")
            notificationLayout.setTextViewText(R.id.maghrib_time, "—")
            notificationLayout.setTextViewText(R.id.isha_time, "—")
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_on)
            .setCustomContentView(notificationLayout)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
        val date = DateComponents.from(Date())
        val params = calculationMethod.parameters
        params.madhab = madhab
        
        val prayerTimes = PrayerTimes(coordinates, date, params)
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val currentPrayer = prayerTimes.currentPrayer()
        val nextPrayer = prayerTimes.nextPrayer()
        
        // Set title
        layout.setTextViewText(R.id.notification_title, "Today's Prayer Times")
        
        // Set prayer times
        layout.setTextViewText(R.id.fajr_time, timeFormat.format(prayerTimes.fajr))
        layout.setTextViewText(R.id.sunrise_time, timeFormat.format(prayerTimes.sunrise))
        layout.setTextViewText(R.id.dhuhr_time, timeFormat.format(prayerTimes.dhuhr))
        layout.setTextViewText(R.id.asr_time, timeFormat.format(prayerTimes.asr))
        layout.setTextViewText(R.id.maghrib_time, timeFormat.format(prayerTimes.maghrib))
        layout.setTextViewText(R.id.isha_time, timeFormat.format(prayerTimes.isha))
        
        // Reset all backgrounds to default
        layout.setInt(R.id.fajr_container, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
        layout.setInt(R.id.sunrise_container, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
        layout.setInt(R.id.dhuhr_container, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
        layout.setInt(R.id.asr_container, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
        layout.setInt(R.id.maghrib_container, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
        layout.setInt(R.id.isha_container, "setBackgroundColor", android.graphics.Color.TRANSPARENT)
        
        // Highlight next prayer with cyan color
        nextPrayer?.let { prayer ->
            val highlightColor = android.graphics.Color.parseColor("#3300D9FF") // Cyan with transparency
            when (prayer) {
                Prayer.FAJR -> layout.setInt(R.id.fajr_container, "setBackgroundColor", highlightColor)
                Prayer.SUNRISE -> layout.setInt(R.id.sunrise_container, "setBackgroundColor", highlightColor)
                Prayer.DHUHR -> layout.setInt(R.id.dhuhr_container, "setBackgroundColor", highlightColor)
                Prayer.ASR -> layout.setInt(R.id.asr_container, "setBackgroundColor", highlightColor)
                Prayer.MAGHRIB -> layout.setInt(R.id.maghrib_container, "setBackgroundColor", highlightColor)
                Prayer.ISHA -> layout.setInt(R.id.isha_container, "setBackgroundColor", highlightColor)
                else -> {}
            }
        }
    }
    
    private fun scheduleDailyUpdate() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Schedule update at midnight
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 1)
            set(Calendar.SECOND, 0)
            
            // If it's past midnight, schedule for next day
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        val intent = Intent(this, DailyUpdateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Use setRepeating for daily updates
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
    
    fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
}