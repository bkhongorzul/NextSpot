package com.nextspot.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import com.nextspot.app.ui.theme.NsColors

/** Line icons from docs/design/DESIGN.md section 9 (viewBox 0 0 24 24). */
object NsPaths {
    const val SEARCH = "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14zM20 20l-3.5-3.5"
    const val DETECT = "M14 4H5v16h9M10 12h10M17 9l3 3-3 3"
    const val VOICE = "M4 10v4h4l5 4V6L8 10zM16 9a4 4 0 0 1 0 6M18.5 6.5a8 8 0 0 1 0 11"
    const val BRAKE = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM10 16V8h2.6a2.4 2.4 0 0 1 0 4.8H10"
    const val PARK = "M4 4h16v16H4zM10 16V8h3a2.5 2.5 0 0 1 0 5h-3"
    const val CLOCK = "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7v5l3 2"
    const val LOCK = "M6 11h12v9H6zM9 11V8a3 3 0 0 1 6 0v3"
    const val CHECK = "M5 12.5l4.5 4.5L19 7.5"
    const val X = "M6 6l12 12M18 6L6 18"
    const val PIN = "M12 22s7-6.2 7-12a7 7 0 0 0-14 0c0 5.8 7 12 7 12z"
    const val PIN_P = "M10 14V6.5h2.6a2.3 2.3 0 0 1 0 4.6H10"
    const val PLAY = "M7 4.5v15l13-7.5z"
    const val PAUSE = "M6 5h4v14H6zM14 5h4v14h-4z"
    const val RESET = "M4 12a8 8 0 1 0 2.4-5.7M4 4v5h5"
    const val MIC_STEM = "M5 11a7 7 0 0 0 14 0M12 18v3"
    const val ARROW = "M5 12h14M13 6l6 6-6 6"
    const val BACK = "M15 5l-7 7 7 7"
    const val CHEVRON_DOWN = "M6 9l6 6 6-6"
    const val CHEVRON_UP = "M6 15l6-6 6 6"
}

@Composable
private fun rememberPath(d: String): Path = remember(d) { PathParser().parsePathString(d).toPath() }

/** A stroked line icon drawn from an SVG path, [strokeWidth] in viewBox units (24 = full icon). */
@Composable
fun NsIcon(
    path: String, color: Color, size: Dp, modifier: Modifier = Modifier,
    strokeWidth: Float = 2f, filled: Boolean = false, square: Boolean = false,
) {
    val p = rememberPath(path)
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            if (filled) drawPath(p, color, style = Fill)
            else drawPath(
                p, color,
                style = Stroke(
                    strokeWidth,
                    cap = if (square) StrokeCap.Square else StrokeCap.Round,
                    join = if (square) StrokeJoin.Miter else StrokeJoin.Round,
                ),
            )
        }
    }
}

/** The yellow P pin that marks the searching car (and the start-screen logo). Filled pin, dark P. */
@Composable
fun NsPin(size: Dp, modifier: Modifier = Modifier, pinColor: Color = NsColors.main, pColor: Color = NsColors.onMain) {
    val pin = rememberPath(NsPaths.PIN)
    val p = rememberPath(NsPaths.PIN_P)
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            drawPath(pin, pinColor, style = Fill)
            drawPath(p, pColor, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Microphone icon: rounded capsule plus the stem. */
@Composable
fun NsMic(color: Color, size: Dp, modifier: Modifier = Modifier, strokeWidth: Float = 2f) {
    val stem = rememberPath(NsPaths.MIC_STEM)
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) {
            val st = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawRoundRect(color, Offset(9f, 3f), Size(6f, 11f), CornerRadius(3f), style = st)
            drawPath(stem, color, style = st)
        }
    }
}

