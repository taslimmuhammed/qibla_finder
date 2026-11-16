package com.taslim.qiblafinder

import android.location.Location
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Location state owned by [MainActivity] and observed by the UI. */
@Stable
class LocationState {
    var location by mutableStateOf<Location?>(null)
    var placeName by mutableStateOf<String?>(null)
    var countryCode by mutableStateOf<String?>(null)
    var accuracyMeters by mutableFloatStateOf(0f)
    var permissionGranted by mutableStateOf(false)
    var permissionBlocked by mutableStateOf(false)
    var servicesEnabled by mutableStateOf(true)
    var isLocating by mutableStateOf(false)
    var lastAttemptFailed by mutableStateOf(false)
    /** True once the user has turned down the permission dialog in this session. */
    var permissionDenied by mutableStateOf(false)
    /** True when the location is a city the user picked rather than a GPS fix. */
    var isManual by mutableStateOf(false)
    /** True when the phone's time zone has changed since the user picked their city. */
    var movedTimeZone by mutableStateOf(false)

    val status: LocationStatus
        get() = when {
            isLocating -> LocationStatus.Locating
            location != null -> LocationStatus.Found
            !permissionGranted && permissionBlocked -> LocationStatus.Blocked
            !permissionGranted -> LocationStatus.NeedsPermission
            !servicesEnabled -> LocationStatus.ServicesOff
            lastAttemptFailed -> LocationStatus.Failed
            else -> LocationStatus.Idle
        }
}

enum class LocationStatus(
    val title: String,
    val message: String,
    val actionLabel: String?
) {
    NeedsPermission(
        "Find your direction",
        "Your location points the compass to the Kaaba and sets your prayer times. It never leaves this phone.",
        "Enable location"
    ),
    Blocked(
        "Location access is off",
        "Open Settings, tap Permissions › Location and choose \"Allow while using the app\".",
        "Open settings"
    ),
    ServicesOff(
        "Location is switched off",
        "Turn on location in your phone's settings so we can find where you are.",
        "Turn on location"
    ),
    Locating(
        "Finding you…",
        "This usually takes a few seconds. It can take longer indoors.",
        null
    ),
    Failed(
        "We couldn't find you",
        "Move nearer a window or step outside, then try again.",
        "Try again"
    ),
    Idle(
        "Find your location",
        "We need your location once to work out the Qibla and prayer times.",
        "Find my location"
    ),
    Found("Location found", "", null)
}

/** Compass readings, updated at sensor rate and read only by the Qibla screen. */
@Stable
class CompassState {
    var azimuth by mutableFloatStateOf(0f)
    var qiblaBearing by mutableFloatStateOf(0f)
    var isAligned by mutableStateOf(false)
    var needsCalibration by mutableStateOf(false)
    var isFlat by mutableStateOf(true)
}

/** Callbacks into [MainActivity] for things that need an Activity. */
class AppActions(
    val requestLocation: () -> Unit,
    val openLocationSettings: () -> Unit,
    val openAppSettings: () -> Unit,
    val onCompassVisibilityChanged: (Boolean) -> Unit,
    /** Uses a city the user picked instead of the device location. */
    val setManualLocation: (CityResult) -> Unit,
    /** Keeps the chosen city after the time zone changed, and stops asking about it. */
    val keepManualLocation: () -> Unit
) {
    /** Resolves whatever is currently stopping us from getting a location. */
    fun resolveLocation(state: LocationState) {
        when (state.status) {
            LocationStatus.Blocked -> openAppSettings()
            LocationStatus.ServicesOff -> openLocationSettings()
            LocationStatus.Locating -> Unit
            else -> requestLocation()
        }
    }
}
