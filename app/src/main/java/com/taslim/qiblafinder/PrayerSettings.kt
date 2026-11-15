package com.taslim.qiblafinder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.Prayer
import java.util.Locale

/**
 * Calculation methods offered to the user. [key] is what gets persisted in
 * SharedPreferences and read back by the notification service, so it must never change.
 */
enum class CalculationOption(
    val key: String,
    val title: String,
    val regions: String,
    val method: CalculationMethod
) {
    MUSLIM_WORLD_LEAGUE(
        "MUSLIM_WORLD_LEAGUE", "Muslim World League",
        "Europe, the Far East and much of the world", CalculationMethod.MUSLIM_WORLD_LEAGUE
    ),
    NORTH_AMERICA(
        "NORTH_AMERICA", "ISNA",
        "United States and Canada", CalculationMethod.NORTH_AMERICA
    ),
    MOON_SIGHTING_COMMITTEE(
        "MOON_SIGHTING_COMMITTEE", "Moonsighting Committee",
        "United Kingdom and parts of North America", CalculationMethod.MOON_SIGHTING_COMMITTEE
    ),
    UMM_AL_QURA(
        "UMM_AL_QURA", "Umm al-Qura, Makkah",
        "Saudi Arabia and the Arabian Peninsula", CalculationMethod.UMM_AL_QURA
    ),
    DUBAI("DUBAI", "Dubai", "Used across the United Arab Emirates", CalculationMethod.DUBAI),
    KUWAIT("KUWAIT", "Kuwait", "Used in Kuwait", CalculationMethod.KUWAIT),
    QATAR("QATAR", "Qatar", "Used in Qatar", CalculationMethod.QATAR),
    EGYPTIAN(
        "EGYPTIAN", "Egyptian General Authority",
        "Egypt, the Levant, Iraq and parts of Africa", CalculationMethod.EGYPTIAN
    ),
    KARACHI(
        "KARACHI", "University of Islamic Sciences, Karachi",
        "Pakistan, India, Bangladesh and Afghanistan", CalculationMethod.KARACHI
    ),
    SINGAPORE(
        "SINGAPORE", "Singapore",
        "Singapore, Malaysia, Indonesia and Brunei", CalculationMethod.SINGAPORE
    );

    companion object {
        fun fromKey(key: String?): CalculationOption =
            entries.firstOrNull { it.key == key } ?: MUSLIM_WORLD_LEAGUE
    }
}

val Madhab.asrLabel: String
    get() = if (this == Madhab.HANAFI) "Hanafi" else "Standard"

val Madhab.asrDescription: String
    get() = if (this == Madhab.HANAFI) {
        "Asr starts when a shadow is twice an object's length — a little later in the afternoon."
    } else {
        "Used by Shafi'i, Maliki and Hanbali schools. Asr starts when a shadow equals an object's length."
    }

/** Sensible defaults so new users rarely need to touch the calculation settings. */
object RegionDefaults {

    private val hanafiMajority = setOf(
        "PK", "AF", "BD", "IN", "TR", "UZ", "TJ", "KZ", "KG", "TM", "AZ"
    )

    fun methodFor(countryCode: String?): CalculationOption = when (countryCode?.uppercase()) {
        "US", "CA" -> CalculationOption.NORTH_AMERICA
        "GB", "IE" -> CalculationOption.MOON_SIGHTING_COMMITTEE
        "SA", "BH", "OM", "YE" -> CalculationOption.UMM_AL_QURA
        "AE" -> CalculationOption.DUBAI
        "KW" -> CalculationOption.KUWAIT
        "QA" -> CalculationOption.QATAR
        "EG", "SY", "LB", "IQ", "JO", "PS", "SD", "LY" -> CalculationOption.EGYPTIAN
        "PK", "IN", "BD", "AF" -> CalculationOption.KARACHI
        "SG", "MY", "ID", "BN" -> CalculationOption.SINGAPORE
        else -> CalculationOption.MUSLIM_WORLD_LEAGUE
    }

    fun madhabFor(countryCode: String?): Madhab =
        if (countryCode?.uppercase() in hanafiMajority) Madhab.HANAFI else Madhab.SHAFI

    /** Best guess at the user's country before (or without) reverse geocoding. */
    fun guessCountry(context: Context): String? {
        val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        return listOfNotNull(
            telephony?.networkCountryIso,
            telephony?.simCountryIso,
            Locale.getDefault().country
        ).firstOrNull { it.length == 2 }?.uppercase()
    }

    fun countryName(countryCode: String?): String? =
        countryCode?.let { Locale("", it).displayCountry.takeIf(String::isNotBlank) }
}

/**
 * Prayer preferences backed by SharedPreferences and exposed as Compose state, so a change
 * made in Settings is reflected on the Today screen immediately.
 */
class PrayerSettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var madhab by mutableStateOf(readMadhab())
        private set

    var calculation by mutableStateOf(CalculationOption.fromKey(prefs.getString(KEY_METHOD, null)))
        private set

    var notificationsEnabled by mutableStateOf(prefs.getBoolean(KEY_NOTIFICATIONS, true))
        private set

    /** Prayers that get an alert when their time begins. */
    var reminders by mutableStateOf(readReminders(prefs))
        private set

    // Users who configured an earlier version of the app already went through setup
    var onboardingComplete by mutableStateOf(
        prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false) || prefs.contains(KEY_MADHAB)
    )
        private set

    /** Whether the first-run compass tip on the Qibla screen has been dismissed. */
    var compassTipSeen by mutableStateOf(prefs.getBoolean(KEY_COMPASS_TIP_SEEN, false))
        private set

    fun updateMadhab(value: Madhab) {
        madhab = value
        prefs.edit { putString(KEY_MADHAB, value.name) }
        refreshNotification()
    }

    fun updateCalculation(value: CalculationOption) {
        calculation = value
        prefs.edit { putString(KEY_METHOD, value.key) }
        refreshNotification()
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled = enabled
        prefs.edit { putBoolean(KEY_NOTIFICATIONS, enabled) }
        if (enabled) {
            PrayerTimesNotificationService.startService(appContext)
        } else {
            PrayerTimesNotificationService.stopService(appContext)
        }
    }

    fun setReminder(prayer: Prayer, enabled: Boolean) {
        updateReminders(if (enabled) reminders + prayer else reminders - prayer)
    }

    fun updateReminders(value: Set<Prayer>) {
        reminders = value.intersect(REMINDER_PRAYERS.toSet())
        prefs.edit { putStringSet(KEY_REMINDERS, reminders.mapTo(mutableSetOf()) { it.name }) }
        if (reminders.isNotEmpty()) PrayerAlarmScheduler.scheduleAllPrayerAlarms(appContext)
    }

    fun markCompassTipSeen() {
        compassTipSeen = true
        prefs.edit { putBoolean(KEY_COMPASS_TIP_SEEN, true) }
    }

    fun completeOnboarding(
        madhab: Madhab,
        calculation: CalculationOption,
        notifications: Boolean,
        reminders: Set<Prayer>
    ) {
        this.madhab = madhab
        this.calculation = calculation
        notificationsEnabled = notifications
        this.reminders = reminders
        prefs.edit {
            putString(KEY_MADHAB, madhab.name)
            putString(KEY_METHOD, calculation.key)
            putBoolean(KEY_NOTIFICATIONS, notifications)
            putStringSet(KEY_REMINDERS, reminders.mapTo(mutableSetOf()) { it.name })
            putBoolean(KEY_ONBOARDING_COMPLETE, true)
        }
        if (notifications) PrayerTimesNotificationService.startService(appContext)
        if (reminders.isNotEmpty()) PrayerAlarmScheduler.scheduleAllPrayerAlarms(appContext)
        onboardingComplete = true
    }

    private fun refreshNotification() {
        // The service re-reads preferences on every start
        if (notificationsEnabled) PrayerTimesNotificationService.startService(appContext)
        if (reminders.isNotEmpty()) PrayerAlarmScheduler.scheduleAllPrayerAlarms(appContext)
    }

    private fun readMadhab(): Madhab =
        runCatching { Madhab.valueOf(prefs.getString(KEY_MADHAB, null) ?: Madhab.SHAFI.name) }
            .getOrDefault(Madhab.SHAFI)

    companion object {
        const val PREFS_NAME = "prayer_settings"
        const val KEY_MADHAB = "madhab_preference"
        const val KEY_METHOD = "method_preference"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_REMINDERS = "prayer_reminders"
        const val KEY_COMPASS_TIP_SEEN = "compass_tip_seen"

        /** The five prayers that can have a reminder; sunrise is a time, not a prayer. */
        val REMINDER_PRAYERS = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

        // Existing users haven't opted in to sound alerts, so the default is none
        private fun readReminders(prefs: android.content.SharedPreferences): Set<Prayer> =
            prefs.getStringSet(KEY_REMINDERS, emptySet()).orEmpty()
                .mapNotNull { name -> runCatching { Prayer.valueOf(name) }.getOrNull() }
                .toSet()

        fun reminderPrayers(context: Context): Set<Prayer> =
            readReminders(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

        /** Alarms are needed if either the times notification or any reminder is on. */
        fun needsPrayerAlarms(context: Context): Boolean =
            isNotificationsEnabled(context) || reminderPrayers(context).isNotEmpty()

        fun isNotificationsEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_NOTIFICATIONS, true)

        fun hasNotificationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
