package com.taslim.qiblafinder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.*

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
    
    private var currentAzimuth by mutableFloatStateOf(0f)
    private var qiblaDirection by mutableFloatStateOf(0f)
    private var userLocation by mutableStateOf<Location?>(null)
    private var isLocationPermissionGranted by mutableStateOf(false)
    private var locationAccuracy by mutableFloatStateOf(0f)
    private var magneticDeclination by mutableFloatStateOf(0f)
    private var compassAccuracy by mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
    private var showCalibrationOverlay by mutableStateOf(false)
    private var isAligned by mutableStateOf(false)
    private var lastAlignmentState by mutableStateOf(false)
    private var resumeTrigger by mutableIntStateOf(0)
    
    private lateinit var vibrator: Vibrator
    
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                isLocationPermissionGranted = true
                getUserLocation()
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                isLocationPermissionGranted = true
                getUserLocation()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        
        // Load saved location from SharedPreferences so prayer times work without live GPS
        loadSavedLocation()
        
        // Try to get fresh location (will request permission if needed)
        checkLocationPermission()
        
        // Add lifecycle observer to detect when coming back from settings
        lifecycle.addObserver(object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    // Check location when resuming
                    checkAndUpdateLocationState()
                }
            }
        })
        
        setContent {
            QiblaFinderTheme {
                val navController = rememberNavController()
                
                // Check if location services are enabled - rechecks when resumeTrigger changes
                val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val isLocationEnabled = remember(resumeTrigger) {
                    val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                    val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                    isGpsEnabled || isNetworkEnabled
                }
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // If no location data is available at all (fresh install, never saved),
                    // block the user with location screens until they grant it once
                    if (userLocation == null) {
                        if (!isLocationPermissionGranted) {
                            LocationPermissionScreen(
                                onRequestPermission = { checkLocationPermission() }
                            )
                        } else if (!isLocationEnabled) {
                            LocationRequiredScreen(
                                onOpenSettings = { openLocationSettings() }
                            )
                        } else {
                            // Permission granted and GPS on, but waiting for location fix
                            LocationLoadingScreen()
                        }
                    } else {
                        QiblaFinderApp(
                            navController = navController,
                            azimuth = currentAzimuth,
                            qiblaDirection = qiblaDirection,
                            location = userLocation,
                            locationAccuracy = locationAccuracy,
                            showCalibration = showCalibrationOverlay,
                            isAligned = isAligned,
                            onDismissCalibration = { showCalibrationOverlay = false },
                            onRequestLocation = { checkLocationPermission() },
                            isLocationPermissionGranted = isLocationPermissionGranted,
                            isLocationEnabled = isLocationEnabled,
                            onOpenLocationSettings = { openLocationSettings() }
                        )
                    }
                }
            }
        }
    }
    
    private fun openLocationSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }
    
    private fun checkAndUpdateLocationState() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        
        // Trigger recomposition
        resumeTrigger++
        
        if ((isGpsEnabled || isNetworkEnabled) && isLocationPermissionGranted && userLocation == null) {
            getUserLocation()
        }
    }
    
    private fun checkLocationPermission() {
        when {
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                isLocationPermissionGranted = true
                getUserLocation()
            }
            else -> {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }
    
    private fun loadSavedLocation() {
        val locationPrefs = getSharedPreferences("location_data", Context.MODE_PRIVATE)
        val latitude = locationPrefs.getFloat("latitude", 0f).toDouble()
        val longitude = locationPrefs.getFloat("longitude", 0f).toDouble()
        
        if (latitude != 0.0 || longitude != 0.0) {
            val savedLocation = Location("saved").apply {
                this.latitude = latitude
                this.longitude = longitude
            }
            userLocation = savedLocation
            calculateQiblaDirection(latitude, longitude)
            
            // Calculate magnetic declination from saved location
            val geoField = android.hardware.GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                0f,
                System.currentTimeMillis()
            )
            magneticDeclination = geoField.declination
        }
    }
    
    private fun saveLocationToPrefs(location: Location) {
        getSharedPreferences("location_data", Context.MODE_PRIVATE).edit().apply {
            putFloat("latitude", location.latitude.toFloat())
            putFloat("longitude", location.longitude.toFloat())
            apply()
        }
    }
    
    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        val cancellationTokenSource = CancellationTokenSource()
        
        // Use high accuracy location with improved settings
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            location?.let {
                userLocation = it
                locationAccuracy = it.accuracy
                
                // Persist location for future use and notifications
                saveLocationToPrefs(it)
                
                // Calculate magnetic declination for this location
                val geoField = android.hardware.GeomagneticField(
                    it.latitude.toFloat(),
                    it.longitude.toFloat(),
                    it.altitude.toFloat(),
                    System.currentTimeMillis()
                )
                magneticDeclination = geoField.declination
                
                calculateQiblaDirection(it.latitude, it.longitude)
            }
        }
    }
    
    private fun calculateQiblaDirection(latitude: Double, longitude: Double) {
        // Kaaba coordinates (precise)
        val kaabaLat = 21.422487
        val kaabaLng = 39.826206
        
        // Convert to radians
        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(kaabaLat)
        val deltaLonRad = Math.toRadians(kaabaLng - longitude)
        
        // Haversine formula for accurate bearing calculation
        val y = sin(deltaLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - 
                sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)
        
        // Calculate initial bearing
        val bearingRad = atan2(y, x)
        
        // Convert to degrees and normalize to 0-360
        val bearing = (Math.toDegrees(bearingRad) + 360) % 360
        
        qiblaDirection = bearing.toFloat()
    }
    
    override fun onResume() {
        super.onResume()
        // Use SENSOR_DELAY_GAME for better accuracy and responsiveness
        accelerometer?.also { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.also { mag ->
            sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME)
        }
        
        // Re-check location when app resumes (user might have enabled it in settings)
        if (isLocationPermissionGranted) {
            // Check if location services are enabled
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            
            if ((isGpsEnabled || isNetworkEnabled) && userLocation == null) {
                // Location is enabled but we don't have a fix yet - try to get it
                getUserLocation()
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // Apply low-pass filter for smoothing
                smoothedAccelerometer[0] = alpha * event.values[0] + (1 - alpha) * smoothedAccelerometer[0]
                smoothedAccelerometer[1] = alpha * event.values[1] + (1 - alpha) * smoothedAccelerometer[1]
                smoothedAccelerometer[2] = alpha * event.values[2] + (1 - alpha) * smoothedAccelerometer[2]
                System.arraycopy(smoothedAccelerometer, 0, accelerometerReading, 0, 3)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Apply low-pass filter for smoothing
                smoothedMagnetometer[0] = alpha * event.values[0] + (1 - alpha) * smoothedMagnetometer[0]
                smoothedMagnetometer[1] = alpha * event.values[1] + (1 - alpha) * smoothedMagnetometer[1]
                smoothedMagnetometer[2] = alpha * event.values[2] + (1 - alpha) * smoothedMagnetometer[2]
                System.arraycopy(smoothedMagnetometer, 0, magnetometerReading, 0, 3)
            }
        }
        
        // Calculate rotation matrix with improved accuracy
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        
        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            
            // Convert azimuth from radians to degrees
            val azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            
            // Apply magnetic declination to convert from magnetic north to true north
            val trueAzimuth = azimuthInDegrees + magneticDeclination
            
            // Normalize to 0-360 range
            currentAzimuth = (trueAzimuth + 360) % 360
            
            // Check if compass is aligned with Qibla (within ±5 degrees)
            val difference = abs(currentAzimuth - qiblaDirection)
            val normalizedDifference = if (difference > 180) 360 - difference else difference
            val newAlignmentState = normalizedDifference <= 5f
            
            // Trigger haptic feedback when alignment state changes to true
            if (newAlignmentState && !lastAlignmentState && userLocation != null) {
                triggerAlignmentHaptic()
            }
            
            isAligned = newAlignmentState
            lastAlignmentState = newAlignmentState
        }
    }
    
    private fun triggerAlignmentHaptic() {
        if (vibrator.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        sensor?.let {
            when (it.type) {
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    compassAccuracy = accuracy
                    // Show calibration overlay if accuracy is low or unreliable
                    showCalibrationOverlay = when (accuracy) {
                        SensorManager.SENSOR_STATUS_UNRELIABLE,
                        SensorManager.SENSOR_STATUS_ACCURACY_LOW -> true
                        else -> false
                    }
                }
            }
        }
    }
}

@Composable
fun QiblaCompassScreen(
    azimuth: Float,
    qiblaDirection: Float,
    location: Location?,
    locationAccuracy: Float,
    showCalibration: Boolean,
    isAligned: Boolean,
    onDismissCalibration: () -> Unit,
    onRequestLocation: () -> Unit,
    isLocationPermissionGranted: Boolean = true,
    isLocationEnabled: Boolean = true,
    onOpenLocationSettings: () -> Unit = {}
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val compassSize = (screenWidth * 0.85f).coerceAtMost(400.dp)
    
    // Calculate the angle to Qibla relative to North
    val qiblaAngle = (qiblaDirection - azimuth + 360) % 360
    
    // Pulsing animation for the Qibla indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Glow animation
    val glowAlpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1128),
                        Color(0xFF1C2541),
                        Color(0xFF2E3D5C)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Show enable-location prompt if location is not available
        if (location == null || (!isLocationPermissionGranted || !isLocationEnabled)) {
            // Stars background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val random = java.util.Random(42)
                repeat(80) {
                    val x = random.nextFloat() * size.width
                    val y = random.nextFloat() * size.height
                    val alpha = random.nextFloat() * 0.5f + 0.3f
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = random.nextFloat() * 1.5f + 0.5f,
                        center = Offset(x, y)
                    )
                }
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                // Compass icon
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    tint = Color(0xFFD4AF37).copy(alpha = 0.6f),
                    modifier = Modifier.size(80.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "QIBLA FINDER",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 6.sp,
                    color = Color(0xFFD4AF37),
                    fontFamily = FontFamily.Serif
                )
                
                Text(
                    text = "قِبْلَة",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFD4AF37).copy(alpha = 0.9f),
                    fontFamily = FontFamily.Serif
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C2541).copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = if (!isLocationPermissionGranted) Icons.Default.LocationOff else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFFFFAA00),
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = if (!isLocationPermissionGranted) "Location Permission Required" else "Location Services Required",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (!isLocationPermissionGranted) {
                                "The Qibla compass needs your location to accurately point towards the Kaaba. Please grant location permission."
                            } else {
                                "Please enable location services (GPS) so the Qibla compass can determine your position and point towards the Kaaba."
                            },
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = {
                                if (!isLocationPermissionGranted) {
                                    onRequestLocation()
                                } else {
                                    onOpenLocationSettings()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD4AF37)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (!isLocationPermissionGranted) "Grant Location Permission" else "Enable Location",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            return
        }
        // Stars background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val random = java.util.Random(42)
            repeat(80) {
                val x = random.nextFloat() * size.width
                val y = random.nextFloat() * size.height
                val alpha = random.nextFloat() * 0.5f + 0.3f
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = random.nextFloat() * 1.5f + 0.5f,
                    center = Offset(x, y)
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 8.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            // Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "QIBLA FINDER",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 6.sp,
                    color = Color(0xFFD4AF37),
                    fontFamily = FontFamily.Serif
                )
                
                Text(
                    text = "قِبْلَة",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFD4AF37).copy(alpha = 0.9f),
                    fontFamily = FontFamily.Serif
                )
            }
            
            // Compass
            Box(
                modifier = Modifier.size(compassSize),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFD4AF37).copy(alpha = glowAlpha.value * 0.3f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.minDimension / 2
                        )
                    )
                }
                
                // Compass rose
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    val radius = size.minDimension / 2
                    
                    // Outer decorative ring
                    drawCircle(
                        color = Color(0xFFD4AF37).copy(alpha = 0.3f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    
                    drawCircle(
                        color = Color(0xFFD4AF37).copy(alpha = 0.2f),
                        radius = radius - 8.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    
                    // Inner circle
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1C2541).copy(alpha = 0.9f),
                                Color(0xFF0A1128).copy(alpha = 0.95f)
                            ),
                            center = center,
                            radius = radius - 12.dp.toPx()
                        ),
                        radius = radius - 12.dp.toPx(),
                        center = center
                    )
                    
                    // Rotate based on azimuth
                    rotate(-azimuth, center) {
                        // Cardinal points
                        val cardinals = listOf("N", "E", "S", "W")
                        val cardinalAngles = listOf(0f, 90f, 180f, 270f)
                        
                        cardinalAngles.forEachIndexed { index, angle ->
                            val rad = Math.toRadians(angle.toDouble())
                            val x = center.x + (radius - 40.dp.toPx()) * sin(rad).toFloat()
                            val y = center.y - (radius - 40.dp.toPx()) * cos(rad).toFloat()
                            
                            // Cardinal marker lines
                            val startX = center.x + (radius - 20.dp.toPx()) * sin(rad).toFloat()
                            val startY = center.y - (radius - 20.dp.toPx()) * cos(rad).toFloat()
                            val endX = center.x + (radius - 35.dp.toPx()) * sin(rad).toFloat()
                            val endY = center.y - (radius - 35.dp.toPx()) * cos(rad).toFloat()
                            
                            drawLine(
                                color = if (index == 0) Color(0xFFD4AF37) else Color(0xFFD4AF37).copy(alpha = 0.6f),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = if (index == 0) 3.dp.toPx() else 2.dp.toPx()
                            )
                        }
                        
                        // Degree markers
                        for (i in 0 until 360 step 10) {
                            if (i % 90 != 0) {
                                val rad = Math.toRadians(i.toDouble())
                                val startRadius = if (i % 30 == 0) radius - 20.dp.toPx() else radius - 15.dp.toPx()
                                val endRadius = radius - 12.dp.toPx()
                                
                                val startX = center.x + startRadius * sin(rad).toFloat()
                                val startY = center.y - startRadius * cos(rad).toFloat()
                                val endX = center.x + endRadius * sin(rad).toFloat()
                                val endY = center.y - endRadius * cos(rad).toFloat()
                                
                                drawLine(
                                    color = Color(0xFFD4AF37).copy(alpha = if (i % 30 == 0) 0.5f else 0.3f),
                                    start = Offset(startX, startY),
                                    end = Offset(endX, endY),
                                    strokeWidth = if (i % 30 == 0) 1.5.dp.toPx() else 1.dp.toPx()
                                )
                            }
                        }
                    }
                    
                    // Qibla indicator (fixed to Qibla direction)
                    rotate(-azimuth + qiblaDirection, center) {
                        val qiblaRad = Math.toRadians(0.0)
                        val qiblaX = center.x + (radius - 60.dp.toPx()) * sin(qiblaRad).toFloat()
                        val qiblaY = center.y - (radius - 60.dp.toPx()) * cos(qiblaRad).toFloat()
                        
                        // Alignment glow (green circle when aligned)
                        if (isAligned) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF00FF88).copy(alpha = 0.4f),
                                        Color(0xFF00FF88).copy(alpha = 0.2f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = radius * 1.1f
                                )
                            )
                        }
                        
                        // Qibla arrow with glow
                        val path = Path().apply {
                            moveTo(center.x, center.y - 15.dp.toPx())
                            lineTo(center.x - 15.dp.toPx(), center.y + 15.dp.toPx())
                            lineTo(center.x, center.y + 5.dp.toPx())
                            lineTo(center.x + 15.dp.toPx(), center.y + 15.dp.toPx())
                            close()
                        }
                        
                        // Glow effect - changes color when aligned
                        val glowColor = if (isAligned) Color(0xFF00FF88) else Color(0xFF00D9FF)
                        drawPath(
                            path = path,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = glowAlpha.value),
                                    glowColor.copy(alpha = 0f)
                                ),
                                center = center,
                                radius = 40.dp.toPx()
                            )
                        )
                        
                        val arrowColor = if (isAligned) Color(0xFF00FF88) else Color(0xFF00D9FF)
                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    arrowColor,
                                    arrowColor.copy(alpha = 0.8f)
                                )
                            )
                        )
                        
                        // Line to edge
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    arrowColor,
                                    arrowColor.copy(alpha = 0.3f)
                                ),
                                start = center,
                                end = Offset(qiblaX, qiblaY)
                            ),
                            start = center,
                            end = Offset(qiblaX, qiblaY),
                            strokeWidth = 2.dp.toPx()
                        )
                        
                        // Small Kaaba icon at the edge of the compass
                        val kaabaSize = 14.dp.toPx()
                        val kaabaX = center.x
                        val kaabaY = center.y - (radius - 30.dp.toPx())
                        val kaabaColor = if (isAligned) Color(0xFF00FF88) else Color(0xFFD4AF37)
                        
                        // Kaaba glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    kaabaColor.copy(alpha = glowAlpha.value * 0.5f),
                                    Color.Transparent
                                ),
                                center = Offset(kaabaX, kaabaY),
                                radius = kaabaSize * 2f
                            ),
                            center = Offset(kaabaX, kaabaY),
                            radius = kaabaSize * 2f
                        )
                        
                        // Kaaba body (cube shape)
                        val kaabaPath = Path().apply {
                            // Main cube face
                            moveTo(kaabaX - kaabaSize * 0.5f, kaabaY - kaabaSize * 0.3f)
                            lineTo(kaabaX + kaabaSize * 0.5f, kaabaY - kaabaSize * 0.3f)
                            lineTo(kaabaX + kaabaSize * 0.5f, kaabaY + kaabaSize * 0.5f)
                            lineTo(kaabaX - kaabaSize * 0.5f, kaabaY + kaabaSize * 0.5f)
                            close()
                        }
                        drawPath(
                            path = kaabaPath,
                            color = kaabaColor
                        )
                        
                        // Kaaba top (slight roof line)
                        val roofPath = Path().apply {
                            moveTo(kaabaX - kaabaSize * 0.55f, kaabaY - kaabaSize * 0.3f)
                            lineTo(kaabaX, kaabaY - kaabaSize * 0.5f)
                            lineTo(kaabaX + kaabaSize * 0.55f, kaabaY - kaabaSize * 0.3f)
                            close()
                        }
                        drawPath(
                            path = roofPath,
                            color = kaabaColor.copy(alpha = 0.8f)
                        )
                        
                        // Kiswah band (horizontal gold stripe)
                        drawLine(
                            color = Color(0xFF0A1128),
                            start = Offset(kaabaX - kaabaSize * 0.5f, kaabaY + kaabaSize * 0.05f),
                            end = Offset(kaabaX + kaabaSize * 0.5f, kaabaY + kaabaSize * 0.05f),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        
                        // Door detail (small rectangle)
                        drawRect(
                            color = Color(0xFF0A1128).copy(alpha = 0.7f),
                            topLeft = Offset(kaabaX - kaabaSize * 0.1f, kaabaY + kaabaSize * 0.15f),
                            size = Size(kaabaSize * 0.2f, kaabaSize * 0.35f)
                        )
                    }
                    
                    // Center dot
                    drawCircle(
                        color = Color(0xFFD4AF37),
                        radius = 6.dp.toPx(),
                        center = center
                    )
                    
                    drawCircle(
                        color = Color(0xFF0A1128),
                        radius = 4.dp.toPx(),
                        center = center
                    )
                }
            }
            
            // Information
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                // Direction degree
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C2541).copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "QIBLA DIRECTION",
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                            color = Color(0xFFD4AF37).copy(alpha = 0.7f),
                            fontWeight = FontWeight.Light
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "${qiblaAngle.toInt()}°",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00D9FF),
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = when {
                                qiblaAngle in 350f..360f || qiblaAngle in 0f..10f -> "Straight Ahead"
                                qiblaAngle in 10f..80f -> "Turn Right"
                                qiblaAngle in 80f..100f -> "Turn Right 90°"
                                qiblaAngle in 100f..170f -> "Turn Right More"
                                qiblaAngle in 170f..190f -> "Turn Around"
                                qiblaAngle in 190f..260f -> "Turn Left More"
                                qiblaAngle in 260f..280f -> "Turn Left 90°"
                                else -> "Turn Left"
                            },
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                    }
                }
                
                // Location info
                location?.let { loc ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1C2541).copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "LATITUDE",
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFFD4AF37).copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "%.4f°".format(loc.latitude),
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1C2541).copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Text(
                                    text = "LONGITUDE",
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    color = Color(0xFFD4AF37).copy(alpha = 0.6f)
                                )
                                Text(
                                    text = "%.4f°".format(loc.longitude),
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    
                    // GPS Accuracy Indicator
                    if (locationAccuracy > 0) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF1C2541).copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Accuracy indicator dot
                                val accuracyColor = when {
                                    locationAccuracy <= 10f -> Color(0xFF00FF88) // Excellent
                                    locationAccuracy <= 25f -> Color(0xFF00D9FF) // Good
                                    locationAccuracy <= 50f -> Color(0xFFFFAA00) // Fair
                                    else -> Color(0xFFFF4444) // Poor
                                }
                                
                                Canvas(modifier = Modifier.size(8.dp)) {
                                    drawCircle(color = accuracyColor)
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Text(
                                    text = "GPS ACCURACY: ±%.1fm".format(locationAccuracy),
                                    fontSize = 11.sp,
                                    letterSpacing = 0.5.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Calibration Overlay
        if (showCalibration) {
            CalibrationOverlay(onDismiss = onDismissCalibration)
        }
    }
}

@Composable
fun QiblaFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFD4AF37),
            secondary = Color(0xFF00D9FF),
            background = Color(0xFF0A1128),
            surface = Color(0xFF1C2541)
        ),
        content = content
    )
}

// Calibration Overlay Composables
@Composable
fun CalibrationOverlay(onDismiss: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        alpha.animateTo(targetValue = 1f, animationSpec = tween(300, easing = EaseOut))
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1128).copy(alpha = alpha.value * 0.95f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale = infiniteTransition.animateFloat(
                initialValue = 1f, targetValue = 1.1f,
                animationSpec = infiniteRepeatable(animation = tween(800, easing = EaseInOut), repeatMode = RepeatMode.Reverse),
                label = "pulse"
            )
            
            Canvas(modifier = Modifier.size(80.dp)) {
                drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFFFFAA00).copy(alpha = 0.4f), Color.Transparent)), radius = size.minDimension / 2 * pulseScale.value)
                val path = Path().apply { moveTo(size.width / 2, size.height * 0.2f); lineTo(size.width * 0.2f, size.height * 0.8f); lineTo(size.width * 0.8f, size.height * 0.8f); close() }
                drawPath(path = path, color = Color(0xFFFFAA00), style = Stroke(width = 3.dp.toPx()))
                drawLine(color = Color(0xFFFFAA00), start = Offset(size.width / 2, size.height * 0.4f), end = Offset(size.width / 2, size.height * 0.6f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color = Color(0xFFFFAA00), radius = 3.dp.toPx(), center = Offset(size.width / 2, size.height * 0.7f))
            }
            
            Text(text = "COMPASS CALIBRATION NEEDED", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = Color(0xFFFFAA00), textAlign = TextAlign.Center)
            Text(text = "Low compass accuracy detected", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            
            Figure8Animation()
            
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541).copy(alpha = 0.8f)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = "How to Calibrate:", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37), letterSpacing = 1.sp)
                    CalibrationStep("1", "Hold your device and move it away from your body")
                    CalibrationStep("2", "Tilt and rotate the device in a figure-8 pattern")
                    CalibrationStep("3", "Repeat the motion 3-4 times in different orientations")
                    CalibrationStep("4", "Move away from metal objects and electronics")
                }
            }
            
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37).copy(alpha = 0.3f)), modifier = Modifier.fillMaxWidth()) {
                Text(text = "I'VE CALIBRATED", letterSpacing = 2.sp, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            
            Text(text = "Tap anywhere to dismiss", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), letterSpacing = 1.sp)
        }
    }
}

@Composable
fun Figure8Animation() {
    val infiniteTransition = rememberInfiniteTransition(label = "figure8")
    val progress = infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(3000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "progress")
    
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2541).copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxWidth(0.8f).height(150.dp)) {
                val centerY = size.height / 2; val width = size.width; val lobeRadius = size.height / 3
                val path = Path(); var firstPoint = true
                for (t in 0..360 step 2) {
                    val radians = Math.toRadians(t.toDouble()); val scale = 2.0 / (3.0 - cos(2 * radians))
                    val x = (width / 2 + lobeRadius * scale * cos(radians)).toFloat()
                    val y = (centerY + lobeRadius * scale * sin(radians) * cos(radians)).toFloat()
                    if (firstPoint) { path.moveTo(x, y); firstPoint = false } else { path.lineTo(x, y) }
                }
                path.close()
                drawPath(path = path, color = Color(0xFFD4AF37).copy(alpha = 0.3f), style = Stroke(width = 2.dp.toPx()))
                
                val t = progress.value * 360; val radians = Math.toRadians(t.toDouble()); val scale = 2.0 / (3.0 - cos(2 * radians))
                val deviceX = (width / 2 + lobeRadius * scale * cos(radians)).toFloat()
                val deviceY = (centerY + lobeRadius * scale * sin(radians) * cos(radians)).toFloat()
                
                for (i in 1..10) {
                    val trailT = (progress.value - i * 0.02f) * 360; val trailRadians = Math.toRadians(trailT.toDouble()); val trailScale = 2.0 / (3.0 - cos(2 * trailRadians))
                    val trailX = (width / 2 + lobeRadius * trailScale * cos(trailRadians)).toFloat()
                    val trailY = (centerY + lobeRadius * trailScale * sin(trailRadians) * cos(trailRadians)).toFloat()
                    drawCircle(color = Color(0xFF00D9FF).copy(alpha = 0.1f * (11 - i) / 10), radius = 4.dp.toPx() * (11 - i) / 10, center = Offset(trailX, trailY))
                }
                
                val devicePath = Path().apply {
                    val phoneWidth = 20.dp.toPx(); val phoneHeight = 30.dp.toPx()
                    addRoundRect(RoundRect(rect = Rect(left = deviceX - phoneWidth / 2, top = deviceY - phoneHeight / 2, right = deviceX + phoneWidth / 2, bottom = deviceY + phoneHeight / 2), cornerRadius = CornerRadius(4.dp.toPx())))
                }
                drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFF00D9FF).copy(alpha = 0.6f), Color.Transparent), center = Offset(deviceX, deviceY), radius = 20.dp.toPx()), radius = 20.dp.toPx(), center = Offset(deviceX, deviceY))
                drawPath(path = devicePath, brush = Brush.linearGradient(colors = listOf(Color(0xFF00D9FF), Color(0xFF00A8CC))))
                drawLine(color = Color.White, start = Offset(deviceX - 6.dp.toPx(), deviceY + 10.dp.toPx()), end = Offset(deviceX + 6.dp.toPx(), deviceY + 10.dp.toPx()), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
            }
            Text(text = "↻", fontSize = 40.sp, color = Color(0xFFD4AF37).copy(alpha = 0.5f), modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
fun CalibrationStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(28.dp).background(color = Color(0xFFD4AF37).copy(alpha = 0.3f), shape = MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
            Text(text = number, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD4AF37))
        }
        Text(text = text, fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f), lineHeight = 20.sp, modifier = Modifier.weight(1f))
    }
}

// App Container with Bottom Navigation
@Composable
fun QiblaFinderApp(
    navController: NavHostController,
    azimuth: Float,
    qiblaDirection: Float,
    location: Location?,
    locationAccuracy: Float,
    showCalibration: Boolean,
    isAligned: Boolean,
    onDismissCalibration: () -> Unit,
    onRequestLocation: () -> Unit,
    isLocationPermissionGranted: Boolean,
    isLocationEnabled: Boolean,
    onOpenLocationSettings: () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "prayerTimes"
    
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            BottomNavigationBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "prayerTimes",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("prayerTimes") {
                PrayerTimesScreen(
                    location = location,
                    onBack = { navController.navigate("compass") }
                )
            }
            
            composable("compass") {
                QiblaCompassScreen(
                    azimuth = azimuth,
                    qiblaDirection = qiblaDirection,
                    location = location,
                    locationAccuracy = locationAccuracy,
                    showCalibration = showCalibration,
                    isAligned = isAligned,
                    onDismissCalibration = onDismissCalibration,
                    onRequestLocation = onRequestLocation,
                    isLocationPermissionGranted = isLocationPermissionGranted,
                    isLocationEnabled = isLocationEnabled,
                    onOpenLocationSettings = onOpenLocationSettings
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    // Gradient background matching the app theme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1C2541).copy(alpha = 0.95f),
                        Color(0xFF0A1128).copy(alpha = 0.98f)
                    )
                )
            )
    ) {
        // Top border glow
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFD4AF37).copy(alpha = 0.4f),
                        Color.Transparent
                    )
                )
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Prayer Times Tab (first)
            NavigationItem(
                icon = Icons.Default.Schedule,
                label = "Prayer Times",
                isSelected = currentRoute == "prayerTimes",
                onClick = { onNavigate("prayerTimes") }
            )
            
            // Qibla Tab (second)
            NavigationItem(
                icon = Icons.Default.Explore,
                label = "Qibla",
                isSelected = currentRoute == "compass",
                onClick = { onNavigate("compass") }
            )
        }
    }
}

@Composable
fun NavigationItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "navGlow")
    val glowAlpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    val iconColor = if (isSelected) Color(0xFF00D9FF) else Color(0xFFD4AF37).copy(alpha = 0.5f)
    val labelColor = if (isSelected) Color(0xFF00D9FF) else Color.White.copy(alpha = 0.5f)
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            // Glow effect for selected item
            if (isSelected) {
                Canvas(modifier = Modifier.size(40.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00D9FF).copy(alpha = glowAlpha.value * 0.3f),
                                Color.Transparent
                            )
                        )
                    )
                }
            }
            
            // Icon background
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (isSelected) {
                            Color(0xFF00D9FF).copy(alpha = 0.15f)
                        } else {
                            Color(0xFF1C2541).copy(alpha = 0.3f)
                        },
                        shape = MaterialTheme.shapes.small
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Label
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = labelColor,
            letterSpacing = 0.5.sp
        )
    }
}

// Location Permission Screen
@Composable
fun LocationPermissionScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1128),
                        Color(0xFF1C2541),
                        Color(0xFF2E3D5C)
                    )
                )
            )
    ) {
        // Stars background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val random = java.util.Random(42)
            repeat(80) {
                val x = random.nextFloat() * size.width
                val y = random.nextFloat() * size.height
                val alpha = random.nextFloat() * 0.5f + 0.3f
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = random.nextFloat() * 1.5f + 0.5f,
                    center = Offset(x, y)
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // App icon/logo area
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        color = Color(0xFFD4AF37).copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.large
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = "Qibla Finder",
                    tint = Color(0xFFD4AF37),
                    modifier = Modifier.size(56.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "QIBLA FINDER",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 6.sp,
                    color = Color(0xFFD4AF37),
                    fontFamily = FontFamily.Serif
                )
                
                Text(
                    text = "قِبْلَة",
                    fontSize = 24.sp,
                    color = Color(0xFFD4AF37).copy(alpha = 0.9f),
                    fontFamily = FontFamily.Serif
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Permission explanation
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1C2541).copy(alpha = 0.8f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF00D9FF),
                        modifier = Modifier.size(40.dp)
                    )
                    
                    Text(
                        text = "Location Permission Required",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        letterSpacing = 1.sp
                    )
                    
                    Text(
                        text = "This app requires your location to:",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        PermissionReason(
                            icon = Icons.Default.Explore,
                            text = "Calculate accurate Qibla direction"
                        )
                        
                        PermissionReason(
                            icon = Icons.Default.Schedule,
                            text = "Determine precise prayer times"
                        )
                        
                        PermissionReason(
                            icon = Icons.Default.MyLocation,
                            text = "Provide location-specific guidance"
                        )
                    }
                    
                    Text(
                        text = "Without location, the app cannot function correctly and may provide incorrect directions.",
                        fontSize = 12.sp,
                        color = Color(0xFFFFAA00).copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Grant permission button
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00D9FF)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "GRANT LOCATION PERMISSION",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// Location Required Screen (Permission granted but GPS off)
@Composable
fun LocationRequiredScreen(
    onOpenSettings: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale = infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1128),
                        Color(0xFF1C2541),
                        Color(0xFF2E3D5C)
                    )
                )
            )
    ) {
        // Stars background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val random = java.util.Random(42)
            repeat(80) {
                val x = random.nextFloat() * size.width
                val y = random.nextFloat() * size.height
                val alpha = random.nextFloat() * 0.5f + 0.3f
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = random.nextFloat() * 1.5f + 0.5f,
                    center = Offset(x, y)
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Pulsing location icon
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFAA00).copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        ),
                        radius = size.minDimension / 2 * pulseScale.value
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.LocationOff,
                    contentDescription = "Location Disabled",
                    tint = Color(0xFFFFAA00),
                    modifier = Modifier.size(60.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "LOCATION SERVICES DISABLED",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = Color(0xFFFFAA00),
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "تم تعطيل خدمات الموقع",
                    fontSize = 16.sp,
                    color = Color(0xFFFFAA00).copy(alpha = 0.8f),
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Explanation card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1C2541).copy(alpha = 0.8f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Why Location is Essential",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD4AF37),
                        letterSpacing = 1.sp
                    )
                    
                    Text(
                        text = "This app requires GPS to calculate accurate Islamic prayer information:",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LocationBenefit(
                            icon = Icons.Default.MyLocation,
                            title = "Precise Qibla Direction",
                            description = "Without your location, we cannot determine which direction to face for prayer."
                        )
                        
                        LocationBenefit(
                            icon = Icons.Default.Schedule,
                            title = "Accurate Prayer Times",
                            description = "Prayer times vary by location. GPS ensures times are correct for your area."
                        )
                        
                        LocationBenefit(
                            icon = Icons.Default.Warning,
                            title = "Avoid Misguidance",
                            description = "Using the app without location may provide incorrect directions and times."
                        )
                    }
                    
                    Divider(
                        color = Color(0xFFD4AF37).copy(alpha = 0.3f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    Text(
                        text = "How to Enable Location:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00D9FF)
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LocationStep(number = "1", text = "Tap the button below to open Settings")
                        LocationStep(number = "2", text = "Turn ON Location Services")
                        LocationStep(number = "3", text = "Return to this app")
                        LocationStep(number = "4", text = "App will automatically detect location")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Open settings button
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00D9FF)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "OPEN LOCATION SETTINGS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Help text
            Text(
                text = "The app will work automatically once location is enabled",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun PermissionReason(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF00D9FF),
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.9f),
            lineHeight = 20.sp
        )
    }
}

@Composable
fun LocationBenefit(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = Color(0xFF00D9FF).copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF00D9FF),
                modifier = Modifier.size(24.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                letterSpacing = 0.5.sp
            )
            
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun LocationStep(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = Color(0xFFD4AF37).copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD4AF37)
            )
        }
        
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.9f),
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// Location Loading Screen (GPS acquiring signal)
@Composable
fun LocationLoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A1128),
                        Color(0xFF1C2541),
                        Color(0xFF2E3D5C)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Stars background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val random = java.util.Random(42)
            repeat(80) {
                val x = random.nextFloat() * size.width
                val y = random.nextFloat() * size.height
                val alpha = random.nextFloat() * 0.5f + 0.3f
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = random.nextFloat() * 1.5f + 0.5f,
                    center = Offset(x, y)
                )
            }
        }
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Rotating compass icon
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00D9FF).copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Finding Location",
                    tint = Color(0xFF00D9FF),
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer(rotationZ = rotation.value)
                )
            }
            
            Text(
                text = "Acquiring GPS Signal...",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF00D9FF),
                letterSpacing = 1.sp
            )
            
            Text(
                text = "جارٍ الحصول على الموقع",
                fontSize = 16.sp,
                color = Color(0xFF00D9FF).copy(alpha = 0.8f),
                fontFamily = FontFamily.Serif
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1C2541).copy(alpha = 0.6f)
                ),
                modifier = Modifier.padding(horizontal = 48.dp)
            ) {
                Text(
                    text = "Please wait while we determine your precise location...",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}