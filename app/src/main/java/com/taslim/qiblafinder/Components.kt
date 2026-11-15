package com.taslim.qiblafinder

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.taslim.qiblafinder.ui.theme.Gold
import com.taslim.qiblafinder.ui.theme.KaabaLeft
import com.taslim.qiblafinder.ui.theme.KaabaRight
import com.taslim.qiblafinder.ui.theme.KaabaTop
import com.taslim.qiblafinder.ui.theme.MihrabType
import com.taslim.qiblafinder.ui.theme.Midnight
import com.taslim.qiblafinder.ui.theme.QiblaTheme
import kotlin.random.Random

// region Backdrop

/**
 * The midnight ground every screen sits on: a deep green glow from the top, an optional gold
 * glow behind the focal point, and a faint film grain.
 */
@Composable
fun MihrabBackground(
    modifier: Modifier = Modifier,
    glowHeight: Float = 0.38f,
    glowStrength: Float = 0.55f,
    goldGlowCenterY: Float? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val green = QiblaTheme.colors.glow
    val grain = rememberGrainBrush()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Midnight)
            .drawBehind {
                drawEllipticalGlow(
                    color = green.copy(alpha = glowStrength),
                    center = Offset(size.width / 2f, 0f),
                    radiusX = size.width * 0.9f,
                    radiusY = size.height * glowHeight,
                    fadeAt = 0.72f
                )
                goldGlowCenterY?.let { y ->
                    drawEllipticalGlow(
                        color = Gold.copy(alpha = 0.08f),
                        center = Offset(size.width / 2f, size.height * y),
                        radiusX = size.width * 0.6f,
                        radiusY = size.height * 0.34f,
                        fadeAt = 0.7f
                    )
                }
                drawRect(grain)
            },
        content = content
    )
}

/** A radial glow stretched into an ellipse, like CSS `radial-gradient(rx ry at x y, …)`. */
fun DrawScope.drawEllipticalGlow(
    color: Color,
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    fadeAt: Float = 1f
) {
    if (radiusX <= 0f || radiusY <= 0f) return
    scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color,
                fadeAt to color.copy(alpha = 0f),
                center = center,
                radius = radiusX
            ),
            radius = radiusX,
            center = center
        )
    }
}

/** A tiling noise texture, generated once, that gives flat fills a little tooth. */
@Composable
private fun rememberGrainBrush(): ShaderBrush = remember {
    val side = 96
    val random = Random(7)
    val pixels = IntArray(side * side) {
        android.graphics.Color.argb(random.nextInt(0, 16), 255, 255, 255)
    }
    val bitmap = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

// endregion

// region Drawing

/**
 * The Kaaba as a small isometric cube with its gold kiswah band, centred on [center].
 * [unit] is the size of one design unit in pixels; the glyph is about 14 units wide.
 */
fun DrawScope.drawKaaba(center: Offset, unit: Float, outline: Color = Gold, band: Color = Gold) {
    fun polygon(vararg points: Float) = Path().apply {
        moveTo(center.x + points[0] * unit, center.y + points[1] * unit)
        for (i in 2 until points.size step 2) lineTo(center.x + points[i] * unit, center.y + points[i + 1] * unit)
        close()
    }

    val edge = Stroke(width = 0.6f * unit, join = StrokeJoin.Round)
    listOf(
        polygon(0f, -8f, 6.9f, -4f, 0f, 0f, -6.9f, -4f) to KaabaTop,
        polygon(-6.9f, -4f, 0f, 0f, 0f, 8f, -6.9f, 4f) to KaabaLeft,
        polygon(0f, 0f, 6.9f, -4f, 6.9f, 4f, 0f, 8f) to KaabaRight
    ).forEach { (face, fill) ->
        drawPath(face, fill)
        drawPath(face, outline, style = edge)
    }
    drawPath(polygon(-6.9f, -2.4f, 0f, 1.6f, 0f, 3f, -6.9f, -1f), band)
    drawPath(polygon(0f, 1.6f, 6.9f, -2.4f, 6.9f, -1f, 0f, 3f), band)
}

/** A square and the same square turned 45°, the eight-pointed star of Islamic geometry. */
fun DrawScope.drawEightPointStar(center: Offset, halfSide: Float, color: Color, strokeWidth: Float, turn: Float = 0f) {
    listOf(turn, turn + 45f).forEach { angle ->
        rotate(angle, pivot = center) {
            drawRect(
                color = color,
                topLeft = Offset(center.x - halfSide, center.y - halfSide),
                size = Size(halfSide * 2, halfSide * 2),
                style = Stroke(strokeWidth)
            )
        }
    }
}

// endregion

// region Controls

/** A 44dp round button with a hairline gold border, used for icon-only actions in headers. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = QiblaTheme.colors.text
) {
    val colors = QiblaTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.chip)
            .border(1.dp, colors.border, CircleShape)
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = contentDescription)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** The location chip in the Qibla header: a gold pin and the place name. */
@Composable
fun LocationPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false
) {
    val colors = QiblaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(colors.chip)
            .border(1.dp, colors.border, CircleShape)
            .clickable(onClick = onClick, role = Role.Button, onClickLabel = "Change location")
            .padding(start = 13.dp, end = 18.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = colors.gold,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Icon(MihrabIcons.Pin, contentDescription = null, tint = colors.gold, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, letterSpacing = 0.01.em),
            fontWeight = FontWeight.Medium,
            color = colors.text,
            maxLines = 1
        )
    }
}

/** The one primary action on a screen: a full-width gold capsule. */
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val colors = QiblaTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.gold,
            contentColor = Midnight,
            disabledContainerColor = colors.gold.copy(alpha = 0.55f),
            disabledContentColor = Midnight.copy(alpha = 0.8f)
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(24.dp, CircleShape, ambientColor = colors.gold, spotColor = colors.gold.copy(alpha = 0.5f))
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Midnight,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** A quiet text action that sits under a [GoldButton]. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = QiblaTheme.colors.text
) {
    TextButton(
        onClick = onClick,
        shape = CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = color
        )
    }
}

// endregion

// region Type and ornament

/** Small wide-tracked caps, e.g. "NEXT PRAYER". */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = QiblaTheme.colors.gold,
    bold: Boolean = false
) {
    Text(
        text = text.uppercase(),
        style = MihrabType.eyebrow,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        color = color,
        modifier = modifier
    )
}

/** A rule with a gold diamond at its centre. */
@Composable
fun DiamondRule(modifier: Modifier = Modifier, lineWidth: Dp = 40.dp) {
    val gold = QiblaTheme.colors.gold
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        Box(
            Modifier
                .width(lineWidth)
                .height(1.dp)
                .background(gold.copy(alpha = 0.45f))
        )
        Box(
            Modifier
                .size(5.dp)
                .graphicsLayer { rotationZ = 45f }
                .background(gold)
        )
        Box(
            Modifier
                .width(lineWidth)
                .height(1.dp)
                .background(gold.copy(alpha = 0.45f))
        )
    }
}

/** An outlined capsule label, e.g. "NOW". */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val colors = QiblaTheme.colors
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.18.em),
        fontWeight = FontWeight.Bold,
        color = colors.goldLight,
        modifier = modifier
            .border(1.dp, colors.gold.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** A 1dp vertical rule down the leading edge, for dividing cells in a row. */
fun Modifier.leftHairline(color: Color): Modifier = drawBehind {
    drawRect(color, size = Size(1.dp.toPx(), size.height))
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = QiblaTheme.colors.hairline) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

// endregion

// region Containers

/** The standard card: deep green-black with a hairline gold border. */
@Composable
fun MihrabCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    glow: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = QiblaTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = colors.card,
        border = BorderStroke(1.dp, colors.gold.copy(alpha = 0.26f))
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (glow) {
                        Modifier.drawBehind {
                            drawEllipticalGlow(
                                color = colors.gold.copy(alpha = 0.12f),
                                center = Offset(size.width, 0f),
                                radiusX = size.width * 0.7f,
                                radiusY = size.height * 0.9f,
                                fadeAt = 0.6f
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(contentPadding),
            content = content
        )
    }
}

enum class BannerTone { Info, Warning }

/** A compact inline message, used instead of blocking dialogs. */
@Composable
fun StatusBanner(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    val colors = QiblaTheme.colors
    val accent = if (tone == BannerTone.Warning) colors.warning else colors.gold
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.07f))
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(18.dp))
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = if (onDismiss != null) 4.dp else 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.text)
            Text(message, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAction, role = Role.Button)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            )
        }
        if (onDismiss != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss, role = Role.Button, onClickLabel = "Dismiss")
            ) {
                Icon(MihrabIcons.Close, contentDescription = "Dismiss", tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A gold ring with an icon at its centre, used as the hero of empty and setup states. */
@Composable
fun Medallion(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    spinning: Boolean = false
) {
    val colors = QiblaTheme.colors
    val angle by rememberInfiniteTransition(label = "medallion").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .drawBehind {
                val c = center
                val r = this.size.minDimension / 2f
                drawCircle(colors.gold.copy(alpha = 0.10f), radius = r, center = c)
                drawCircle(colors.gold.copy(alpha = 0.38f), radius = r - 0.5.dp.toPx(), center = c, style = Stroke(0.9.dp.toPx()))
                drawEightPointStar(
                    center = c,
                    halfSide = r * 0.5f,
                    color = colors.gold.copy(alpha = 0.22f),
                    strokeWidth = 0.8.dp.toPx(),
                    turn = if (spinning) angle else 0f
                )
            }
    ) {
        Icon(icon, contentDescription = null, tint = colors.gold, modifier = Modifier.size(size * 0.36f))
    }
}

/** Explains why we don't have a location yet and offers the one action that fixes it. */
@Composable
fun LocationNeededCard(
    locationState: LocationState,
    actions: AppActions,
    modifier: Modifier = Modifier
) {
    val colors = QiblaTheme.colors
    val status = locationState.status
    var showCitySearch by rememberSaveable { mutableStateOf(false) }

    MihrabCard(modifier = modifier, contentPadding = PaddingValues(24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Medallion(
                icon = if (status == LocationStatus.Failed) MihrabIcons.Crosshair else MihrabIcons.Pin,
                spinning = status == LocationStatus.Locating
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = status.title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.text,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = status.message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            GoldButton(
                text = status.actionLabel ?: "Finding your location…",
                icon = MihrabIcons.Pin,
                loading = status == LocationStatus.Locating,
                onClick = { actions.resolveLocation(locationState) }
            )
            QuietButton(text = "Choose a city instead", onClick = { showCitySearch = true })
        }
    }

    if (showCitySearch) {
        CitySearchSheet(
            onPick = { city ->
                actions.setManualLocation(city)
                showCitySearch = false
            },
            onDismiss = { showCitySearch = false }
        )
    }
}

// endregion
