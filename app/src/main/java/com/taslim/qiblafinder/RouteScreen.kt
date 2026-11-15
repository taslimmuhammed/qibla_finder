package com.taslim.qiblafinder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.taslim.qiblafinder.ui.theme.Gold
import com.taslim.qiblafinder.ui.theme.GoldLight
import com.taslim.qiblafinder.ui.theme.Ivory
import com.taslim.qiblafinder.ui.theme.Midnight
import com.taslim.qiblafinder.ui.theme.MihrabType
import com.taslim.qiblafinder.ui.theme.QiblaTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun RouteScreen(locationState: LocationState, actions: AppActions) {
    val colors = QiblaTheme.colors
    val location = locationState.location

    MihrabBackground(glowHeight = 0.3f, glowStrength = 0.5f) {
        if (location == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                RouteHeader()
                Spacer(Modifier.height(28.dp))
                LocationNeededCard(locationState, actions)
            }
            return@MihrabBackground
        }

        val city = locationState.placeName?.substringBefore(",")?.trim()?.ifBlank { null } ?: "You"
        val bearing = remember(location) { qiblaBearing(location.latitude, location.longitude) }
        val distance = remember(location) { distanceToKaabaKm(location) }

        // The path draws itself in each time the screen opens or the location changes
        val reveal = remember(location) { Animatable(0f) }
        LaunchedEffect(location) { reveal.animateTo(1f, tween(1400, delayMillis = 150, easing = FastOutSlowInEasing)) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val topInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
            val cardHeight = 248.dp
            // Centre the route in the open space between the header and the details card
            val focusY = (topInset + 150.dp + (maxHeight - cardHeight - 16.dp)) / 2

            val view = remember(location, maxWidth, maxHeight) {
                with(density) {
                    GlobeView.fit(
                        fromLat = location.latitude,
                        fromLon = location.longitude,
                        width = maxWidth.toPx(),
                        focusY = focusY.toPx(),
                        sideRoom = 56.dp.toPx()
                    )
                }
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics {
                        contentDescription = "Globe showing the shortest route from $city to Makkah, ${formatKm(distance)}"
                    }
            ) {
                drawGlobe(view, reveal.value)
            }

            RouteLabels(
                view = view,
                city = city,
                distance = formatKm(distance),
                progress = reveal.value,
                width = maxWidth
            )

            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp)
            ) {
                RouteHeader()
            }

            RouteDetailsCard(
                from = city,
                bearing = bearing,
                distanceKm = distance,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }
    }
}

@Composable
private fun RouteHeader() {
    val colors = QiblaTheme.colors
    Text("Path to the Kaaba", style = MaterialTheme.typography.headlineLarge, color = colors.text)
    Spacer(Modifier.height(8.dp))
    Text(
        text = "The great-circle route — the shortest path across the Earth, and the line your Qibla follows.",
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textMuted,
        modifier = Modifier.widthIn(max = 300.dp)
    )
}

/** An orthographic view of the Earth, framed so the route sits in the open space on screen. */
private class GlobeView(
    val centerLat: Double,
    val centerLon: Double,
    val radius: Float,
    val origin: Offset,
    val route: List<Pair<Double, Double>>,
    val graticuleStep: Int
) {
    private val sinLat0 = sin(centerLat)
    private val cosLat0 = cos(centerLat)

    /** Screen position of a point given in radians, or null on the far side of the globe. */
    fun project(lat: Double, lon: Double): Offset? {
        val cosLat = cos(lat)
        val dLon = lon - centerLon
        val visible = sinLat0 * sin(lat) + cosLat0 * cosLat * cos(dLon)
        if (visible < 0) return null
        val x = cosLat * sin(dLon)
        val y = cosLat0 * sin(lat) - sinLat0 * cosLat * cos(dLon)
        return Offset(origin.x + radius * x.toFloat(), origin.y - radius * y.toFloat())
    }

    val start: Offset get() = project(route.first().first, route.first().second) ?: origin
    val end: Offset get() = project(route.last().first, route.last().second) ?: origin

    /** The point a fraction [t] of the way along the route. */
    fun along(t: Float): Offset {
        val index = (t.coerceIn(0f, 1f) * (route.size - 1)).roundToInt()
        return route[index].let { project(it.first, it.second) } ?: origin
    }

    companion object {
        private const val SAMPLES = 96

        fun fit(fromLat: Double, fromLon: Double, width: Float, focusY: Float, sideRoom: Float): GlobeView {
            val a = unit(Math.toRadians(fromLat), Math.toRadians(fromLon))
            val b = unit(Math.toRadians(KAABA_LATITUDE), Math.toRadians(KAABA_LONGITUDE))
            val angle = acos(dot(a, b).coerceIn(-1.0, 1.0))

            // Points along the great circle, by spherical interpolation
            val route = (0..SAMPLES).map { i ->
                val t = i.toDouble() / SAMPLES
                val p = if (angle < 1e-9) a else {
                    val s = sin(angle)
                    val wa = sin((1 - t) * angle) / s
                    val wb = sin(t * angle) / s
                    doubleArrayOf(a[0] * wa + b[0] * wb, a[1] * wa + b[1] * wb, a[2] * wa + b[2] * wb)
                }
                asin(p[2].coerceIn(-1.0, 1.0)) to atan2(p[1], p[0])
            }
            val mid = route[SAMPLES / 2]

            fun frame(radius: Float): GlobeView {
                // Tip the view south so the route sits above the globe's centre, without
                // letting either end slip over the horizon
                val wanted = asin((width * 0.44f / radius).toDouble().coerceIn(0.0, 1.0))
                val limit = (PI / 2 - angle / 2 - Math.toRadians(12.0)).coerceAtLeast(0.0)
                val tilt = min(wanted, limit)
                return GlobeView(
                    centerLat = (mid.first - tilt).coerceIn(-PI / 2 + 0.01, PI / 2 - 0.01),
                    centerLon = mid.second,
                    radius = radius,
                    origin = Offset(width / 2f, focusY + radius * sin(tilt).toFloat()),
                    route = route,
                    graticuleStep = when {
                        radius > width * 20f -> 1
                        radius > width * 3f -> 5
                        else -> 15
                    }
                )
            }

            // A globe as wide as the design's, enlarged for short routes so they stay legible
            val baseRadius = width * 0.77f
            var radius = if (sin(angle) > 1e-6) {
                max(baseRadius, min(width * 0.4f / sin(angle).toFloat(), width * 200f))
            } else {
                width * 200f
            }

            // Long routes shrink the globe until both ends clear the screen edges
            var view = frame(radius)
            repeat(2) {
                val reach = max(abs(view.start.x - width / 2f), abs(view.end.x - width / 2f))
                val allowed = width / 2f - sideRoom
                if (reach <= allowed || reach < 1f) return view
                radius *= allowed / reach
                view = frame(radius)
            }
            return view
        }

        private fun unit(lat: Double, lon: Double) =
            doubleArrayOf(cos(lat) * cos(lon), cos(lat) * sin(lon), sin(lat))

        private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    }
}

private fun DrawScope.drawGlobe(view: GlobeView, progress: Float) {
    val r = view.radius
    val c = view.origin

    // Atmosphere and body
    drawCircle(Gold.copy(alpha = 0.05f), radius = r + 18.dp.toPx(), center = c, style = Stroke(24.dp.toPx()))
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFF14201C),
            0.7f to Color(0xFF0D1412),
            1f to Midnight,
            center = Offset(c.x - r * 0.16f, c.y - r * 0.4f),
            radius = r * 1.5f
        ),
        radius = r,
        center = c
    )
    drawCircle(Gold.copy(alpha = 0.38f), radius = r, center = c, style = Stroke(1.dp.toPx()))

    // Graticule
    val lineColor = Gold.copy(alpha = 0.13f)
    val lineStroke = Stroke(0.75.dp.toPx())
    val step = view.graticuleStep
    for (lonDeg in -180 until 180 step step) {
        val lon = Math.toRadians(lonDeg.toDouble())
        drawProjectedLine(view, lineColor, lineStroke, (-90..90 step 2).map { Math.toRadians(it.toDouble()) to lon })
    }
    for (latDeg in (-90 + step) until 90 step step) {
        val lat = Math.toRadians(latDeg.toDouble())
        drawProjectedLine(view, lineColor, lineStroke, (-180..180 step 2).map { lat to Math.toRadians(it.toDouble()) })
    }

    // The route, drawn in as [progress] goes from 0 to 1
    val shown = view.route.take(max(2, (view.route.size * progress).roundToInt()))
    val routePath = Path()
    var started = false
    shown.forEach { (lat, lon) ->
        val p = view.project(lat, lon) ?: return@forEach
        if (!started) routePath.moveTo(p.x, p.y) else routePath.lineTo(p.x, p.y)
        started = true
    }
    drawPath(routePath, Gold.copy(alpha = 0.22f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(routePath, GoldLight, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

    // You
    val start = view.start
    drawCircle(Ivory.copy(alpha = 0.4f), radius = 11.dp.toPx(), center = start, style = Stroke(1.dp.toPx()))
    drawCircle(Ivory, radius = 4.dp.toPx(), center = start)

    // The Kaaba, which appears as the line arrives
    val arrive = ((progress - 0.85f) / 0.15f).coerceIn(0f, 1f)
    if (arrive > 0f) {
        val end = view.end
        drawCircle(Gold.copy(alpha = 0.12f * arrive), radius = 26.dp.toPx(), center = end)
        drawCircle(Gold.copy(alpha = 0.18f * arrive), radius = 15.dp.toPx(), center = end)
        drawKaaba(
            end + Offset(0f, 0.6.dp.toPx()),
            1.25.dp.toPx(),
            outline = Gold.copy(alpha = arrive),
            band = Gold.copy(alpha = arrive)
        )
    }
}

private fun DrawScope.drawProjectedLine(
    view: GlobeView,
    color: Color,
    stroke: Stroke,
    points: List<Pair<Double, Double>>
) {
    val path = Path()
    var penDown = false
    var any = false
    points.forEach { (lat, lon) ->
        val p = view.project(lat, lon)
        if (p == null) {
            penDown = false
        } else {
            if (penDown) path.lineTo(p.x, p.y) else path.moveTo(p.x, p.y)
            penDown = true
            any = true
        }
    }
    if (any) drawPath(path, color, style = stroke)
}

/**
 * The two place labels and the distance tag, positioned around the projected route and kept
 * on the side of each point that faces away from the other.
 */
@Composable
private fun RouteLabels(view: GlobeView, city: String, distance: String, progress: Float, width: Dp) {
    val colors = QiblaTheme.colors
    val margin = 16.dp
    Layout(
        content = {
            Column(Modifier.graphicsLayer { alpha = ((progress - 0.05f) / 0.3f).coerceIn(0f, 1f) }) {
                Text(city, style = MaterialTheme.typography.titleSmall, color = colors.text, maxLines = 1)
                Text("You are here", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp), color = colors.textMuted)
            }
            Text(
                text = distance,
                style = MaterialTheme.typography.labelMedium,
                color = colors.goldLight,
                modifier = Modifier
                    .graphicsLayer { alpha = ((progress - 0.5f) / 0.3f).coerceIn(0f, 1f) }
                    .background(Midnight, CircleShape)
                    .border(1.dp, colors.gold.copy(alpha = 0.4f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
            Column(Modifier.graphicsLayer { alpha = ((progress - 0.85f) / 0.15f).coerceIn(0f, 1f) }) {
                Text("Makkah", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), color = colors.goldLight)
                Text(
                    text = "مكة المكرمة",
                    style = MihrabType.arabic.copy(textDirection = TextDirection.Rtl),
                    color = colors.gold
                )
            }
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val (you, tag, makkah) = measurables.map { it.measure(loose) }
        val w = width.toPx()
        val m = margin.toPx()
        val start = view.start
        val end = view.end
        val youOnLeft = start.x <= end.x

        // Beside the point, on the side away from the route; below it when that side is too tight
        fun place(point: Offset, gap: Float, toLeft: Boolean, label: Placeable): IntOffset {
            val x = if (toLeft) point.x - gap - label.width else point.x + gap
            return if (x >= m && x + label.width <= w - m) {
                IntOffset(x.roundToInt(), (point.y - label.height / 2f).roundToInt())
            } else {
                IntOffset(
                    (point.x - label.width / 2f).coerceIn(m, max(m, w - m - label.width)).roundToInt(),
                    (point.y + gap).roundToInt()
                )
            }
        }

        val youAt = place(start, 14.dp.toPx(), toLeft = youOnLeft, label = you)
        val makkahAt = place(end, 28.dp.toPx(), toLeft = !youOnLeft, label = makkah)

        // The tag sits off the middle of the route, on the side facing up the screen
        val mid = view.along(0.5f)
        val dx = end.x - start.x
        val dy = end.y - start.y
        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        var nx = dy / length
        var ny = -dx / length
        if (ny > 0) {
            nx = -nx
            ny = -ny
        }
        val push = tag.height / 2f + 12.dp.toPx() + abs(nx) * tag.width / 2f
        val tagX = (mid.x + nx * push - tag.width / 2f).coerceIn(m, max(m, w - m - tag.width))
        val tagY = mid.y + ny * push - tag.height / 2f

        layout(constraints.maxWidth, constraints.maxHeight) {
            you.place(youAt)
            tag.place(tagX.roundToInt(), tagY.roundToInt())
            makkah.place(makkahAt)
        }
    }
}

@Composable
private fun RouteDetailsCard(from: String, bearing: Float, distanceKm: Int, modifier: Modifier = Modifier) {
    val colors = QiblaTheme.colors
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(40.dp, shape, ambientColor = Midnight, spotColor = Midnight)
            .background(Color(0xEB0C1210), shape)
            .border(1.dp, colors.border, shape)
            .padding(PaddingValues(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 8.dp))
            .semantics(mergeDescendants = true) {}
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("FROM", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.22.em), color = colors.textFaint)
                Text(from, style = MaterialTheme.typography.headlineSmall, color = colors.text, maxLines = 1)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 14.dp)
            ) {
                DashedRule(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(6.dp)
                        .graphicsLayer { rotationZ = 45f }
                        .background(colors.gold)
                )
                DashedRule(Modifier.weight(1f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("TO", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.22.em), color = colors.textFaint)
                Text("Makkah", style = MaterialTheme.typography.headlineSmall, color = colors.goldLight)
            }
        }
        Spacer(Modifier.height(14.dp))
        Hairline(color = colors.gold.copy(alpha = 0.14f))
        DetailRow("Qibla bearing", "${wholeDegrees(bearing)}° ${compassPoint(bearing)}")
        Hairline()
        DetailRow("Distance", formatKm(distanceKm))
        Hairline()
        DetailRow("Kaaba", formatCoordinates(KAABA_LATITUDE, KAABA_LONGITUDE))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = QiblaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = colors.text,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun DashedRule(modifier: Modifier = Modifier) {
    val color = QiblaTheme.colors.gold.copy(alpha = 0.45f)
    Canvas(modifier.height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        )
    }
}
