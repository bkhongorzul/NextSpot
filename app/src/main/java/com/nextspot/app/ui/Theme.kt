package com.nextspot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect look (values from PLEOS_DESIGN.md, dark mode, Hyundai theme).
 * Goal: look like the car's own apps. Gray steps for surfaces, white-alpha for text and dividers,
 * and Pleos meaning colors only for status.
 */
object NsColor {
    // background / surface
    val BgPrimary = Color(0xFF1D1E21)
    val BgSecondary = Color(0xFF131417)       // deeper background (map floor)
    val SurfaceBasic = Color(0xFF1D1E21)      // basic card
    val SurfaceHigh = Color(0xFF313236)       // emphasized card, popup
    val SurfaceAccent = Color(0xFF44464E)     // selected surface

    // gray steps (basic)
    val Gray00 = Color(0xFF131417)
    val Gray50 = Color(0xFF1D1E21)
    val Gray100 = Color(0xFF313236)
    val Gray200 = Color(0xFF44464E)
    val Gray300 = Color(0xFF686A72)
    val Gray400 = Color(0xFFA4A8AE)
    val Gray500 = Color(0xFFC7C9CE)
    val Gray600 = Color(0xFFE0E1E6)
    val Gray700 = Color(0xFFEDEEF2)
    val Gray900 = Color(0xFFFFFFFF)

    // text / icon (white with alpha)
    val Text = Color.White
    val TextSecondary = Color.White.copy(alpha = 0.84f)
    val TextTertiary = Color.White.copy(alpha = 0.64f)
    val TextQuaternary = Color.White.copy(alpha = 0.32f)

    // dividers
    val Divider1 = Color.White.copy(alpha = 0.05f)
    val Divider2 = Color.White.copy(alpha = 0.10f)
    val Divider3 = Color.White.copy(alpha = 0.20f)

    // buttons
    val ButtonFilled = Color(0xFFE0E1E6)
    val ButtonFilledPressed = Color(0xFFC7C9CE)
    val OnButtonFilled = Color(0xFF131417)
    val ButtonBasic = Color.White.copy(alpha = 0.05f)
    val ButtonBasicPressed = Color.White.copy(alpha = 0.10f)

    // meaning colors
    val Active = Color(0xFF00E074)            // spot confirmed, signal on
    val Positive = Color(0xFF0082FF)          // route, guidance
    val Likely = Color(0xFFFFA202)            // spot about to open
    val Red = Color(0xFFFF4339)               // cancelled / withdrawn
}

/** Text sizes: three steps only (title / body / label), from the Pleos type scale. */
object NsText {
    val Title: TextUnit = 36.sp               // Title/Medium
    val TitleLine: TextUnit = 44.sp
    val Body: TextUnit = 28.sp                // Body/Medium
    val BodyLine: TextUnit = 36.sp
    val Label: TextUnit = 24.sp               // Label/Medium
    val LabelLine: TextUnit = 28.sp
}

/** Corner radii: card 16, small element 12 (PLEOS_DESIGN.md section 4). */
val NsCard = RoundedCornerShape(16.dp)
val NsChip = RoundedCornerShape(12.dp)

/** Pleos button sizes: Medium 80 / radius 24, Small 64 / radius 16. */
enum class BtnSize(val height: Dp, val minWidth: Dp, val padH: Dp, val radius: Dp, val text: TextUnit) {
    Medium(80.dp, 120.dp, 32.dp, 24.dp, NsText.Body),
    Small(64.dp, 100.dp, 24.dp, 16.dp, NsText.Label),
}

@Composable
private fun NsButton(
    label: String, onClick: () -> Unit, modifier: Modifier, size: BtnSize,
    bg: Color, bgPressed: Color, fg: Color,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .height(size.height)
            .defaultMinSize(minWidth = size.minWidth)
            .clip(RoundedCornerShape(size.radius))
            .background(if (pressed) bgPressed else bg)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.padH),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = size.text, fontWeight = FontWeight.Bold, color = fg, maxLines = 1) }
}

/** Filled button: light gray with dark text (the main action). */
@Composable
fun FilledButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: BtnSize = BtnSize.Medium) =
    NsButton(label, onClick, modifier, size, NsColor.ButtonFilled, NsColor.ButtonFilledPressed, NsColor.OnButtonFilled)

/** Basic button: white 5% surface (everything secondary). */
@Composable
fun BasicButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: BtnSize = BtnSize.Medium) =
    NsButton(label, onClick, modifier, size, NsColor.ButtonBasic, NsColor.ButtonBasicPressed, NsColor.Text)

/** 64x64 icon button (radius 16) with an icon drawn on a canvas, so no emoji or icon font is needed. */
@Composable
fun NsIconButton(
    description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: DrawScope.(Color) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) NsColor.ButtonBasicPressed else NsColor.ButtonBasic)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size(28.dp).padding(0.dp)) { icon(NsColor.Text) } }
}

/**
 * Asta Sans, loaded by name at runtime so the app builds and runs even when the font files are missing
 * (then the default font is used). Put the TTFs in app/src/main/res/font/ with lowercase names:
 *   asta_sans_regular.ttf, asta_sans_medium.ttf (optional), asta_sans_bold.ttf
 */
@Composable
private fun rememberNsFontFamily(): FontFamily {
    val ctx = LocalContext.current
    return remember(ctx) {
        fun res(vararg names: String): Int = names.firstNotNullOfOrNull { n ->
            ctx.resources.getIdentifier(n, "font", ctx.packageName).takeIf { it != 0 }
        } ?: 0
        val fonts = listOf(
            res("asta_sans_regular", "astasans_regular", "asta_sans") to FontWeight.Normal,
            res("asta_sans_medium", "astasans_medium") to FontWeight.Medium,
            res("asta_sans_bold", "astasans_bold") to FontWeight.Bold,
        ).filter { it.first != 0 }.map { (id, w) -> Font(id, w) }
        if (fonts.isEmpty()) FontFamily.Default else FontFamily(fonts)
    }
}

/** Wraps the demo screen so every Text uses the Pleos font (or the default if the files are missing). */
@Composable
fun NsTheme(content: @Composable () -> Unit) {
    val family = rememberNsFontFamily()
    ProvideTextStyle(LocalTextStyle.current.merge(TextStyle(fontFamily = family)), content)
}
