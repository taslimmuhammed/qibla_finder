package com.taslim.qiblafinder

import android.content.Context
import android.icu.util.IslamicCalendar
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.Prayer
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

const val KAABA_LATITUDE = 21.422487
const val KAABA_LONGITUDE = 39.826206

/** The five daily prayers plus sunrise, in the order they occur. */
val DAILY_TIMES = listOf(
    Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA
)

val Prayer.displayName: String
    get() = when (this) {
        Prayer.FAJR -> "Fajr"
        Prayer.SUNRISE -> "Sunrise"
        Prayer.DHUHR -> "Dhuhr"
        Prayer.ASR -> "Asr"
        Prayer.MAGHRIB -> "Maghrib"
        Prayer.ISHA -> "Isha"
        else -> ""
    }

val Prayer.arabicName: String
    get() = when (this) {
        Prayer.FAJR -> "الفجر"
        Prayer.SUNRISE -> "الشروق"
        Prayer.DHUHR -> "الظهر"
        Prayer.ASR -> "العصر"
        Prayer.MAGHRIB -> "المغرب"
        Prayer.ISHA -> "العشاء"
        else -> ""
    }

fun PrayerTimes.timeOf(prayer: Prayer): Date = when (prayer) {
    Prayer.FAJR -> fajr
    Prayer.SUNRISE -> sunrise
    Prayer.DHUHR -> dhuhr
    Prayer.ASR -> asr
    Prayer.MAGHRIB -> maghrib
    Prayer.ISHA -> isha
    else -> fajr
}

data class PrayerSlot(val prayer: Prayer, val time: Date, val isTomorrow: Boolean = false)

/**
 * Today's prayer times plus the neighbouring Isha/Fajr, so "current" and "next" are always
 * defined — including after Isha and in the hours before Fajr.
 */
class DaySchedule(
    val today: List<PrayerSlot>,
    private val yesterdayIsha: PrayerSlot,
    private val tomorrowFajr: PrayerSlot
) {
    private val timeline = listOf(yesterdayIsha) + today + tomorrowFajr

    private fun periodStart(now: Date): PrayerSlot =
        timeline.lastOrNull { !it.time.after(now) } ?: yesterdayIsha

    /** The prayer whose time is in progress, or null between sunrise and Dhuhr. */
    fun currentPrayer(now: Date): Prayer? =
        periodStart(now).prayer.takeIf { it != Prayer.SUNRISE }

    fun nextPrayer(now: Date): PrayerSlot =
        timeline.firstOrNull { it.time.after(now) && it.prayer != Prayer.SUNRISE } ?: tomorrowFajr

    fun sunrise(): Date = today.first { it.prayer == Prayer.SUNRISE }.time

    /** How far we are through the current period, from 0 to 1. */
    fun progress(now: Date): Float {
        val start = periodStart(now).time.time
        val end = nextPrayer(now).time.time
        if (end <= start) return 0f
        return ((now.time - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    fun previousSlot(now: Date): PrayerSlot = periodStart(now)
}

fun buildDaySchedule(
    latitude: Double,
    longitude: Double,
    madhab: Madhab,
    calculation: CalculationOption,
    day: Date
): DaySchedule {
    val coordinates = Coordinates(latitude, longitude)
    fun timesFor(offsetDays: Int): PrayerTimes {
        val calendar = Calendar.getInstance().apply {
            time = day
            add(Calendar.DAY_OF_YEAR, offsetDays)
        }
        val params = calculation.method.parameters.also { it.madhab = madhab }
        return PrayerTimes(coordinates, DateComponents.from(calendar.time), params)
    }

    val today = timesFor(0)
    return DaySchedule(
        today = DAILY_TIMES.map { PrayerSlot(it, today.timeOf(it)) },
        yesterdayIsha = PrayerSlot(Prayer.ISHA, timesFor(-1).isha),
        tomorrowFajr = PrayerSlot(Prayer.FAJR, timesFor(1).fajr, isTomorrow = true)
    )
}

fun startOfDay(date: Date): Long = Calendar.getInstance().apply {
    time = date
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Current time for countdowns, ticking on the minute since they only show minutes.
 * It stops while the app is out of sight, so a screen left open doesn't keep the
 * phone busy, and catches up as soon as the app is back.
 */
@Composable
fun rememberNow(tickMillis: Long = 60_000L): State<Date> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(Date(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Date()
                delay(tickMillis - System.currentTimeMillis() % tickMillis)
            }
        }
    }
}

/** "1 h 12 min", "8 min" — rounded up so the countdown never reads 0 before the time arrives. */
fun formatCountdown(from: Date, to: Date): String {
    val totalMinutes = ceil((to.time - from.time).coerceAtLeast(0) / 60_000.0).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes <= 1 -> "under a minute"
        hours == 0 -> "$minutes min"
        minutes == 0 -> "$hours h"
        else -> "$hours h $minutes min"
    }
}

/** Formats times following the device's 12/24-hour setting. */
class TimeFormatter(private val is24Hour: Boolean) {
    private val clock = SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", Locale.getDefault())
    private val meridiem = SimpleDateFormat("a", Locale.getDefault())

    fun clock(date: Date): String = clock.format(date)
    fun meridiem(date: Date): String? = if (is24Hour) null else meridiem.format(date)
    fun full(date: Date): String = meridiem(date)?.let { "${clock(date)} $it" } ?: clock(date)
}

@Composable
fun rememberTimeFormatter(): TimeFormatter {
    val context = LocalContext.current
    return remember { TimeFormatter(android.text.format.DateFormat.is24HourFormat(context)) }
}

private val HIJRI_MONTHS = listOf(
    "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
    "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah"
)

fun hijriDate(date: Date): String {
    val calendar = IslamicCalendar().apply {
        calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        time = date
    }
    val day = calendar.get(android.icu.util.Calendar.DAY_OF_MONTH)
    val month = HIJRI_MONTHS[calendar.get(android.icu.util.Calendar.MONTH)]
    val year = calendar.get(android.icu.util.Calendar.YEAR)
    return "$day $month $year AH"
}

fun gregorianDate(date: Date): String =
    SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(date)

private val COMPASS_POINTS = listOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
)

/** The 16-point compass name for a bearing, e.g. 119° → "ESE". */
fun compassPoint(bearing: Float): String {
    val normalized = ((bearing % 360f) + 360f) % 360f
    return COMPASS_POINTS[((normalized + 11.25f) / 22.5f).toInt() % 16]
}

/** Whole degrees, 0–359, so 359.6° reads 0° rather than 360°. */
fun wholeDegrees(bearing: Float): Int = ((bearing.roundToInt() % 360) + 360) % 360

fun formatKm(km: Int): String = "${NumberFormat.getIntegerInstance().format(km)} km"

/** "21.4225° N, 39.8262° E" */
fun formatCoordinates(latitude: Double, longitude: Double, digits: Int = 4): String {
    val lat = "%.${digits}f° %s".format(Locale.US, abs(latitude), if (latitude >= 0) "N" else "S")
    val lon = "%.${digits}f° %s".format(Locale.US, abs(longitude), if (longitude >= 0) "E" else "W")
    return "$lat, $lon"
}

/** Initial great-circle bearing from a point to the Kaaba, in degrees from true north. */
fun qiblaBearing(latitude: Double, longitude: Double): Float {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(KAABA_LATITUDE)
    val deltaLon = Math.toRadians(KAABA_LONGITUDE - longitude)
    val y = sin(deltaLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
    return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
}

fun distanceToKaabaKm(location: Location): Int {
    val results = FloatArray(1)
    Location.distanceBetween(
        location.latitude, location.longitude, KAABA_LATITUDE, KAABA_LONGITUDE, results
    )
    return (results[0] / 1000f).toInt()
}

fun appVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull() ?: ""
