package com.nextspot.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextspot.app.R
import com.nextspot.app.ui.theme.NsCar
import com.nextspot.app.ui.theme.NsTheme
import com.nextspot.app.ui.theme.NsType

private val SIDE = 68.dp

/**
 * Page frame of the real Car A / Car B screens: black window, "NextSpot" mark at the top left and a quiet
 * way back at the top right. [left] replaces the mark (e.g. the "320 m to destination" line).
 */
@Composable
internal fun CarFrame(onBack: () -> Unit, right: (@Composable () -> Unit)? = null, content: @Composable BoxScope.() -> Unit) {
    NsTheme {
        NsWindow(NsCar.bg) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(112.dp).padding(horizontal = SIDE - 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // plain way back, top left: arrow + "처음으로"
                    Row(
                        Modifier.clip(NsCar.shape).clickable(role = Role.Button, onClick = onBack).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NsIcon(NsPaths.BACK, NsCar.text2, 28.dp, strokeWidth = 2.2f)
                        Text(stringResource(R.string.car_back), style = NsType.body.copy(fontSize = 28.sp), color = NsCar.text2)
                    }
                    Spacer(Modifier.weight(1f))
                    right?.invoke()
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(start = SIDE, end = SIDE, bottom = 48.dp), content = content)
            }
        }
    }
}

@Composable
internal fun Eyebrow(text: String) =
    Text(text, style = NsType.tag.copy(fontSize = 24.sp, lineHeight = 30.sp), color = NsCar.text3)

@Composable
internal fun Headline(text: String, center: Boolean = false, modifier: Modifier = Modifier) =
    Text(
        text, modifier = modifier,
        style = NsType.logo.copy(fontSize = 60.sp, lineHeight = 74.sp, lineBreak = NsType.keepAll),
        color = NsCar.text, textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )

@Composable
internal fun SubText(text: String, center: Boolean = false, modifier: Modifier = Modifier) =
    Text(
        text, modifier = modifier,
        style = NsType.caption.copy(fontSize = 32.sp, lineHeight = 44.sp, lineBreak = NsType.keepAll),
        color = NsCar.text2, textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )

/** Dark card: 1 dp line, 10 dp corners. */
@Composable
internal fun CarBox(modifier: Modifier = Modifier, pad: Dp = 44.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(
        modifier.fillMaxWidth().clip(NsCar.shape).background(NsCar.card).border(1.dp, NsCar.border, NsCar.shape).padding(pad),
        content = content,
    )

/** The only fully round thing: a status tag. */
@Composable
internal fun Pill(text: String, cancel: Boolean = false) =
    Box(
        Modifier.clip(NsCar.pill).background(if (cancel) NsCar.cancelDim else NsCar.accentDim)
            .padding(horizontal = 30.dp, vertical = 14.dp),
    ) {
        Text(text, style = NsType.bodyBold.copy(fontSize = 26.sp), color = if (cancel) NsCar.cancel else NsCar.accent)
    }

/** Small underlined touch text: the quiet "or tap" way next to the voice. */
@Composable
internal fun TextLink(text: String, onClick: () -> Unit, color: Color = NsCar.text2) =
    Text(
        text,
        style = NsType.body.copy(fontSize = 28.sp, lineHeight = 36.sp, textDecoration = TextDecoration.Underline),
        color = color,
        modifier = Modifier.clip(NsCar.shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp),
    )

/** Simulator control (the real car sends this by itself). Quiet outlined button. */
@Composable
internal fun SimButton(label: String, onClick: () -> Unit) =
    Text(
        label, style = NsType.body.copy(fontSize = 26.sp), color = NsCar.text2,
        modifier = Modifier.clip(NsCar.shape).border(1.dp, NsCar.border, NsCar.shape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 28.dp, vertical = 20.dp),
    )

/** The car mark: a rounded body with two lines (like the design's car icon). */
@Composable
internal fun CarGlyph(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val st = Stroke(2f * u, cap = StrokeCap.Round)
        drawRoundRect(color, Offset(6f * u, 2.5f * u), Size(12f * u, 19f * u), CornerRadius(3.5f * u), style = st)
        drawLine(color, Offset(8.5f * u, 9f * u), Offset(15.5f * u, 9f * u), 2f * u, StrokeCap.Round)
        drawLine(color, Offset(8.5f * u, 14f * u), Offset(15.5f * u, 14f * u), 2f * u, StrokeCap.Round)
    }
}

/** Big round status mark: ring + check (waiting), filled + check (done), or grey + red cross (cancelled). */
internal enum class MarkKind { RING, FILLED, CANCEL }

@Composable
internal fun StatusMark(kind: MarkKind, size: Dp = 160.dp) {
    val bg = if (kind == MarkKind.FILLED) NsCar.accent else NsCar.card
    val ring = when (kind) { MarkKind.RING -> NsCar.accent; MarkKind.FILLED -> NsCar.accent; MarkKind.CANCEL -> NsCar.border }
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).border(3.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            MarkKind.CANCEL -> NsIcon(NsPaths.X, NsCar.cancel, size * 0.27f, strokeWidth = 2.4f)
            MarkKind.FILLED -> NsIcon(NsPaths.CHECK, NsCar.onAccent, size * 0.36f, strokeWidth = 2.8f)
            MarkKind.RING -> NsIcon(NsPaths.CHECK, NsCar.accent, size * 0.36f, strokeWidth = 2.8f)
        }
    }
}

/** Small yellow check disc (a sensor that has fired). Empty grey ring while it has not. */
@Composable
internal fun CheckDisc(on: Boolean, size: Dp = 56.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (on) NsCar.accent else NsCar.cardHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (on) NsIcon(NsPaths.CHECK, NsCar.onAccent, size * 0.5f, strokeWidth = 3f)
    }
}

/** Voice mark: yellow mic disc with soft rings that keep pulsing (the car is waiting for a voice). */
@Composable
internal fun MicBadge(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "mic").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "t",
    )
    Box(modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r0 = 56.dp.toPx()
            val r1 = size.minDimension / 2f
            drawCircle(NsCar.accent.copy(alpha = 0.16f), r1, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(NsCar.accent.copy(alpha = 0.26f), (r0 + r1) / 2f, c, style = Stroke(1.5.dp.toPx()))
            val rr = r0 + (r1 - r0) * t
            drawCircle(NsCar.accent.copy(alpha = (1f - t) * 0.5f), rr, c, style = Stroke(2.dp.toPx()))
        }
        Box(
            Modifier.size(112.dp).clip(CircleShape).background(NsCar.accentDim).border(3.dp, NsCar.accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) { NsMic(NsCar.accent, 52.dp, strokeWidth = 1.8f) }
    }
}

/** Searching: three rings around the car mark, one ring spreading outwards like a radar. */
@Composable
internal fun Radar(modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "radar").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "t",
    )
    Box(modifier.size(320.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val rOut = size.minDimension / 2f
            val rIn = 60.dp.toPx()
            drawCircle(NsCar.accent.copy(alpha = 0.14f), rOut, c, style = Stroke(1.5.dp.toPx()))
            drawCircle(NsCar.accent.copy(alpha = 0.30f), (rOut + rIn) / 2f + 8.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
            drawCircle(NsCar.accent.copy(alpha = 0.60f), rIn, c, style = Stroke(1.5.dp.toPx()))
            val rr = rIn + (rOut - rIn) * t
            drawCircle(NsCar.accent.copy(alpha = (1f - t) * 0.55f), rr, c, style = Stroke(2.dp.toPx()))
        }
        CarGlyph(NsCar.accent, 44.dp)
    }
}

/** Small spinning ring with a yellow arc ("waiting for the next signal"). */
@Composable
internal fun Spinner(size: Dp = 36.dp) {
    val a by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "a",
    )
    Canvas(Modifier.size(size)) {
        val w = 4.dp.toPx()
        val inset = w / 2f
        val s = Size(this.size.width - w, this.size.height - w)
        drawArc(NsCar.border, 0f, 360f, false, Offset(inset, inset), s, style = Stroke(w))
        drawArc(NsCar.accent, a, 80f, false, Offset(inset, inset), s, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Spinner or icon + one line of grey text. */
@Composable
internal fun HintRow(text: String, modifier: Modifier = Modifier, icon: (@Composable () -> Unit)? = null) =
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (icon != null) icon() else Spinner()
        Text(text, style = NsType.bodyBold.copy(fontSize = 28.sp), color = NsCar.text2)
    }

/** Five steps as a line: done = yellow dot, current = car mark in a ring, next = empty ring. */
@Composable
internal fun StepTrack(current: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..4) {
            when {
                i < current -> Box(Modifier.size(30.dp).clip(CircleShape).background(NsCar.accent))
                i == current -> Box(
                    Modifier.size(84.dp).clip(CircleShape).background(NsCar.card).border(3.dp, NsCar.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { CarGlyph(NsCar.accent, 38.dp) }
                else -> Box(Modifier.size(30.dp).clip(CircleShape).border(3.dp, NsCar.border, CircleShape))
            }
            if (i < 4) Box(
                Modifier.weight(1f).height(4.dp).padding(horizontal = 6.dp)
                    .background(if (i < current) NsCar.accent else NsCar.border),
            )
        }
    }
}

@Composable
internal fun CenterColumn(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content)
