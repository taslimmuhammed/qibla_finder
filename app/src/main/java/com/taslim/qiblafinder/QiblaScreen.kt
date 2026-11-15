package com.taslim.qiblafinder

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.taslim.qiblafinder.ui.theme.Gold
import com.taslim.qiblafinder.ui.theme.GoldDeep
import com.taslim.qiblafinder.ui.theme.GoldLight
import com.taslim.qiblafinder.ui.theme.Ivory
import com.taslim.qiblafinder.ui.theme.Midnight
import com.taslim.qiblafinder.ui.theme.Midnight800
import com.taslim.qiblafinder.ui.theme.MihrabType
import com.taslim.qiblafinder.ui.theme.QiblaTheme
import com.taslim.qiblafinder.ui.theme.Sans
import com.taslim.qiblafinder.ui.theme.Serif
import com.taslim.qiblafinder.ui.theme.Stone
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// The dial is drawn on a 330-unit square plus 18 units for the marker above it
private const val DIAL_HEIGHT_RATIO = 1f + 18f / 330f

/** Degrees to turn to face the Qibla: positive is clockwise (turn right). */
private fun turnAngle(qiblaBearing: Float, heading: Float): Float =
    ((qiblaBearing - heading + 540f) % 360f) - 180f

@Composable
fun QiblaScreen(
    settings: PrayerSettingsStore,
    compass: CompassState,
    locationState: LocationState,
    actions: AppActions,
    onOpenSettings: () -> Unit
) {
    val colors = QiblaTheme.colors
    var showTips by rememberSaveable { mutableStateOf(false) }
    var showPlaces by rememberSaveable { mutableStateOf(false) }
    val location = locationState.location
    val aligned by animateFloatAsState(if (compass.isAligned && location != null) 1f else 0f, tween(450), label = "aligned")

    MihrabBackground(goldGlowCenterY = 0.57f) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The dial takes the height the steady parts around it leave, so the first-run tip
            // shows without scrolling on any screen. The calibration and hold-flat banners are
            // left out on purpose: the dial shouldn't shrink every time the phone tilts.
            val density = LocalDensity.current
            val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            var headerHeight by remember { mutableIntStateOf(0) }
            var readoutHeight by remember { mutableIntStateOf(0) }
            var footerHeight by remember { mutableIntStateOf(0) }
            val measured = headerHeight > 0 && readoutHeight > 0 && footerHeight > 0
            val widest = min(maxWidth - 60.dp, 380.dp)
            val fitted = if (measured) {
                val around = with(density) { (headerHeight + readoutHeight + footerHeight).toDp() }
                // The spacers above the header, the readout and the dial
                val left = maxHeight - statusBar - around - (12 + 24 + 18).dp
                (left / DIAL_HEIGHT_RATIO).coerceIn(min(240.dp, widest), widest)
            } else {
                min(widest, maxHeight * 0.4f)
            }
            val dialSize = remember { Animatable(fitted, Dp.VectorConverter) }
            var settled by remember { mutableStateOf(false) }
            LaunchedEffect(fitted, measured) {
                if (!measured) return@LaunchedEffect
                // The first fit lands before the screen has faded in; later ones glide
                if (settled) dialSize.animateTo(fitted, tween(400)) else dialSize.snapTo(fitted)
                settled = true
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { headerHeight = it.height }
                ) {
                    LocationPill(
                        text = locationState.placeName ?: if (location != null) "Your location" else "Set location",
                        loading = locationState.isLocating,
                        onClick = { showPlaces = true },
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 12.dp)
                    )
                    RoundIconButton(icon = MihrabIcons.Sliders, contentDescription = "Settings", onClick = onOpenSettings)
                }

                if (location == null) {
                    Spacer(Modifier.height(32.dp))
                    LocationNeededCard(locationState, actions)
                    return@Column
                }

                AnimatedVisibility(
                    visible = compass.needsCalibration,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    StatusBanner(
                        icon = MihrabIcons.Loop,
                        title = "Compass needs calibrating",
                        message = "Wave your phone in a figure-8 a few times.",
                        tone = BannerTone.Warning,
                        actionLabel = "Show me",
                        onAction = { showTips = true },
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
                AnimatedVisibility(
                    visible = !compass.isFlat && !compass.needsCalibration,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    StatusBanner(
                        icon = MihrabIcons.Phone,
                        title = "Hold your phone flat",
                        message = "Keep it level, screen facing up, for an accurate reading.",
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))
                BearingReadout(
                    bearing = compass.qiblaBearing,
                    turn = turnAngle(compass.qiblaBearing, compass.azimuth),
                    isAligned = compass.isAligned,
                    modifier = Modifier.onSizeChanged { readoutHeight = it.height }
                )

                Spacer(Modifier.height(18.dp))
                CompassDial(
                    heading = compass.azimuth,
                    qiblaBearing = compass.qiblaBearing,
                    aligned = { aligned },
                    modifier = Modifier.size(width = dialSize.value, height = dialSize.value * DIAL_HEIGHT_RATIO)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { footerHeight = it.height }
                ) {
                    Spacer(Modifier.height(22.dp))
                    // New users see the tip in place of the stats, so it fits without scrolling
                    // even on short screens; the stats take over once it's dismissed
                    AnimatedContent(
                        targetState = settings.compassTipSeen,
                        transitionSpec = { fadeIn(tween(300, delayMillis = 150)) togetherWith fadeOut(tween(150)) },
                        label = "tipOrStats"
                    ) { tipSeen ->
                        if (tipSeen) {
                            CompassStats(
                                distanceKm = remember(location) { distanceToKaabaKm(location) },
                                heading = compass.azimuth,
                                needsCalibration = compass.needsCalibration,
                                onCalibrate = { showTips = true }
                            )
                        } else {
                            StatusBanner(
                                icon = MihrabIcons.Phone,
                                title = "Lay flat, turn slowly",
                                message = "When the Kaaba meets the gold marker, you're facing the Qibla.",
                                actionLabel = "Tips",
                                onAction = { showTips = true },
                                onDismiss = settings::markCompassTipSeen
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showTips) {
        CompassTipsSheet(
            accuracyMeters = locationState.accuracyMeters,
            onDismiss = {
                showTips = false
                settings.markCompassTipSeen()
            }
        )
    }
    if (showPlaces) {
        CitySearchSheet(
            onPick = {
                showPlaces = false
                actions.setManualLocation(it)
            },
            onDismiss = { showPlaces = false },
            onUseCurrentLocation = {
                showPlaces = false
                actions.requestLocation()
            }
        )
    }
}

@Composable
private fun BearingReadout(bearing: Float, turn: Float, isAligned: Boolean, modifier: Modifier = Modifier) {
    val colors = QiblaTheme.colors
    val degrees = wholeDegrees(bearing)
    val turnDegrees = abs(turn).roundToInt()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.semantics(mergeDescendants = true) {}
    ) {
        AnimatedContent(
            targetState = isAligned,
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) },
            label = "eyebrow"
        ) { facing ->
            if (facing) {
                Eyebrow("Aligned · hold steady", color = colors.goldLight, bold = true)
            } else {
                Eyebrow("Qibla direction")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = degrees.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = colors.text
            )
            Text(
                text = "°",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp, lineHeight = 50.sp),
                color = colors.gold,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${compassPoint(bearing)} · FROM TRUE NORTH",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
            fontWeight = FontWeight.Medium,
            color = colors.textMuted
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                isAligned -> "You are facing the Qibla"
                else -> "Turn $turnDegrees° to your ${if (turn > 0) "right" else "left"}"
            },
            style = MihrabType.guidance,
            color = if (isAligned) colors.goldLight else colors.textSoft,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The dial, drawn on the design's 330-unit grid. The card turns with the phone so north stays
 * north, the gold needle points at the Kaaba, and the fixed marker at the top shows where the
 * phone is pointing. When the needle meets the marker the ring lights up.
 */
@Composable
private fun CompassDial(
    heading: Float,
    qiblaBearing: Float,
    aligned: () -> Float,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.82f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val description = "Compass. The Qibla is at ${wholeDegrees(qiblaBearing)} degrees, " +
        "you are facing ${wholeDegrees(heading)} degrees."

    Canvas(modifier.semantics { contentDescription = description }) {
        val u = size.width / 330f
        val c = Offset(size.width / 2f, 18f * u + 165f * u)
        val glow = aligned()

        // Warm halo behind the whole dial once aligned
        if (glow > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Gold.copy(alpha = 0.26f * glow * pulse),
                    0.38f to Gold.copy(alpha = 0.08f * glow),
                    0.66f to Color.Transparent,
                    center = c,
                    radius = 260f * u
                ),
                radius = 260f * u,
                center = c
            )
        }

        // Fixed outer rings
        drawCircle(Gold.copy(alpha = 0.18f), radius = 164f * u, center = c, style = Stroke(1f * u))
        drawCircle(Gold.copy(alpha = 0.07f), radius = 146f * u, center = c, style = Stroke(1f * u))

        rotate(-heading, pivot = c) {
            drawDialCard(c, u, textMeasurer)
            rotate(qiblaBearing, pivot = c) { drawQiblaNeedle(c, u) }
        }

        // Aligned ring
        if (glow > 0f) {
            val r = 141f * u
            drawCircle(
                brush = Brush.radialGradient(
                    (r - 22f * u) / (r + 24f * u) to Color.Transparent,
                    r / (r + 24f * u) to Gold.copy(alpha = 0.40f * glow * pulse),
                    1f to Color.Transparent,
                    center = c,
                    radius = r + 24f * u
                ),
                radius = r + 24f * u,
                center = c
            )
            drawCircle(GoldLight.copy(alpha = glow), radius = r, center = c, style = Stroke(1.5f * u))
        }

        // Fixed marker where the top of the phone points
        val marker = Path().apply {
            moveTo(c.x, c.y - 168f * u)
            lineTo(c.x - 7f * u, c.y - 180f * u)
            lineTo(c.x + 7f * u, c.y - 180f * u)
            close()
        }
        drawPath(marker, GoldLight)

        // Hub
        drawCircle(Midnight, radius = 10f * u, center = c)
        drawCircle(Gold, radius = 10f * u, center = c, style = Stroke(1f * u))
        drawCircle(Gold, radius = 3f * u, center = c)
    }
}

private fun DrawScope.drawDialCard(c: Offset, u: Float, textMeasurer: androidx.compose.ui.text.TextMeasurer) {
    drawCircle(Midnight800, radius = 140f * u, center = c)
    drawCircle(Gold.copy(alpha = 0.30f), radius = 140f * u, center = c, style = Stroke(1f * u))

    // Ticks every 2°, brighter every 10°, gold every 30°
    for (deg in 0 until 360 step 2) {
        val (inner, width, color) = when {
            deg % 30 == 0 -> Triple(122f, 1.8f, Gold)
            deg % 10 == 0 -> Triple(124f, 1.2f, Ivory.copy(alpha = 0.62f))
            else -> Triple(128f, 0.8f, Ivory.copy(alpha = 0.30f))
        }
        val rad = Math.toRadians(deg.toDouble())
        val dir = Offset(sin(rad).toFloat(), -cos(rad).toFloat())
        drawLine(color, start = c + dir * (inner * u), end = c + dir * (136f * u), strokeWidth = width * u)
    }

    // Geometric centre: two rings and an eight-pointed star
    drawCircle(Gold.copy(alpha = 0.16f), radius = 88f * u, center = c, style = Stroke(0.75f * u))
    drawCircle(Gold.copy(alpha = 0.16f), radius = 34f * u, center = c, style = Stroke(0.75f * u))
    drawEightPointStar(c, 56f * u, Gold.copy(alpha = 0.15f), 0.75f * u)

    // Labels turn with the card, like a printed compass rose
    val labelRadius = 108.6f * u
    val cardinals = listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270)
    cardinals.forEach { (label, deg) ->
        val style = TextStyle(
            fontFamily = Serif,
            fontWeight = FontWeight.Medium,
            fontSize = ((if (label == "N") 24f else 20f) * u).toSp(),
            color = if (label == "N") Gold else Ivory
        )
        drawLabel(textMeasurer, label, style, c, labelRadius, deg.toFloat())
    }
    listOf(30, 60, 120, 150, 210, 240, 300, 330).forEach { deg ->
        val style = TextStyle(
            fontFamily = Sans,
            fontWeight = FontWeight.Medium,
            fontSize = (10f * u).toSp(),
            color = Stone
        )
        drawLabel(textMeasurer, deg.toString(), style, c, labelRadius, deg.toFloat())
    }
}

private fun DrawScope.drawLabel(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    c: Offset,
    radius: Float,
    degrees: Float
) {
    val layout = textMeasurer.measure(text, style)
    rotate(degrees, pivot = c) {
        drawText(
            layout,
            topLeft = Offset(c.x - layout.size.width / 2f, c.y - radius - layout.size.height / 2f)
        )
    }
}

/** Drawn pointing straight up; the caller rotates it onto the Qibla bearing. */
private fun DrawScope.drawQiblaNeedle(c: Offset, u: Float) {
    fun p(x: Float, y: Float) = Offset(c.x + x * u, c.y + y * u)

    drawLine(
        color = Gold.copy(alpha = 0.45f),
        start = c,
        end = p(0f, -139f),
        strokeWidth = 1f * u,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 6f * u))
    )

    val needle = Path().apply {
        moveTo(p(0f, -95f).x, p(0f, -95f).y)
        lineTo(p(6.5f, -13f).x, p(6.5f, -13f).y)
        lineTo(p(0f, -2f).x, p(0f, -2f).y)
        lineTo(p(-6.5f, -13f).x, p(-6.5f, -13f).y)
        close()
    }
    drawPath(needle, Gold)
    val shade = Path().apply {
        moveTo(p(0f, -95f).x, p(0f, -95f).y)
        lineTo(p(6.5f, -13f).x, p(6.5f, -13f).y)
        lineTo(p(0f, -2f).x, p(0f, -2f).y)
        close()
    }
    drawPath(shade, GoldDeep)

    val tail = Path().apply {
        moveTo(p(0f, 3f).x, p(0f, 3f).y)
        lineTo(p(3.5f, 35f).x, p(3.5f, 35f).y)
        lineTo(p(0f, 51f).x, p(0f, 51f).y)
        lineTo(p(-3.5f, 35f).x, p(-3.5f, 35f).y)
        close()
    }
    drawPath(tail, Ivory.copy(alpha = 0.22f))

    // The Kaaba rides just outside the card, at the end of the needle
    drawCircle(Gold.copy(alpha = 0.14f), radius = 13f * u, center = p(0f, -151f))
    drawKaaba(p(0f, -153f), u)
}

@Composable
private fun CompassStats(
    distanceKm: Int,
    heading: Float,
    needsCalibration: Boolean,
    onCalibrate: () -> Unit
) {
    val colors = QiblaTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Hairline(color = colors.gold.copy(alpha = 0.14f))
        Row(Modifier.height(64.dp)) {
            StatCell("To Makkah", formatKm(distanceKm), Modifier.weight(1f))
            StatCell(
                "Heading",
                "${wholeDegrees(heading)}°",
                Modifier
                    .weight(1f)
                    .leftHairline(colors.gold.copy(alpha = 0.14f))
            )
            StatCell(
                label = "Compass",
                value = if (needsCalibration) "Calibrate" else "Calibrated",
                valueColor = if (needsCalibration) colors.warning else colors.text,
                modifier = Modifier
                    .weight(1f)
                    .leftHairline(colors.gold.copy(alpha = 0.14f))
                    .clickable(role = Role.Button, onClickLabel = "Compass tips", onClick = onCalibrate)
            )
        }
        Hairline(color = colors.gold.copy(alpha = 0.14f))
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = QiblaTheme.colors.text
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .semantics(mergeDescendants = true) {}
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = QiblaTheme.colors.textFaint, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassTipsSheet(accuracyMeters: Float, onDismiss: () -> Unit) {
    val colors = QiblaTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.card,
        contentColor = colors.text
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            Text("Getting an accurate reading", style = MaterialTheme.typography.headlineLarge, color = colors.text)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Phone compasses are easily thrown off. These steps take a few seconds and make a big difference.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
            Spacer(Modifier.height(20.dp))
            FigureEightAnimation(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
            Spacer(Modifier.height(22.dp))

            TipRow(MihrabIcons.Phone, "Lay your phone flat", "Hold it level in front of you with the screen facing up.")
            TipRow(MihrabIcons.Loop, "Calibrate with a figure-8", "Wave your phone through the air in a figure-8, three or four times.")
            TipRow(MihrabIcons.Magnet, "Keep away from metal", "Cars, laptops, speakers and magnetic cases can skew the reading.")
            TipRow(MihrabIcons.Pulse, "Feel for the buzz", "Turn slowly. Your phone buzzes gently when you're facing the Qibla.")

            if (accuracyMeters > 0f) {
                Text(
                    text = "Location accurate to ${accuracyMeters.roundToInt()} m",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            GoldButton(
                text = "Got it",
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TipRow(icon: ImageVector, title: String, body: String) {
    val colors = QiblaTheme.colors
    Row(Modifier.padding(bottom = 18.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(colors.gold.copy(alpha = 0.10f))
        ) {
            Icon(icon, contentDescription = null, tint = colors.gold, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun FigureEightAnimation(modifier: Modifier = Modifier) {
    val colors = QiblaTheme.colors
    val progress by rememberInfiniteTransition(label = "figure8").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "progress"
    )

    Canvas(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(Midnight)
            .padding(16.dp)
    ) {
        val lobe = size.height * 0.42f
        fun point(t: Float): Offset {
            val rad = t * 2.0 * Math.PI
            val scale = 2.0 / (3.0 - cos(2 * rad))
            return Offset(
                (size.width / 2 + lobe * 1.6f * scale * cos(rad)).toFloat(),
                (size.height / 2 + lobe * scale * sin(rad) * cos(rad) * 1.6f).toFloat()
            )
        }

        val path = Path()
        for (i in 0..120) {
            val p = point(i / 120f)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(path, colors.gold.copy(alpha = 0.22f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        for (i in 1..12) {
            drawCircle(
                color = colors.gold.copy(alpha = 0.3f * (13 - i) / 12f),
                radius = 4.dp.toPx() * (13 - i) / 12f,
                center = point((progress - i * 0.012f + 1f) % 1f)
            )
        }

        val phone = point(progress)
        val w = 18.dp.toPx()
        val h = 30.dp.toPx()
        drawRoundRect(
            color = colors.gold,
            topLeft = Offset(phone.x - w / 2, phone.y - h / 2),
            size = Size(w, h),
            cornerRadius = CornerRadius(5.dp.toPx())
        )
        drawRoundRect(
            color = Midnight,
            topLeft = Offset(phone.x - w / 2 + 3.dp.toPx(), phone.y - h / 2 + 4.dp.toPx()),
            size = Size(w - 6.dp.toPx(), h - 10.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
    }
}
