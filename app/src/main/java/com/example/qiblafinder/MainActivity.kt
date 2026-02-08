package com.example.qiblafinder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
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
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    private var currentAzimuth by mutableFloatStateOf(0f)
    private var qiblaDirection by mutableFloatStateOf(0f)
    private var userLocation by mutableStateOf<Location?>(null)
    private var isLocationPermissionGranted by mutableStateOf(false)
    
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
        
        checkLocationPermission()
        
        setContent {
            QiblaFinderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QiblaCompassScreen(
                        azimuth = currentAzimuth,
                        qiblaDirection = qiblaDirection,
                        location = userLocation,
                        onRequestLocation = { checkLocationPermission() }
                    )
                }
            }
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
    
    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        val cancellationTokenSource = CancellationTokenSource()
        
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location: Location? ->
            location?.let {
                userLocation = it
                calculateQiblaDirection(it.latitude, it.longitude)
            }
        }
    }
    
    private fun calculateQiblaDirection(latitude: Double, longitude: Double) {
        // Kaaba coordinates
        val kaabaLat = 21.4225
        val kaabaLng = 39.8262
        
        val latDiff = Math.toRadians(kaabaLat - latitude)
        val lngDiff = Math.toRadians(kaabaLng - longitude)
        
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(kaabaLat)
        
        val y = sin(lngDiff) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(lngDiff)
        
        var bearing = Math.toDegrees(atan2(y, x))
        qiblaDirection = ((bearing + 360) % 360).toFloat()
    }
    
    override fun onResume() {
        super.onResume()
        accelerometer?.also { accel ->
            sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.also { mag ->
            sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
        }
    }
    
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
        }
        
        SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelerometerReading,
            magnetometerReading
        )
        
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        
        val azimuthInDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        currentAzimuth = (azimuthInDegrees + 360) % 360
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for this implementation
    }
}

@Composable
fun QiblaCompassScreen(
    azimuth: Float,
    qiblaDirection: Float,
    location: Location?,
    onRequestLocation: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
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
                        
                        // Qibla arrow with glow
                        val path = Path().apply {
                            moveTo(center.x, center.y - 15.dp.toPx())
                            lineTo(center.x - 15.dp.toPx(), center.y + 15.dp.toPx())
                            lineTo(center.x, center.y + 5.dp.toPx())
                            lineTo(center.x + 15.dp.toPx(), center.y + 15.dp.toPx())
                            close()
                        }
                        
                        // Glow effect
                        drawPath(
                            path = path,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF00D9FF).copy(alpha = glowAlpha.value),
                                    Color(0xFF00D9FF).copy(alpha = 0f)
                                ),
                                center = center,
                                radius = 40.dp.toPx()
                            )
                        )
                        
                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00D9FF),
                                    Color(0xFF00A8CC)
                                )
                            )
                        )
                        
                        // Line to edge
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00D9FF),
                                    Color(0xFF00D9FF).copy(alpha = 0.3f)
                                ),
                                start = center,
                                end = Offset(qiblaX, qiblaY)
                            ),
                            start = center,
                            end = Offset(qiblaX, qiblaY),
                            strokeWidth = 2.dp.toPx()
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
                } ?: run {
                    Button(
                        onClick = onRequestLocation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD4AF37).copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ENABLE LOCATION",
                            letterSpacing = 2.sp,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
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