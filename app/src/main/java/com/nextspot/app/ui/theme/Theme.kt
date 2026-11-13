package com.nextspot.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object NsShape {
    val window = RoundedCornerShape(12.dp)
    val button = RoundedCornerShape(8.dp)
    val mark = RoundedCornerShape(4.dp)
}

object NsDims {
    // app window (Pleos landscape window on a 2560x1440 Connect-S screen)
    val windowW = 1700.dp
    val windowH = 1184.dp
    val titleBar = 112.dp
    val stripH = 104.dp
    val sidePad = 40.dp

    val mapW = 1080.dp
    val mapH = 880.dp
    val bayW = 132.dp
    val bayH = 240.dp
    const val bayX0 = 78f        // first bay's left edge in the map
    const val bayStep = 132f
    const val bayTop = 48f
    const val bayBottomRow = 720f
    const val handoverBay = 4    // centre x 672, y 168
    const val laneDividerY = 500f

    val bigButtonH = 100.dp
    val smallButtonH = 64.dp
    val signalButtonH = 80.dp
    val stepRowH = 60.dp
    val stepMark = 40.dp

    /** Multiply every number above by this on a screen other than 2560x1440 (e.g. 0.75 on 1920x1080). */
    fun scale(appWindowWidth: Dp): Float = appWindowWidth.value / windowW.value
}

object NsMotion {
    /** One easing for every car move. Durations differ per leg so the speed feels steady (DESIGN.md section 8). */
    val drive = CubicBezierEasing(0.4f, 0f, 0.3f, 1f)

    /** Every car moves at this speed (dp per second), in a straight line, so nothing looks rushed. */
    const val carSpeed = 80f
    val logoLine = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
}

/**
 * Wraps every screen: dark Pleos colors, Asta Sans, and the Material3 colors that
 * the Car A / Car B screens use. Dark only (DESIGN.md section 0).
 */
@Composable
fun NsTheme(content: @Composable () -> Unit) {
    val family = rememberNsFontFamily()
    val scheme = darkColorScheme(
        primary = NsColors.main, onPrimary = NsColors.onMain,
        secondary = NsColors.surfaceHigh, onSecondary = NsColors.text,
        background = NsColors.appBg, onBackground = NsColors.text,
        surface = NsColors.appBg, onSurface = NsColors.text,
        surfaceVariant = NsColors.surface, onSurfaceVariant = NsColors.text2,
        surfaceContainerHighest = NsColors.surface, surfaceContainerHigh = NsColors.surface,
        surfaceContainer = NsColors.surface, surfaceContainerLow = NsColors.surface,
        outline = NsColors.text3, outlineVariant = NsColors.surfaceHigh,
    )
    CompositionLocalProvider(LocalNsType provides NsTextStyles(family)) {
        MaterialTheme(colorScheme = scheme, typography = nsMaterialTypography(family), content = content)
    }
}
