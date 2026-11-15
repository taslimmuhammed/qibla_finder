package com.taslim.qiblafinder.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// The design is a single midnight look, so there is no light scheme
private val MihrabColors = darkColorScheme(
    primary = Gold,
    onPrimary = Midnight,
    primaryContainer = Color(0xFF2A281E),
    onPrimaryContainer = GoldLight,
    secondary = GoldLight,
    onSecondary = Midnight,
    secondaryContainer = Color(0xFF2A281E),
    onSecondaryContainer = GoldLight,
    tertiary = Gold,
    background = Midnight,
    onBackground = Ivory,
    surface = Midnight700,
    onSurface = Ivory,
    surfaceVariant = Midnight600,
    onSurfaceVariant = Stone,
    surfaceContainerLowest = Midnight,
    surfaceContainerLow = Midnight800,
    surfaceContainer = Midnight700,
    surfaceContainerHigh = Midnight600,
    surfaceContainerHighest = Midnight500,
    inverseSurface = Ivory,
    inverseOnSurface = Midnight,
    outline = Gold.copy(alpha = 0.24f),
    outlineVariant = Gold.copy(alpha = 0.12f),
    scrim = Color.Black,
    error = Error,
)

/** Design tokens that don't map onto a Material role. */
@Immutable
data class QiblaColors(
    val gold: Color = Gold,
    val goldLight: Color = GoldLight,
    val goldDeep: Color = GoldDeep,
    val text: Color = Ivory,
    val textSoft: Color = Parchment,
    val textMuted: Color = Stone,
    val textFaint: Color = Ash,
    /** Hairlines between rows. */
    val hairline: Color = Gold.copy(alpha = 0.10f),
    /** Borders on cards and round buttons. */
    val border: Color = Gold.copy(alpha = 0.24f),
    /** Translucent fill behind round icon buttons and pills. */
    val chip: Color = Midnight600.copy(alpha = 0.72f),
    val card: Color = Midnight700,
    val glow: Color = DeepGreen,
    val warning: Color = Warning,
)

val LocalQiblaColors = staticCompositionLocalOf { QiblaColors() }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun QiblaFinderTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalQiblaColors provides QiblaColors()) {
        MaterialTheme(
            colorScheme = MihrabColors,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}

object QiblaTheme {
    val colors: QiblaColors
        @Composable get() = LocalQiblaColors.current
}
