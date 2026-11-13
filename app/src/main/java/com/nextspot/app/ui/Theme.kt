package com.nextspot.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One place for the screen's colors and text sizes.
 * Dark background (car display), ONE accent color (mint). Warn / Danger are only for status
 * (spot pending / cancelled), not decoration.
 */
object NsColor {
    // backgrounds / surfaces
    val Bg = Color(0xFF0E1216)
    val Surface = Color(0xFF171C22)          // cards, panels
    val SurfaceHigh = Color(0xFF242B33)      // secondary buttons, unlit chips
    val Outline = Color(0xFF6B7782)          // button borders
    val Asphalt = Color(0xFF1C2229)          // parking-lot drawing background
    val LotLine = Color(0xFF55606B)          // parking lines, lane dashes

    // text
    val Text = Color(0xFFF2F4F5)
    val TextDim = Color(0xFFA3ADB5)

    // the accent (Car B, primary buttons, lit chips, confirmed spot)
    val Accent = Color(0xFF2DD4A7)
    val OnAccent = Color(0xFF04211A)

    // status only
    val Warn = Color(0xFFF2B33D)             // spot likely / reserved
    val Danger = Color(0xFFEF5B5B)           // offer withdrawn

    // cars in the lot drawing
    val CarA = Color(0xFFE6EAED)
    val CarB = Accent
    val CarParked = Color(0xFF47525D)
    val Glass = Color(0xFF0E1216)

    /** Translucent version of a surface, for things floating over the map. */
    fun overlay(c: Color, a: Float = 0.92f) = c.copy(alpha = a)
}

/** Text sizes (car screen: big). */
object NsText {
    val Small: TextUnit = 16.sp
    val Label: TextUnit = 18.sp
    val Body: TextUnit = 22.sp
    val Chip: TextUnit = 20.sp
    val Button: TextUnit = 22.sp
    val Title: TextUnit = 30.sp
    val Headline: TextUnit = 34.sp
    val Answer: TextUnit = 36.sp
}

/** Filled accent button (main action). */
@Composable
fun PrimaryButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier, fontSize: TextUnit = NsText.Button,
) {
    Button(
        onClick = onClick, modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NsColor.Accent, contentColor = NsColor.OnAccent),
    ) { Text(label, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1) }
}

/** Clearly visible secondary button: raised surface, light text, visible border. */
@Composable
fun SecondaryButton(
    label: String, onClick: () -> Unit, modifier: Modifier = Modifier, fontSize: TextUnit = NsText.Button,
) {
    Button(
        onClick = onClick, modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, NsColor.Outline),
        colors = ButtonDefaults.buttonColors(containerColor = NsColor.SurfaceHigh, contentColor = NsColor.Text),
    ) { Text(label, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1) }
}
