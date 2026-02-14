package com.taslim.qiblafinder

import android.content.Context
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batoulapps.adhan.*
import com.batoulapps.adhan.data.DateComponents
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PrayerTimesScreen(
    location: Location?,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    
    val sharedPreferences = remember {
        context.getSharedPreferences("prayer_settings", Context.MODE_PRIVATE)
    }
    
    // Check if this is first time
    var showFirstTimeSetup by remember {
        mutableStateOf(!sharedPreferences.contains("madhab_preference"))
    }
    
    // Load saved preferences or use defaults
    var selectedMadhab by remember {
        mutableStateOf(
            Madhab.valueOf(
                sharedPreferences.getString("madhab_preference", Madhab.SHAFI.name) ?: Madhab.SHAFI.name
            )
        )
    }
    
    var selectedCalculationMethod by remember {
        mutableStateOf(
            try {
                when (sharedPreferences.getString("method_preference", "MUSLIM_WORLD_LEAGUE")) {
                    "MUSLIM_WORLD_LEAGUE" -> CalculationMethod.MUSLIM_WORLD_LEAGUE
                    "NORTH_AMERICA" -> CalculationMethod.NORTH_AMERICA
                    "EGYPTIAN" -> CalculationMethod.EGYPTIAN
                    "UMM_AL_QURA" -> CalculationMethod.UMM_AL_QURA
                    "KARACHI" -> CalculationMethod.KARACHI
                    "MOON_SIGHTING_COMMITTEE" -> CalculationMethod.MOON_SIGHTING_COMMITTEE
                    else -> CalculationMethod.MUSLIM_WORLD_LEAGUE
                }
            } catch (e: Exception) {
                CalculationMethod.MUSLIM_WORLD_LEAGUE
            }
        )
    }
    
    var showSettings by remember { mutableStateOf(false) }
    
    // Notification permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, start notification service
            PrayerTimesNotificationService.startService(context)
        } else {
            // Permission denied, still start service (will work on older Android versions)
            PrayerTimesNotificationService.startService(context)
        }
    }
    
    // Save preferences function
    fun savePreferences() {
        sharedPreferences.edit().apply {
            putString("madhab_preference", selectedMadhab.name)
            putString("method_preference", when (selectedCalculationMethod) {
                CalculationMethod.MUSLIM_WORLD_LEAGUE -> "MUSLIM_WORLD_LEAGUE"
                CalculationMethod.NORTH_AMERICA -> "NORTH_AMERICA"
                CalculationMethod.EGYPTIAN -> "EGYPTIAN"
                CalculationMethod.UMM_AL_QURA -> "UMM_AL_QURA"
                CalculationMethod.KARACHI -> "KARACHI"
                CalculationMethod.MOON_SIGHTING_COMMITTEE -> "MOON_SIGHTING_COMMITTEE"
                else -> "MUSLIM_WORLD_LEAGUE"
            })
            apply()
        }
        
        // Save location data for notification
        location?.let { loc ->
            context.getSharedPreferences("location_data", Context.MODE_PRIVATE).edit().apply {
                putFloat("latitude", loc.latitude.toFloat())
                putFloat("longitude", loc.longitude.toFloat())
                apply()
            }
            
            // Request notification permission and start service
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ requires runtime permission
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    // Already granted
                    PrayerTimesNotificationService.startService(context)
                } else {
                    // Request permission
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            } else {
                // Android 12 and below - no permission needed
                PrayerTimesNotificationService.startService(context)
            }
        }
    }
    
    val prayerTimes = remember(location, selectedMadhab, selectedCalculationMethod) {
        location?.let { calculatePrayerTimes(it, selectedMadhab, selectedCalculationMethod) }
    }
    
    val currentPrayer = remember(prayerTimes) {
        prayerTimes?.currentPrayer()
    }
    
    val nextPrayer = remember(prayerTimes) {
        prayerTimes?.nextPrayer()
    }
    
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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(48.dp))
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "PRAYER TIMES",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 5.sp,
                        color = Color(0xFFD4AF37),
                        fontFamily = FontFamily.Serif
                    )
                    
                    Text(
                        text = "أوقات الصلاة",
                        fontSize = 20.sp,
                        color = Color(0xFFD4AF37).copy(alpha = 0.8f),
                        fontFamily = FontFamily.Serif
                    )
                }
                
                IconButton(onClick = { showSettings = !showSettings }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color(0xFFD4AF37)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Current Date
            val dateFormat = remember { SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault()) }
            val islamicDate = remember {
                prayerTimes?.let {
                    val hijriCalendar = Calendar.getInstance()
                    // Simple approximation for Islamic date display
                    val gregorianDate = Date()
                    dateFormat.format(gregorianDate)
                } ?: ""
            }
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1C2541).copy(alpha = 0.6f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = dateFormat.format(Date()),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        letterSpacing = 1.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Settings Panel
            if (showSettings) {
                SettingsPanel(
                    selectedMadhab = selectedMadhab,
                    selectedCalculationMethod = selectedCalculationMethod,
                    onMadhabChange = { 
                        selectedMadhab = it
                        savePreferences()
                    },
                    onCalculationMethodChange = { 
                        selectedCalculationMethod = it
                        savePreferences()
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Current Prayer Indicator
            if (prayerTimes != null && currentPrayer != null) {
                CurrentPrayerCard(
                    currentPrayer = currentPrayer,
                    nextPrayer = nextPrayer,
                    prayerTimes = prayerTimes
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // Prayer Times List
            if (prayerTimes != null) {
                PrayerTimesList(prayerTimes = prayerTimes, currentPrayer = currentPrayer)
            } else {
                // No location
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C2541).copy(alpha = 0.8f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = Color(0xFFFFAA00),
                            modifier = Modifier.size(48.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Location Required",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Please enable location services to calculate accurate prayer times for your area.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        // First-time setup dialog
        if (showFirstTimeSetup) {
            FirstTimeSetupDialog(
                onComplete = { madhab, method ->
                    selectedMadhab = madhab
                    selectedCalculationMethod = method
                    savePreferences()
                    showFirstTimeSetup = false
                }
            )
        }
    }
}

@Composable
fun CurrentPrayerCard(
    currentPrayer: Prayer?,
    nextPrayer: Prayer?,
    prayerTimes: PrayerTimes
) {
    val infiniteTransition = rememberInfiniteTransition(label = "currentPrayer")
    val glowAlpha = infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Current Prayer Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C2541).copy(alpha = 0.9f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Glow effect
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00D9FF).copy(alpha = glowAlpha.value * 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width / 2
                        )
                    )
                }
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 24.dp)
                ) {
                    Text(
                        text = "CURRENT PRAYER",
                        fontSize = 11.sp,
                        letterSpacing = 3.sp,
                        color = Color(0xFFD4AF37).copy(alpha = 0.7f),
                        fontWeight = FontWeight.Light
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = currentPrayer?.name ?: "Unknown",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D9FF),
                        letterSpacing = 2.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = getPrayerArabicName(currentPrayer),
                        fontSize = 24.sp,
                        color = Color(0xFF00D9FF).copy(alpha = 0.8f),
                        fontFamily = FontFamily.Serif
                    )
                }
            }
        }
        
        // Next Prayer Card (separate)
        if (nextPrayer != null) {
            val nextPrayerTime = getPrayerTime(prayerTimes, nextPrayer)
            val timeUntil = calculateTimeUntil(nextPrayerTime)
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1C2541).copy(alpha = 0.7f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "NEXT PRAYER",
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            color = Color(0xFFD4AF37).copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = nextPrayer.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "IN",
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            color = Color(0xFFD4AF37).copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = timeUntil,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD4AF37),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrayerTimesList(
    prayerTimes: PrayerTimes,
    currentPrayer: Prayer?
) {
    val prayers = listOf(
        Prayer.FAJR to prayerTimes.fajr,
        Prayer.SUNRISE to prayerTimes.sunrise,
        Prayer.DHUHR to prayerTimes.dhuhr,
        Prayer.ASR to prayerTimes.asr,
        Prayer.MAGHRIB to prayerTimes.maghrib,
        Prayer.ISHA to prayerTimes.isha
    )
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        prayers.forEach { (prayer, time) ->
            PrayerTimeItem(
                prayer = prayer,
                time = time,
                isCurrent = prayer == currentPrayer
            )
        }
    }
}

@Composable
fun PrayerTimeItem(
    prayer: Prayer,
    time: Date,
    isCurrent: Boolean
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val backgroundColor = if (isCurrent) {
        Color(0xFF00D9FF).copy(alpha = 0.15f)
    } else {
        Color(0xFF1C2541).copy(alpha = 0.6f)
    }
    
    val borderColor = if (isCurrent) {
        Color(0xFF00D9FF).copy(alpha = 0.6f)
    } else {
        Color.Transparent
    }
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = if (isCurrent) BorderStroke(2.dp, borderColor) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prayer Icon
                PrayerIcon(prayer = prayer, isCurrent = isCurrent)
                
                Column {
                    Text(
                        text = prayer.name,
                        fontSize = 18.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) Color(0xFF00D9FF) else Color.White,
                        letterSpacing = 1.sp
                    )
                    
                    Text(
                        text = getPrayerArabicName(prayer),
                        fontSize = 14.sp,
                        color = if (isCurrent) Color(0xFF00D9FF).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Serif
                    )
                }
            }
            
            Text(
                text = timeFormat.format(time),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) Color(0xFF00D9FF) else Color(0xFFD4AF37),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun PrayerIcon(prayer: Prayer, isCurrent: Boolean) {
    val iconColor = if (isCurrent) Color(0xFF00D9FF) else Color(0xFFD4AF37)
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                color = iconColor.copy(alpha = 0.2f),
                shape = MaterialTheme.shapes.medium
            ),
        contentAlignment = Alignment.Center
    ) {
        val icon = when (prayer) {
            Prayer.FAJR -> Icons.Default.DarkMode
            Prayer.SUNRISE -> Icons.Default.WbSunny
            Prayer.DHUHR -> Icons.Default.WbSunny
            Prayer.ASR -> Icons.Default.WbTwilight
            Prayer.MAGHRIB -> Icons.Default.Brightness3
            Prayer.ISHA -> Icons.Default.NightsStay
            else -> Icons.Default.AccessTime
        }
        
        Icon(
            imageVector = icon,
            contentDescription = prayer.name,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SettingsPanel(
    selectedMadhab: Madhab,
    selectedCalculationMethod: CalculationMethod,
    onMadhabChange: (Madhab) -> Unit,
    onCalculationMethodChange: (CalculationMethod) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1C2541).copy(alpha = 0.8f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "CALCULATION SETTINGS",
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD4AF37)
            )
            
            // Madhab Selection
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Asr Calculation (Madhab)",
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MadhabOption(
                        name = "Shafi",
                        madhab = Madhab.SHAFI,
                        isSelected = selectedMadhab == Madhab.SHAFI,
                        onClick = { onMadhabChange(Madhab.SHAFI) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    MadhabOption(
                        name = "Hanafi",
                        madhab = Madhab.HANAFI,
                        isSelected = selectedMadhab == Madhab.HANAFI,
                        onClick = { onMadhabChange(Madhab.HANAFI) },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Text(
                    text = if (selectedMadhab == Madhab.SHAFI) {
                        "Standard: Shadow = Object length"
                    } else {
                        "Later time: Shadow = 2× Object length"
                    },
                    fontSize = 11.sp,
                    color = Color(0xFF00D9FF).copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
            }
            
            Divider(color = Color(0xFFD4AF37).copy(alpha = 0.2f))
            
            // Calculation Method
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Calculation Method",
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalculationMethodOption(
                        name = "Muslim World League",
                        method = CalculationMethod.MUSLIM_WORLD_LEAGUE,
                        description = "Fajr: 18°, Isha: 17°",
                        isSelected = selectedCalculationMethod == CalculationMethod.MUSLIM_WORLD_LEAGUE,
                        onClick = { onCalculationMethodChange(CalculationMethod.MUSLIM_WORLD_LEAGUE) }
                    )
                    
                    CalculationMethodOption(
                        name = "Islamic Society of North America",
                        method = CalculationMethod.NORTH_AMERICA,
                        description = "Fajr: 15°, Isha: 15°",
                        isSelected = selectedCalculationMethod == CalculationMethod.NORTH_AMERICA,
                        onClick = { onCalculationMethodChange(CalculationMethod.NORTH_AMERICA) }
                    )
                    
                    CalculationMethodOption(
                        name = "Egyptian General Authority",
                        method = CalculationMethod.EGYPTIAN,
                        description = "Fajr: 19.5°, Isha: 17.5°",
                        isSelected = selectedCalculationMethod == CalculationMethod.EGYPTIAN,
                        onClick = { onCalculationMethodChange(CalculationMethod.EGYPTIAN) }
                    )
                    
                    CalculationMethodOption(
                        name = "Umm al-Qura University",
                        method = CalculationMethod.UMM_AL_QURA,
                        description = "Makkah, Saudi Arabia",
                        isSelected = selectedCalculationMethod == CalculationMethod.UMM_AL_QURA,
                        onClick = { onCalculationMethodChange(CalculationMethod.UMM_AL_QURA) }
                    )
                    
                    CalculationMethodOption(
                        name = "University of Islamic Sciences, Karachi",
                        method = CalculationMethod.KARACHI,
                        description = "Fajr: 18°, Isha: 18°",
                        isSelected = selectedCalculationMethod == CalculationMethod.KARACHI,
                        onClick = { onCalculationMethodChange(CalculationMethod.KARACHI) }
                    )
                    
                    CalculationMethodOption(
                        name = "Moonsighting Committee",
                        method = CalculationMethod.MOON_SIGHTING_COMMITTEE,
                        description = "Fajr: 18°, Isha: 18°",
                        isSelected = selectedCalculationMethod == CalculationMethod.MOON_SIGHTING_COMMITTEE,
                        onClick = { onCalculationMethodChange(CalculationMethod.MOON_SIGHTING_COMMITTEE) }
                    )
                }
            }
        }
    }
}

@Composable
fun MadhabOption(
    name: String,
    madhab: Madhab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(0xFF00D9FF).copy(alpha = 0.3f)
            } else {
                Color(0xFF0A1128).copy(alpha = 0.5f)
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, Color(0xFF00D9FF))
        } else {
            BorderStroke(1.dp, Color(0xFFD4AF37).copy(alpha = 0.3f))
        },
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = name,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF00D9FF) else Color.White,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        )
    }
}

@Composable
fun CalculationMethodOption(
    name: String,
    method: CalculationMethod,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                Color(0xFF00D9FF).copy(alpha = 0.2f)
            } else {
                Color(0xFF0A1128).copy(alpha = 0.4f)
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, Color(0xFF00D9FF))
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF00D9FF) else Color.White,
                    letterSpacing = 0.5.sp
                )
                
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = if (isSelected) {
                        Color(0xFF00D9FF).copy(alpha = 0.7f)
                    } else {
                        Color.White.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color(0xFF00D9FF),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Helper Functions

fun calculatePrayerTimes(
    location: Location,
    madhab: Madhab,
    calculationMethod: CalculationMethod
): PrayerTimes {
    val coordinates = Coordinates(location.latitude, location.longitude)
    val date = DateComponents.from(Date())
    val params = calculationMethod.parameters
    params.madhab = madhab
    
    return PrayerTimes(coordinates, date, params)
}

fun getPrayerTime(prayerTimes: PrayerTimes, prayer: Prayer): Date {
    return when (prayer) {
        Prayer.FAJR -> prayerTimes.fajr
        Prayer.SUNRISE -> prayerTimes.sunrise
        Prayer.DHUHR -> prayerTimes.dhuhr
        Prayer.ASR -> prayerTimes.asr
        Prayer.MAGHRIB -> prayerTimes.maghrib
        Prayer.ISHA -> prayerTimes.isha
        else -> Date()
    }
}

fun getPrayerArabicName(prayer: Prayer?): String {
    return when (prayer) {
        Prayer.FAJR -> "الفجر"
        Prayer.SUNRISE -> "الشروق"
        Prayer.DHUHR -> "الظهر"
        Prayer.ASR -> "العصر"
        Prayer.MAGHRIB -> "المغرب"
        Prayer.ISHA -> "العشاء"
        else -> ""
    }
}

fun calculateTimeUntil(targetTime: Date): String {
    val now = Date()
    val diff = targetTime.time - now.time
    
    if (diff < 0) return "Passed"
    
    val hours = (diff / (1000 * 60 * 60)).toInt()
    val minutes = ((diff / (1000 * 60)) % 60).toInt()
    
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

// First-time Setup Dialog
@Composable
fun FirstTimeSetupDialog(
    onComplete: (Madhab, CalculationMethod) -> Unit
) {
    var selectedMadhab by remember { mutableStateOf(Madhab.SHAFI) }
    var selectedMethod by remember { mutableStateOf(CalculationMethod.MUSLIM_WORLD_LEAGUE) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1128).copy(alpha = 0.97f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1C2541).copy(alpha = 0.95f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFFD4AF37),
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "PRAYER TIME SETTINGS",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = Color(0xFFD4AF37),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Please configure your preferences",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
                
                Divider(color = Color(0xFFD4AF37).copy(alpha = 0.3f))
                
                // Madhab Selection
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Asr Calculation Method (Madhab)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD4AF37),
                        letterSpacing = 1.sp
                    )
                    
                    Text(
                        text = "This determines when Asr prayer time begins",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MadhabOption(
                            name = "Shafi'i",
                            madhab = Madhab.SHAFI,
                            isSelected = selectedMadhab == Madhab.SHAFI,
                            onClick = { selectedMadhab = Madhab.SHAFI },
                            modifier = Modifier.weight(1f)
                        )
                        
                        MadhabOption(
                            name = "Hanafi",
                            madhab = Madhab.HANAFI,
                            isSelected = selectedMadhab == Madhab.HANAFI,
                            onClick = { selectedMadhab = Madhab.HANAFI },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF00D9FF).copy(alpha = 0.1f)
                        )
                    ) {
                        Text(
                            text = if (selectedMadhab == Madhab.SHAFI) {
                                "Shafi'i: Asr begins when shadow = object length (earlier time)"
                            } else {
                                "Hanafi: Asr begins when shadow = 2× object length (later time)"
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF00D9FF),
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                
                Divider(color = Color(0xFFD4AF37).copy(alpha = 0.3f))
                
                // Calculation Method Selection
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Calculation Method",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD4AF37),
                        letterSpacing = 1.sp
                    )
                    
                    Text(
                        text = "Choose based on your region or local community",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CalculationMethodOption(
                            name = "Muslim World League",
                            method = CalculationMethod.MUSLIM_WORLD_LEAGUE,
                            description = "Most countries worldwide",
                            isSelected = selectedMethod == CalculationMethod.MUSLIM_WORLD_LEAGUE,
                            onClick = { selectedMethod = CalculationMethod.MUSLIM_WORLD_LEAGUE }
                        )
                        
                        CalculationMethodOption(
                            name = "ISNA (North America)",
                            method = CalculationMethod.NORTH_AMERICA,
                            description = "USA, Canada",
                            isSelected = selectedMethod == CalculationMethod.NORTH_AMERICA,
                            onClick = { selectedMethod = CalculationMethod.NORTH_AMERICA }
                        )
                        
                        CalculationMethodOption(
                            name = "Egyptian",
                            method = CalculationMethod.EGYPTIAN,
                            description = "Egypt, Syria, Lebanon",
                            isSelected = selectedMethod == CalculationMethod.EGYPTIAN,
                            onClick = { selectedMethod = CalculationMethod.EGYPTIAN }
                        )
                        
                        CalculationMethodOption(
                            name = "Umm al-Qura",
                            method = CalculationMethod.UMM_AL_QURA,
                            description = "Makkah, Saudi Arabia",
                            isSelected = selectedMethod == CalculationMethod.UMM_AL_QURA,
                            onClick = { selectedMethod = CalculationMethod.UMM_AL_QURA }
                        )
                        
                        CalculationMethodOption(
                            name = "Karachi",
                            method = CalculationMethod.KARACHI,
                            description = "Pakistan, Bangladesh, India",
                            isSelected = selectedMethod == CalculationMethod.KARACHI,
                            onClick = { selectedMethod = CalculationMethod.KARACHI }
                        )
                        
                        CalculationMethodOption(
                            name = "Moonsighting Committee",
                            method = CalculationMethod.MOON_SIGHTING_COMMITTEE,
                            description = "Parts of USA",
                            isSelected = selectedMethod == CalculationMethod.MOON_SIGHTING_COMMITTEE,
                            onClick = { selectedMethod = CalculationMethod.MOON_SIGHTING_COMMITTEE }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Confirm Button
                Button(
                    onClick = { 
                        onComplete(selectedMadhab, selectedMethod)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00D9FF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "SAVE & ENABLE NOTIFICATION",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Text(
                    text = "You can change these settings anytime from the settings menu",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}