package com.taslim.qiblafinder

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.taslim.qiblafinder.ui.theme.QiblaFinderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.abs

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Low-pass filter for smoothing sensor readings
    private val alpha = 0.15f // Smoothing factor (0 = max smoothing, 1 = no smoothing)
    private val smoothedAccelerometer = FloatArray(3)
    private val smoothedMagnetometer = FloatArray(3)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var vibrator: Vibrator
    private lateinit var settings: PrayerSettingsStore

    private val locationState = LocationState()
    private val compassState = CompassState()

    // Bumped when a city is picked by hand, so a GPS fix already in flight can't replace it
    private var locationRequestId = 0

    private var magneticDeclination = 0f
    private var lastAlignmentState = false
    private var isCompassScreenActive = false
    private var areCompassSensorsRegistered = false
    private var isActivityInForeground = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        locationState.permissionGranted = granted
        locationState.permissionDenied = !granted
        // After a denial the system only stops offering the dialog once it's permanently blocked
        locationState.permissionBlocked = !granted &&
            !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (granted) fetchLocation()
    }

    // Reacts when location is toggled from quick settings without leaving the app
    private val providersChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val wasEnabled = locationState.servicesEnabled
            locationState.servicesEnabled = isLocationEnabled()
            if (!wasEnabled && locationState.servicesEnabled && locationState.location == null) {
                fetchLocation()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always midnight, so the bar icons stay light whatever the system theme
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        settings = PrayerSettingsStore(this)

        // Saved location lets prayer times work instantly, before any GPS fix
        loadSavedLocation()
        locationState.permissionGranted = hasLocationPermission()
        locationState.servicesEnabled = isLocationEnabled()

        // Refresh silently on launch, unless the user chose a city by hand.
        // New users are asked for permission during onboarding.
        if (locationState.permissionGranted && !locationState.isManual) fetchLocation()

        val actions = AppActions(
            requestLocation = ::requestLocation,
            openLocationSettings = ::openLocationSettings,
            openAppSettings = ::openAppSettings,
            onCompassVisibilityChanged = ::onCompassScreenVisibilityChanged,
            setManualLocation = ::setManualLocation
        )

        setContent {
            QiblaFinderTheme {
                QiblaApp(
                    settings = settings,
                    locationState = locationState,
                    compassState = compassState,
                    actions = actions
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // A city picked by hand never triggers a GPS fix, so make sure the notification and
        // reminders are running whenever the app comes to the foreground
        if (settings.onboardingComplete) {
            if (settings.notificationsEnabled) runCatching { PrayerTimesNotificationService.startService(this) }
            if (settings.reminders.isNotEmpty()) PrayerAlarmScheduler.scheduleAllPrayerAlarms(this)
        }
        ContextCompat.registerReceiver(
            this,
            providersChangedReceiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(providersChangedReceiver)
    }

    override fun onResume() {
        super.onResume()
        isActivityInForeground = true
        updateCompassSensorRegistration()

        // The user may have changed permission or location settings while we were away
        val wasGranted = locationState.permissionGranted
        locationState.permissionGranted = hasLocationPermission()
        if (locationState.permissionGranted) locationState.permissionBlocked = false
        locationState.servicesEnabled = isLocationEnabled()

        val justGranted = !wasGranted && locationState.permissionGranted
        if (locationState.permissionGranted && locationState.servicesEnabled && !locationState.isManual &&
            (locationState.location == null || justGranted)
        ) {
            fetchLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityInForeground = false
        updateCompassSensorRegistration()
    }

    // region Location

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /** Asks for permission if needed, otherwise gets a fresh fix. */
    private fun requestLocation() {
        when {
            !hasLocationPermission() -> locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            !isLocationEnabled() -> openLocationSettings()
            else -> fetchLocation()
        }
    }

    private fun openLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        if (!hasLocationPermission() || locationState.isLocating) return
        if (!isLocationEnabled()) {
            locationState.servicesEnabled = false
            return
        }

        locationState.isLocating = true
        locationState.lastAttemptFailed = false

        // Approximate-only permission still gives a Qibla direction accurate to well under a degree
        val priority = if (hasFineLocationPermission()) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val requestId = ++locationRequestId
        fusedLocationClient.getCurrentLocation(priority, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (requestId != locationRequestId) return@addOnSuccessListener
                if (location != null) onLocationFound(location) else fetchLastKnownLocation(requestId)
            }
            .addOnFailureListener { if (requestId == locationRequestId) fetchLastKnownLocation(requestId) }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLastKnownLocation(requestId: Int) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (requestId != locationRequestId) return@addOnSuccessListener
                if (location != null) onLocationFound(location) else onLocationFailed()
            }
            .addOnFailureListener { if (requestId == locationRequestId) onLocationFailed() }
    }

    private fun onLocationFailed() {
        locationState.isLocating = false
        locationState.lastAttemptFailed = true
    }

    private fun onLocationFound(location: Location) {
        locationState.location = location
        locationState.accuracyMeters = location.accuracy
        locationState.isLocating = false
        locationState.lastAttemptFailed = false
        locationState.isManual = false
        locationState.permissionDenied = false

        updateDirectionFor(location.latitude, location.longitude, location.altitude.toFloat())
        saveLocation(location, isManual = false)
        resolvePlaceName(location.latitude, location.longitude)
        onPositionChanged()
    }

    /** Uses a city picked from search, and stops GPS from replacing it on the next launch. */
    private fun setManualLocation(city: CityResult) {
        val location = Location("manual").apply {
            latitude = city.latitude
            longitude = city.longitude
        }
        locationRequestId++
        locationState.location = location
        locationState.accuracyMeters = 0f
        locationState.isLocating = false
        locationState.lastAttemptFailed = false
        locationState.isManual = true
        locationState.placeName = city.placeName
        city.countryCode?.let { locationState.countryCode = it }

        updateDirectionFor(city.latitude, city.longitude, 0f)
        saveLocation(location, isManual = true)
        getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_PLACE_NAME, city.placeName)
            putString(KEY_COUNTRY_CODE, locationState.countryCode)
        }
        onPositionChanged()
    }

    private fun onPositionChanged() {
        if (settings.onboardingComplete && settings.reminders.isNotEmpty()) {
            PrayerAlarmScheduler.scheduleAllPrayerAlarms(this)
        }

        // Keep the notification in sync with the new position. Foreground services can't be
        // started from the background, and the fix may arrive after the user has left.
        if (settings.onboardingComplete && settings.notificationsEnabled &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            runCatching { PrayerTimesNotificationService.startService(this) }
        }
    }

    private fun loadSavedLocation() {
        val prefs = getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE)
        val latitude = prefs.getFloat(KEY_LATITUDE, 0f).toDouble()
        val longitude = prefs.getFloat(KEY_LONGITUDE, 0f).toDouble()

        if (latitude != 0.0 || longitude != 0.0) {
            locationState.location = Location("saved").apply {
                this.latitude = latitude
                this.longitude = longitude
            }
            locationState.placeName = prefs.getString(KEY_PLACE_NAME, null)
            locationState.countryCode = prefs.getString(KEY_COUNTRY_CODE, null)
            locationState.isManual = prefs.getBoolean(KEY_MANUAL, false)
            updateDirectionFor(latitude, longitude, 0f)
            if (locationState.placeName == null) resolvePlaceName(latitude, longitude)
        }
        if (locationState.countryCode == null) {
            locationState.countryCode = RegionDefaults.guessCountry(this)
        }
    }

    private fun saveLocation(location: Location, isManual: Boolean) {
        getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE).edit {
            putFloat(KEY_LATITUDE, location.latitude.toFloat())
            putFloat(KEY_LONGITUDE, location.longitude.toFloat())
            putBoolean(KEY_MANUAL, isManual)
        }
    }

    /** Turns coordinates into a friendly "City, Country" label, in the background. */
    private fun resolvePlaceName(latitude: Double, longitude: Double) {
        if (!Geocoder.isPresent()) return
        lifecycleScope.launch {
            val address = runCatching { reverseGeocode(latitude, longitude) }.getOrNull() ?: return@launch
            val city = address.locality ?: address.subAdminArea ?: address.adminArea
            val name = listOfNotNull(city, address.countryName).distinct().joinToString(", ")
                .ifBlank { null }
            locationState.placeName = name
            address.countryCode?.let { locationState.countryCode = it.uppercase() }
            getSharedPreferences(LOCATION_PREFS, Context.MODE_PRIVATE).edit {
                putString(KEY_PLACE_NAME, name)
                putString(KEY_COUNTRY_CODE, locationState.countryCode)
            }
        }
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): android.location.Address? {
        val geocoder = Geocoder(this)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<android.location.Address>) {
                        continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        }
    }

    private fun updateDirectionFor(latitude: Double, longitude: Double, altitude: Float) {
        magneticDeclination = GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            altitude,
            System.currentTimeMillis()
        ).declination
        compassState.qiblaBearing = qiblaBearing(latitude, longitude)
    }

    // endregion

    // region Compass

    private fun updateCompassSensorRegistration() {
        val shouldRegisterSensors = isActivityInForeground && isCompassScreenActive

        if (shouldRegisterSensors == areCompassSensorsRegistered) {
            return
        }

        if (shouldRegisterSensors) {
            accelerometer?.also { accel ->
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometer?.also { mag ->
                sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME)
            }
        } else {
            sensorManager.unregisterListener(this)
            compassState.isAligned = false
            lastAlignmentState = false
        }

        areCompassSensorsRegistered = shouldRegisterSensors
    }

    private fun onCompassScreenVisibilityChanged(isVisible: Boolean) {
        if (isCompassScreenActive == isVisible) {
            return
        }

        isCompassScreenActive = isVisible
        updateCompassSensorRegistration()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isCompassScreenActive) {
            return
        }

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                for (i in 0..2) {
                    smoothedAccelerometer[i] = alpha * event.values[i] + (1 - alpha) * smoothedAccelerometer[i]
                }
                System.arraycopy(smoothedAccelerometer, 0, accelerometerReading, 0, 3)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                for (i in 0..2) {
                    smoothedMagnetometer[i] = alpha * event.values[i] + (1 - alpha) * smoothedMagnetometer[i]
                }
                System.arraycopy(smoothedMagnetometer, 0, magnetometerReading, 0, 3)
            }
        }

        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )

        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            // Convert from magnetic to true north
            val azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val heading = (azimuthInDegrees + magneticDeclination + 360) % 360
            compassState.azimuth = heading

            // Hysteresis keeps the "hold flat" hint from flickering at the threshold
            val tilt = maxOf(
                abs(Math.toDegrees(orientationAngles[1].toDouble())),
                abs(Math.toDegrees(orientationAngles[2].toDouble()))
            )
            compassState.isFlat = if (compassState.isFlat) tilt < 35 else tilt < 20

            // Aligned when within ±5 degrees of the Qibla
            val difference = abs(heading - compassState.qiblaBearing)
            val normalizedDifference = if (difference > 180) 360 - difference else difference
            val newAlignmentState = normalizedDifference <= 5f

            if (newAlignmentState && !lastAlignmentState && locationState.location != null) {
                triggerAlignmentHaptic()
            }

            compassState.isAligned = newAlignmentState
            lastAlignmentState = newAlignmentState
        }
    }

    private fun triggerAlignmentHaptic() {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (!isCompassScreenActive || sensor?.type != Sensor.TYPE_MAGNETIC_FIELD) {
            return
        }
        compassState.needsCalibration = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
    }

    // endregion

    private companion object {
        const val LOCATION_PREFS = "location_data"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
        const val KEY_PLACE_NAME = "place_name"
        const val KEY_COUNTRY_CODE = "country_code"
        const val KEY_MANUAL = "is_manual"
    }
}
