package com.taslim.qiblafinder

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.Prayer
import com.taslim.qiblafinder.ui.theme.MihrabType
import com.taslim.qiblafinder.ui.theme.QiblaTheme
import com.taslim.qiblafinder.ui.theme.Serif
import java.util.Date
import kotlin.math.min

/**
 * First run, in two steps: a welcome that asks for location (or a city), then a review of the
 * defaults we picked for where they are, with notifications offered in the same place.
 */
@Composable
fun OnboardingFlow(
    settings: PrayerSettingsStore,
    locationState: LocationState,
    actions: AppActions
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    // Only move on once the user has acted, not because an old saved location exists
    var requested by rememberSaveable { mutableStateOf(false) }
    // Null until the user picks something, so the defaults can follow the detected country
    var pickedMethod by rememberSaveable { mutableStateOf<CalculationOption?>(null) }
    var pickedMadhab by rememberSaveable { mutableStateOf<Madhab?>(null) }

    val country = locationState.countryCode
    val method = pickedMethod ?: RegionDefaults.methodFor(country)
    val madhab = pickedMadhab ?: RegionDefaults.madhabFor(country)

    LaunchedEffect(requested, locationState.status) {
        if (requested && step == 0 && locationState.status == LocationStatus.Found) step = 1
    }

    BackHandler(enabled = step > 0) {
        requested = false
        step = 0
    }

    MihrabBackground(glowHeight = 0.42f, glowStrength = 0.75f) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(380)) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(380)))
                    .togetherWith(
                        slideOutHorizontally(tween(260)) { if (forward) -it / 5 else it / 5 } + fadeOut(tween(220))
                    )
            },
            label = "onboardingStep"
        ) { current ->
            when (current) {
                0 -> WelcomeStep(
                    locationState = locationState,
                    onEnableLocation = {
                        requested = true
                        actions.resolveLocation(locationState)
                    },
                    onPickCity = { city ->
                        requested = true
                        actions.setManualLocation(city)
                    }
                )
                else -> ReadyStep(
                    locationState = locationState,
                    method = method,
                    madhab = madhab,
                    onMethodChange = { pickedMethod = it },
                    onMadhabChange = { pickedMadhab = it },
                    onPickCity = actions.setManualLocation,
                    onBack = {
                        requested = false
                        step = 0
                    },
                    onFinish = { showTimes, reminders ->
                        settings.completeOnboarding(madhab, method, showTimes, reminders)
                    }
                )
            }
        }
    }
}

// region Welcome

@Composable
private fun WelcomeStep(
    locationState: LocationState,
    onEnableLocation: () -> Unit,
    onPickCity: (CityResult) -> Unit
) {
    val colors = QiblaTheme.colors
    val status = locationState.status
    var showCitySearch by rememberSaveable { mutableStateOf(false) }

    // A gentle staggered entrance: the medallion settles, then the words rise in
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(1600, easing = LinearEasing)) }
    fun reveal(index: Int): Modifier = Modifier.graphicsLayer {
        val t = FastOutSlowInEasing.transform(((appear.value * 1600f - 250f - index * 110f) / 650f).coerceIn(0f, 1f))
        alpha = t
        translationY = (1f - t) * 14.dp.toPx()
    }

    // After a "Don't allow", a city becomes the easy path rather than asking again
    val declined = locationState.permissionDenied && status == LocationStatus.NeedsPermission

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale = min(maxWidth.value / 390f, (maxHeight.value * 0.42f) / 340f)

        WelcomeArt(
            scale = scale,
            progress = { appear.value },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Leaves room for the medallion, which is drawn behind
                Spacer(Modifier.height((330f * scale).dp))
                Text(
                    text = "Mihrab",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.text,
                    modifier = reveal(0)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "QIBLA FINDER",
                    style = MihrabType.eyebrow.copy(letterSpacing = 0.36.em),
                    color = colors.gold,
                    modifier = reveal(1)
                )
                Spacer(Modifier.height(22.dp))
                DiamondRule(reveal(2))
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "فَوَلِّ وَجْهَكَ شَطْرَ الْمَسْجِدِ الْحَرَامِ",
                    style = MihrabType.arabic.copy(
                        fontSize = 26.sp,
                        lineHeight = 44.sp,
                        textDirection = TextDirection.Rtl
                    ),
                    color = colors.gold,
                    textAlign = TextAlign.Center,
                    modifier = reveal(3)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Turn your face toward the Sacred Mosque.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = Serif,
                        fontStyle = FontStyle.Italic,
                        fontSize = 18.sp,
                        lineHeight = 24.sp
                    ),
                    color = colors.textSoft,
                    textAlign = TextAlign.Center,
                    modifier = reveal(4)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "AL-BAQARAH · 2:144",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.22.em),
                    color = colors.textFaint,
                    modifier = reveal(5)
                )
                Spacer(Modifier.height(24.dp))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = reveal(6).padding(bottom = 16.dp)
            ) {
                WelcomeStatusNote(status = status, declined = declined)
                if (declined) {
                    GoldButton(
                        text = "Choose your city",
                        icon = MihrabIcons.Search,
                        onClick = { showCitySearch = true }
                    )
                    Spacer(Modifier.height(8.dp))
                    QuietButton(text = "Try location again", onClick = onEnableLocation)
                } else {
                    GoldButton(
                        text = when (status) {
                            LocationStatus.Locating -> "Finding your location…"
                            LocationStatus.Blocked, LocationStatus.ServicesOff, LocationStatus.Failed ->
                                status.actionLabel.orEmpty()
                            else -> "Enable location"
                        },
                        icon = MihrabIcons.Pin,
                        loading = status == LocationStatus.Locating,
                        onClick = onEnableLocation
                    )
                    Spacer(Modifier.height(8.dp))
                    QuietButton(text = "Choose a city instead", onClick = { showCitySearch = true })
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Your location is used only to find your direction. It never leaves this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showCitySearch) {
        CitySearchSheet(
            onPick = {
                showCitySearch = false
                onPickCity(it)
            },
            onDismiss = { showCitySearch = false }
        )
    }
}

/** One calm line above the buttons when something needs explaining; nothing otherwise. */
@Composable
private fun WelcomeStatusNote(status: LocationStatus, declined: Boolean) {
    val message = when {
        declined -> "No problem. Choose your city and we'll work everything out from there. You can allow location later."
        status == LocationStatus.Blocked || status == LocationStatus.ServicesOff || status == LocationStatus.Failed ->
            "${status.title}. ${status.message}"
        else -> null
    } ?: return
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = QiblaTheme.colors.textSoft,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

/**
 * The pointed arch of a mihrab framing a geometric medallion with the Kaaba at its heart.
 * Drawn on the design's 390-wide grid and scaled to fit.
 */
@Composable
private fun WelcomeArt(scale: Float, progress: () -> Float, modifier: Modifier = Modifier) {
    val gold = QiblaTheme.colors.gold
    val density = LocalDensity.current
    Canvas(modifier) {
        val unit = scale * density.density
        val left = (size.width - 390f * unit) / 2f
        val t = progress()
        val artAlpha = FastOutSlowInEasing.transform((t * 1.6f).coerceIn(0f, 1f))

        translate(left = left) {
            fun p(x: Float, y: Float) = Offset(x * unit, y * unit)

            // Background glows behind the medallion
            drawEllipticalGlow(
                color = gold.copy(alpha = 0.10f * artAlpha),
                center = p(195f, 236f),
                radiusX = 156f * unit,
                radiusY = 186f * unit,
                fadeAt = 0.7f
            )

            // Pointed arch: two arcs meeting at the apex
            fun arch(spring: Float, radius: Float, apexY: Float, halfWidth: Float): Path {
                val leftX = 195f - halfWidth
                val rightX = 195f + halfWidth
                val leftCenter = Offset(leftX + radius, spring)
                val rightCenter = Offset(rightX - radius, spring)
                val sweep = Math.toDegrees(
                    kotlin.math.atan2((spring - apexY).toDouble(), (195f - rightCenter.x).toDouble())
                ).toFloat()
                return Path().apply {
                    moveTo(leftX * unit, 640f * unit)
                    lineTo(leftX * unit, spring * unit)
                    arcTo(
                        Rect(
                            Offset((leftCenter.x - radius) * unit, (spring - radius) * unit),
                            Size(radius * 2 * unit, radius * 2 * unit)
                        ),
                        180f, sweep, false
                    )
                    arcTo(
                        Rect(
                            Offset((rightCenter.x - radius) * unit, (spring - radius) * unit),
                            Size(radius * 2 * unit, radius * 2 * unit)
                        ),
                        360f - sweep, sweep, false
                    )
                    lineTo(rightX * unit, 640f * unit)
                }
            }

            drawPath(
                path = arch(spring = 330f, radius = 267f, apexY = 90f, halfWidth = 150f),
                brush = Brush.verticalGradient(
                    0f to gold.copy(alpha = 0.8f * artAlpha),
                    0.55f to gold.copy(alpha = 0.4f * artAlpha),
                    1f to gold.copy(alpha = 0f),
                    startY = 90f * unit,
                    endY = 640f * unit
                ),
                style = Stroke(1.25f * unit)
            )
            drawPath(
                path = arch(spring = 334f, radius = 260.7f, apexY = 104f, halfWidth = 138f),
                brush = Brush.verticalGradient(
                    0f to gold.copy(alpha = 0.35f * artAlpha),
                    1f to gold.copy(alpha = 0f),
                    startY = 104f * unit,
                    endY = 640f * unit
                ),
                style = Stroke(0.75f * unit)
            )
            // Finial
            drawLine(gold.copy(alpha = artAlpha), p(195f, 56f), p(195f, 66f), strokeWidth = 0.75f * unit)
            rotate(45f, pivot = p(195f, 74f)) {
                drawRect(gold.copy(alpha = artAlpha), topLeft = p(191f, 70f), size = Size(8f * unit, 8f * unit))
            }

            // Medallion, which settles in slightly after the arch
            val settle = FastOutSlowInEasing.transform(((t * 1600f - 100f) / 900f).coerceIn(0f, 1f))
            val c = p(195f, 240f)
            val grow = 0.94f + 0.06f * settle
            fun r(v: Float) = v * unit * grow
            drawCircle(Color(0xFF0E1513).copy(alpha = 0.6f * settle), radius = r(96f), center = c)
            drawCircle(gold.copy(alpha = 0.38f * settle), radius = r(96f), center = c, style = Stroke(0.9f * unit))
            drawCircle(gold.copy(alpha = 0.14f * settle), radius = r(90f), center = c, style = Stroke(0.75f * unit))
            drawEightPointStar(c, r(64f), gold.copy(alpha = 0.30f * settle), 0.8f * unit)
            drawCircle(gold.copy(alpha = 0.26f * settle), radius = r(46f), center = c, style = Stroke(0.75f * unit))
            drawEightPointStar(c, r(30f), gold.copy(alpha = 0.18f * settle), 0.75f * unit, turn = 22.5f)
            drawCircle(gold.copy(alpha = 0.12f * settle), radius = r(30f), center = p(195f, 242f))
            if (settle > 0f) {
                drawKaaba(p(195f, 242f), unit * 1.9f * grow, outline = gold.copy(alpha = settle), band = gold.copy(alpha = settle))
            }
        }
    }
}

// endregion

// region Ready

@Composable
private fun ReadyStep(
    locationState: LocationState,
    method: CalculationOption,
    madhab: Madhab,
    onMethodChange: (CalculationOption) -> Unit,
    onMadhabChange: (Madhab) -> Unit,
    onPickCity: (CityResult) -> Unit,
    onBack: () -> Unit,
    onFinish: (showTimes: Boolean, reminders: Set<Prayer>) -> Unit
) {
    val context = LocalContext.current
    val colors = QiblaTheme.colors
    val location = locationState.location
    var showMethodPicker by rememberSaveable { mutableStateOf(false) }
    var showCitySearch by rememberSaveable { mutableStateOf(false) }
    var remindersOn by rememberSaveable { mutableStateOf(true) }
    var timesBarOn by rememberSaveable { mutableStateOf(true) }

    val recommended = RegionDefaults.methodFor(locationState.countryCode)
    val countryName = RegionDefaults.countryName(locationState.countryCode)
    val placeName = locationState.placeName
    val city = placeName?.substringBefore(",")?.trim()?.ifBlank { null }
    val region = placeName?.substringAfter(",", "")?.trim()?.ifBlank { null } ?: countryName

    val finish = { granted: Boolean ->
        onFinish(
            timesBarOn && granted,
            if (remindersOn && granted) PrayerSettingsStore.REMINDER_PRAYERS.toSet() else emptySet()
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        finish(granted)
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            RoundIconButton(icon = MihrabIcons.Back, contentDescription = "Back", onClick = onBack)
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Eyebrow("Assalamu alaikum")
            Spacer(Modifier.height(10.dp))
            Text(
                text = city ?: "Your location",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.text
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = region ?: location?.let { formatCoordinates(it.latitude, it.longitude, 2) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Change",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.gold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Change city") { showCitySearch = true }
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                )
            }

            if (location != null) {
                val schedule = remember(location, method, madhab) {
                    buildDaySchedule(location.latitude, location.longitude, madhab, method, Date())
                }
                Spacer(Modifier.height(12.dp))
                ReadyFacts(locationState = locationState, schedule = schedule)

                Spacer(Modifier.height(28.dp))
                CardCaption("Prayer times")
                MihrabCard {
                    SettingsRow(
                        title = "Calculation method",
                        subtitle = method.title,
                        badge = if (method == recommended) {
                            countryName?.let { "Recommended for $it" } ?: "Recommended"
                        } else {
                            null
                        },
                        onClick = { showMethodPicker = true }
                    )
                    Hairline(Modifier.padding(horizontal = 20.dp))
                    Column(Modifier.padding(20.dp)) {
                        Text("Asr time", style = MaterialTheme.typography.titleMedium, color = colors.text)
                        Spacer(Modifier.height(12.dp))
                        AsrSelector(selected = madhab, onSelect = onMadhabChange)
                        Spacer(Modifier.height(10.dp))
                        Text(madhab.asrDescription, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                    Hairline(Modifier.padding(horizontal = 20.dp))
                    ScheduleStrip(schedule)
                }
            }

            Spacer(Modifier.height(24.dp))
            CardCaption("Notifications")
            MihrabCard {
                SwitchRow(
                    title = "Remind me at each prayer",
                    subtitle = "A gentle alert as each prayer time begins. Turn single prayers off from the Prayer tab.",
                    checked = remindersOn,
                    onCheckedChange = { remindersOn = it }
                )
                Hairline(Modifier.padding(horizontal = 20.dp))
                SwitchRow(
                    title = "Today's times in the notification bar",
                    subtitle = "Silent and always one swipe away, with the current prayer highlighted.",
                    checked = timesBarOn,
                    onCheckedChange = { timesBarOn = it }
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp, top = 8.dp)
        ) {
            GoldButton(
                text = "Begin",
                icon = MihrabIcons.Compass,
                onClick = {
                    val wantsAlerts = remindersOn || timesBarOn
                    if (wantsAlerts && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !PrayerSettingsStore.hasNotificationPermission(context)
                    ) {
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        finish(true)
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "You can change any of this later in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showMethodPicker) {
        MethodPickerSheet(
            selected = method,
            recommended = recommended,
            onSelect = onMethodChange,
            onDismiss = { showMethodPicker = false }
        )
    }
    if (showCitySearch) {
        CitySearchSheet(
            onPick = {
                showCitySearch = false
                onPickCity(it)
            },
            onDismiss = { showCitySearch = false }
        )
    }
}

/** Three quick facts that show the app already works where they are. */
@Composable
private fun ReadyFacts(locationState: LocationState, schedule: DaySchedule) {
    val location = locationState.location ?: return
    val now = Date()
    val next = schedule.nextPrayer(now)
    val formatter = rememberTimeFormatter()
    val bearing = remember(location) { qiblaBearing(location.latitude, location.longitude) }

    StatStrip(
        listOf(
            "Qibla" to "${wholeDegrees(bearing)}° ${compassPoint(bearing)}",
            "To Makkah" to formatKm(distanceToKaabaKm(location)),
            next.prayer.displayName to formatter.full(next.time)
        )
    )
}

/** Today's five prayer times in a row, so changing a setting shows its effect immediately. */
@Composable
private fun ScheduleStrip(schedule: DaySchedule) {
    val colors = QiblaTheme.colors
    val formatter = rememberTimeFormatter()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 16.dp)
    ) {
        schedule.today.filter { it.prayer != Prayer.SUNRISE }.forEach { slot ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = slot.prayer.displayName.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.12.em),
                    color = colors.textFaint
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatter.clock(slot.time),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp),
                    color = colors.text
                )
            }
        }
    }
}

@Composable
fun CardCaption(text: String, modifier: Modifier = Modifier) {
    Eyebrow(
        text = text,
        color = QiblaTheme.colors.textFaint,
        modifier = modifier.padding(start = 4.dp, bottom = 10.dp)
    )
}

/**
 * Labelled values separated by hairlines, as under the Qibla compass.
 * Each pair is a label and its value.
 */
@Composable
fun StatStrip(stats: List<Pair<String, String>>, modifier: Modifier = Modifier, height: Dp = 64.dp) {
    val colors = QiblaTheme.colors
    Column(modifier.fillMaxWidth()) {
        Hairline(color = colors.gold.copy(alpha = 0.14f))
        Row(Modifier.height(height)) {
            stats.forEachIndexed { index, (label, value) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .then(if (index > 0) Modifier.leftHairline(colors.gold.copy(alpha = 0.14f)) else Modifier)
                ) {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textFaint,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.text,
                        maxLines = 1
                    )
                }
            }
        }
        Hairline(color = colors.gold.copy(alpha = 0.14f))
    }
}

// endregion
