package com.taslim.qiblafinder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.taslim.qiblafinder.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int, style: FontStyle = FontStyle.Normal) = Font(
    resId = res,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

/** Cormorant Garamond: headings, times and numbers. */
val Serif = FontFamily(
    variable(R.font.cormorant_garamond, 300),
    variable(R.font.cormorant_garamond, 400),
    variable(R.font.cormorant_garamond, 500),
    variable(R.font.cormorant_garamond, 600),
    variable(R.font.cormorant_garamond_italic, 300, FontStyle.Italic),
    variable(R.font.cormorant_garamond_italic, 400, FontStyle.Italic),
)

/** Manrope: body copy, labels and controls. */
val Sans = FontFamily(
    variable(R.font.manrope, 400),
    variable(R.font.manrope, 500),
    variable(R.font.manrope, 600),
    variable(R.font.manrope, 700),
)

/** Amiri: Arabic prayer names and verses. */
val Arabic = FontFamily(Font(R.font.amiri_regular, FontWeight.Normal))

// Cormorant defaults to old-style figures; times and bearings read better lining
private const val LINING = "lnum"
private const val TABULAR = "tnum"

val Typography = Typography(
    // Qibla bearing
    displayLarge = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Light,
        fontSize = 76.sp,
        lineHeight = 76.sp,
        letterSpacing = (-0.01).em,
        fontFeatureSettings = LINING
    ),
    // Wordmark
    displayMedium = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Light,
        fontSize = 54.sp,
        lineHeight = 56.sp,
        letterSpacing = 0.01.em
    ),
    // Next prayer name
    displaySmall = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 42.sp,
        lineHeight = 46.sp,
        fontFeatureSettings = LINING
    ),
    // Screen titles
    headlineLarge = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Light,
        fontSize = 38.sp,
        lineHeight = 42.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Light,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    // Prayer times in the list, From/To on the route card
    headlineSmall = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontFeatureSettings = LINING
    ),
    // Stat values
    titleLarge = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = LINING
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = TABULAR
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    // Buttons
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.02.em
    ),
    // Navigation labels
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.06.em
    ),
    // Small caps labels above values
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.em
    )
)

/** Styles from the design that sit outside the Material scale. */
object MihrabType {
    /** Wide-tracked gold caption, e.g. "QIBLA DIRECTION". */
    val eyebrow = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.28.em
    )

    /** Italic serif guidance line, e.g. "Turn 12° to your right". */
    val guidance = TextStyle(
        fontFamily = Serif,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = LINING
    )

    val arabic = TextStyle(
        fontFamily = Arabic,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )
}
