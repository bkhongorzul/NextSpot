package com.nextspot.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect look (values from PLEOS_DESIGN.md), dark and light, following the system setting.
 * Dark is the main one (night demo). The mode is set once by [NsTheme]; every color below switches with it,
 * including colors read while drawing on a canvas.
 */
object NsColor {
    /** Set by [NsTheme] from the system setting. Compose state, so everything that reads a color updates. */
    var dark by mutableStateOf(true)
        internal set

    private fun pick(d: Long, l: Long) = Color(if (dark) d else l)
    private val ink get() = if (dark) Color.White else Color(0xFF131417)   // text/icon base

    // background / surface
    val BgPrimary get() = pick(0xFF1D1E21, 0xFFF7F8FA)
    val BgSecondary get() = pick(0xFF131417, 0xFFFFFFFF)       // deeper background (map floor)
    val SurfaceBasic get() = pick(0xFF1D1E21, 0xFFFFFFFF)
    val SurfaceHigh get() = pick(0xFF313236, 0xFFEDEEF2)       // emphasized card, popup
    val SurfaceAccent get() = pick(0xFF44464E, 0xFFE0E1E6)

    // gray steps (basic)
    val Gray300 get() = pick(0xFF686A72, 0xFFC7C9CE)

    // text / icon
    val Text get() = ink
    val TextSecondary get() = ink.copy(alpha = 0.84f)
    val TextTertiary get() = ink.copy(alpha = 0.64f)
    val TextQuaternary get() = ink.copy(alpha = 0.32f)

    // dividers
    val Divider2 get() = ink.copy(alpha = 0.10f)

    // buttons
    val ButtonFilled get() = pick(0xFFE0E1E6, 0xFF44464E)
    val ButtonFilledPressed get() = pick(0xFFC7C9CE, 0xFF313236)
    val OnButtonFilled get() = pick(0xFF131417, 0xFFFFFFFF)
    val ButtonBasic get() = ink.copy(alpha = 0.05f)
    val ButtonBasicPressed get() = ink.copy(alpha = 0.10f)

    // meaning colors
    val Active get() = pick(0xFF00E074, 0xFF02C265)            // spot confirmed, signal on
    val Positive get() = pick(0xFF0082FF, 0xFF0064FF)          // route, guidance
    val Likely get() = Color(0xFFFFA202)                       // spot about to open
    val Red get() = pick(0xFFFF4339, 0xFFF62E24)               // cancelled / withdrawn

    // cars in the lot drawing
    val ParkedCar get() = pick(0xFF313236, 0xFFE0E1E6)         // a bit lighter than the floor: a full lot
    val CarB get() = pick(0xFFE0E1E6, 0xFF44464E)              // clearly different from parked cars
    val CarA get() = pick(0xFFA4A8AE, 0xFF8A8C94)              // the leaving car: mid gray
    val Glass get() = pick(0xFF0B0C0E, 0xFF2A2C33)             // car windows
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

/** Filled button: the main action. */
@Composable
fun FilledButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: BtnSize = BtnSize.Medium) =
    NsButton(label, onClick, modifier, size, NsColor.ButtonFilled, NsColor.ButtonFilledPressed, NsColor.OnButtonFilled)

/** Basic button: faint surface (everything secondary). */
@Composable
fun BasicButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: BtnSize = BtnSize.Medium) =
    NsButton(label, onClick, modifier, size, NsColor.ButtonBasic, NsColor.ButtonBasicPressed, NsColor.Text)

/** 64-high button with a canvas-drawn icon and a text label next to it (no emoji, no icon font). */
@Composable
fun NsIconTextButton(
    label: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: DrawScope.(Color) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) NsColor.ButtonBasicPressed else NsColor.ButtonBasic)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(28.dp)) { icon(NsColor.Text) }
        Spacer(Modifier.width(12.dp))
        Text(label, fontSize = NsText.Label, fontWeight = FontWeight.Bold, color = NsColor.Text, maxLines = 1)
    }
}

/**
 * Asta Sans, loaded by name at runtime so the app still builds and runs if the files are missing
 * (then the default font is used). Files: app/src/main/res/font/asta_sans_regular / _medium / _bold .ttf
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

/** true = always dark, whatever the system setting is. */
private const val FORCE_DARK = true

/** Wraps the demo screen: follows the system light/dark setting and applies the Pleos font. */
@Composable
fun NsTheme(content: @Composable () -> Unit) {
    // Dark is forced for now (night demo). To follow the system light/dark setting again, use isSystemInDarkTheme().
    NsColor.dark = FORCE_DARK || isSystemInDarkTheme()   // set before the content reads any color
    val family = rememberNsFontFamily()
    ProvideTextStyle(LocalTextStyle.current.merge(TextStyle(fontFamily = family)), content)
}
