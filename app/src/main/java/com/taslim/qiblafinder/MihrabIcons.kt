package com.taslim.qiblafinder

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Thin-stroke icons drawn to match the design. Each is a 24×24 outline; [androidx.compose.material3.Icon]
 * tints the stroke, so they follow the surrounding content color.
 */
object MihrabIcons {

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) =
        "M${cx - rx} ${cy}a$rx $ry 0 1 0 ${2 * rx} 0a$rx $ry 0 1 0 ${-2 * rx} 0z"

    private fun icon(name: String, vararg paths: String, strokeWidth: Float = 1.5f): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            paths.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()

    val Pin by lazy {
        icon("Pin", "M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11z", circle(12f, 10f, 2.3f))
    }

    val Sliders by lazy {
        icon("Sliders", "M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f))
    }

    val Compass by lazy { icon("Compass", circle(12f, 12f, 9f), "M15.5 8.5l-2 5-5 2 2-5z") }

    val Moon by lazy { icon("Moon", "M19.5 14.5A8 8 0 1 1 9.5 4.5a6.5 6.5 0 0 0 10 10z") }

    val Globe by lazy { icon("Globe", circle(12f, 12f, 9f), ellipse(12f, 12f, 3.8f, 9f), "M3 12h18") }

    val Bell by lazy { icon("Bell", "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0") }

    val BellOff by lazy {
        icon("BellOff", "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z", "M10 20.5a2 2 0 0 0 4 0", "M4 4l16 16")
    }

    val Back by lazy { icon("Back", "M14.5 5.5L8 12l6.5 6.5", strokeWidth = 1.8f) }

    val Chevron by lazy { icon("Chevron", "M9.5 6l6 6-6 6") }

    val Search by lazy { icon("Search", circle(11f, 11f, 6.5f), "M16 16l4.5 4.5") }

    val Close by lazy { icon("Close", "M6.5 6.5l11 11M17.5 6.5l-11 11") }

    val Crosshair by lazy {
        icon("Crosshair", circle(12f, 12f, 7f), circle(12f, 12f, 2.4f), "M12 2.5v2.5M12 19v2.5M2.5 12H5M19 12h2.5")
    }

    val Check by lazy { icon("Check", "M5 12.5l4.5 4.5L19 7.5", strokeWidth = 1.8f) }

    val Info by lazy { icon("Info", circle(12f, 12f, 9f), "M12 11v5.5M12 7.8v.2") }

    val Phone by lazy {
        icon(
            "Phone",
            "M8.5 3h7A1.5 1.5 0 0 1 17 4.5v15a1.5 1.5 0 0 1-1.5 1.5h-7A1.5 1.5 0 0 1 7 19.5v-15A1.5 1.5 0 0 1 8.5 3z",
            "M11 18h2"
        )
    }

    /** A figure-8 loop, for compass calibration. */
    val Loop by lazy {
        icon("Loop", "M12 12c-2.2-3-7-3-7 0s4.8 3 7 0 7-3 7 0-4.8 3-7 0z")
    }

    /** A horseshoe magnet, for "keep away from metal". */
    val Magnet by lazy {
        icon("Magnet", "M6 4v8a6 6 0 0 0 12 0V4h-4v8a2 2 0 0 1-4 0V4z", "M6 8h4M14 8h4")
    }

    /** Concentric arcs, for haptic feedback. */
    val Pulse by lazy {
        icon("Pulse", circle(12f, 12f, 2f), "M7.5 7.5a6.4 6.4 0 0 0 0 9M16.5 7.5a6.4 6.4 0 0 1 0 9M4.6 4.6a10.5 10.5 0 0 0 0 14.8M19.4 4.6a10.5 10.5 0 0 1 0 14.8")
    }

    val Refresh by lazy { icon("Refresh", "M19 12a7 7 0 1 1-2.05-4.95", "M19 4.5V8h-3.5") }
}
