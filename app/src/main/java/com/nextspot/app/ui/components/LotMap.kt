package com.nextspot.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nextspot.app.R
import com.nextspot.app.ui.DemoEngine.Phase
import com.nextspot.app.ui.theme.NsColors
import com.nextspot.app.ui.theme.NsDims
import com.nextspot.app.ui.theme.NsMotion
import com.nextspot.app.ui.theme.NsShape
import com.nextspot.app.ui.theme.NsType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot

private typealias Anim = Animatable<Float, AnimationVector1D>

/** How the handed-over bay looks in each phase (DESIGN.md section 7). */
private class Look(val color: Color, val dashed: Boolean, val width: Float, val icon: String, val tag: Int)

private fun lookOf(p: Phase): Look? = when (p) {
    Phase.LIKELY, Phase.OFFER -> Look(Color.White, true, 5f, NsPaths.CLOCK, R.string.tag_likely)
    Phase.RESERVED -> Look(NsColors.main, false, 5f, NsPaths.LOCK, R.string.tag_reserved)
    Phase.CONFIRMED -> Look(NsColors.ok, false, 5f, NsPaths.CHECK, R.string.tag_free)
    Phase.TAKEN -> Look(NsColors.ok, false, 5f, NsPaths.PARK, R.string.tag_parked)
    Phase.WITHDRAWN -> Look(NsColors.cancel, false, 3f, NsPaths.X, R.string.tag_cancelled)
    else -> null
}

/** Searching car: where it goes in each phase and how many seconds that takes (DESIGN.md section 8). */
private class Leg(val x: Float, val seconds: Float)

private fun searchLeg(p: Phase): Leg? = when (p) {
    Phase.IDLE -> Leg(120f, 0f)
    Phase.SEARCHING -> Leg(210f, 2.5f)
    Phase.LIKELY -> Leg(280f, 2.4f)
    Phase.OFFER -> Leg(340f, 1.6f)
    Phase.RESERVED -> Leg(460f, 2.6f)
    Phase.CONFIRMED -> Leg(560f, 2.4f)
    Phase.WITHDRAWN -> Leg(920f, 3.2f)
    Phase.TAKEN -> null
}

private const val LANE_Y = 620f
private const val BAY_X = 672f     // centre of the handed-over bay
private const val BAY_Y = 168f

/** The parking lot: 1080x880, drawn in dp exactly as in DESIGN.md section 6.2 / 6.3. */
@Composable
internal fun LotMap(phase: Phase, offerMeters: Int?, modifier: Modifier = Modifier, onSettled: (Phase) -> Unit = {}) {
    val look = lookOf(phase)
    val departing = phase == Phase.CONFIRMED || phase == Phase.TAKEN
    // One easing; the time of each leg is the DESIGN.md section 8 value (or distance / speed, see NsMotion).
    val ease = if (NsMotion.constantSpeed) LinearEasing else NsMotion.drive
    fun ms(seconds: Float, dx: Float, dy: Float) =
        (if (NsMotion.constantSpeed) hypot(dx, dy) / NsMotion.carSpeed * 1000f else seconds * 1000f).toInt().coerceAtLeast(1)

    // car positions, animated with one easing and a duration per leg (steady speed)
    val bx = remember { Animatable(searchLeg(phase)?.x ?: BAY_X) }
    val by = remember { Animatable(if (phase == Phase.TAKEN) BAY_Y else LANE_Y) }
    val brot = remember { Animatable(if (phase == Phase.TAKEN) 0f else 90f) }
    val ax = remember { Animatable(if (departing) 1180f else BAY_X) }
    val ay = remember { Animatable(if (departing) 420f else BAY_Y) }
    val arot = remember { Animatable(if (departing) 90f else 0f) }
    val aAlpha = remember { Animatable(if (departing) 0f else 1f) }
    var aMoving by remember { mutableStateOf(departing) }
    var bParked by remember { mutableStateOf(phase == Phase.TAKEN) }

    // searching car
    LaunchedEffect(phase) {
        val leg = searchLeg(phase)
        when {
            phase == Phase.IDLE -> {   // a new run: back to the start at once, no sliding backwards
                bParked = false
                bx.snapTo(120f); by.snapTo(LANE_Y); brot.snapTo(90f)
            }
            phase == Phase.TAKEN -> coroutineScope {   // two legs: in front of the bay, then into it
                bParked = false
                val leg1 = tween<Float>(ms(1.6f, BAY_X - bx.value, 420f - by.value), easing = ease)
                launch { bx.animateTo(BAY_X, leg1) }
                launch { brot.animateTo(0f, leg1) }
                by.animateTo(420f, leg1)
                by.animateTo(BAY_Y, tween(ms(1.6f, 0f, 420f - BAY_Y), easing = ease))
                bParked = true
                onSettled(phase)   // the searching car is in the bay: the closing card may appear
            }
            leg != null && leg.x >= bx.value -> {   // never roll backwards (e.g. after answering "no")
                val spec = tween<Float>(ms(leg.seconds, leg.x - bx.value, 0f), easing = ease)
                coroutineScope {
                    launch { by.animateTo(LANE_Y, spec) }
                    launch { brot.animateTo(90f, spec) }
                    bx.animateTo(leg.x, spec)
                }
                if (phase == Phase.WITHDRAWN) onSettled(phase)
            }
        }
    }

    // leaving car: three legs once the departure is confirmed
    LaunchedEffect(departing) {
        if (!departing) {
            aMoving = false
            ax.snapTo(BAY_X); ay.snapTo(BAY_Y); arot.snapTo(0f); aAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        aMoving = true
        ay.animateTo(370f, tween(ms(1.3f, 0f, 370f - BAY_Y), easing = ease))                       // back out
        coroutineScope {                                                    // turn into the upper lane
            val spec = tween<Float>(ms(1.1f, 830f - BAY_X, 420f - 370f), easing = ease)
            launch { ax.animateTo(830f, spec) }
            launch { arot.animateTo(90f, spec) }
            ay.animateTo(420f, spec)
        }
        coroutineScope {                                                    // drive off and fade
            val spec = tween<Float>(ms(1.9f, 1180f - 830f, 0f), easing = ease)
            launch { aAlpha.animateTo(0f, spec) }
            ax.animateTo(1180f, spec)
        }
    }

    val lineColor by animateColorAsState(look?.color ?: NsColors.surfaceHigh, tween(250), label = "bay")
    val tagBAlpha = animateFloatAsState(if (phase == Phase.TAKEN && !bParked) 0f else 1f, tween(300), label = "tagB")
    val tagAAlpha = animateFloatAsState(if (aMoving) 0f else 1f, tween(300), label = "tagA")
    val showRoute = phase == Phase.RESERVED || phase == Phase.CONFIRMED

    Box(modifier.size(NsDims.mapW, NsDims.mapH).clip(NsShape.button).background(NsColors.screen)) {
        // Layer 1 (never changes): bay lines, lane, parked cars. Cached as one layer, so while cars
        // move it is not drawn again. Parked cars are drawn as images with alpha, not as 13 separate views.
        val parked = ImageBitmap.imageResource(R.drawable.car_parked)
        Canvas(Modifier.fillMaxSize().graphicsLayer { }) {
            val u = 1.dp.toPx()
            val line = NsColors.surfaceHigh
            for (k in 0..6) {
                val x = (NsDims.bayX0 + NsDims.bayStep * k) * u
                for (top in listOf(NsDims.bayTop, NsDims.bayBottomRow)) {
                    drawRect(line, Offset(x, top * u), Size(3 * u, 240 * u))
                    if (k == 6) drawRect(line, Offset(x + 129 * u, top * u), Size(3 * u, 240 * u))
                }
            }
            drawLine(
                NsColors.surface, Offset(40 * u, 501.5f * u), Offset(1040 * u, 501.5f * u), 3 * u,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(9 * u, 9 * u)),
            )
            val src = IntSize(parked.width, parked.height)
            val dst = IntSize((64 * u).roundToInt(), (161 * u).roundToInt())
            for (k in 0..6) {
                val x = NsDims.bayX0 + NsDims.bayStep * k + 37
                if (k != NsDims.handoverBay) {
                    drawImage(parked, IntOffset.Zero, src, IntOffset((x * u).roundToInt(), ((NsDims.bayTop + 38) * u).roundToInt()), dst, alpha = 0.42f)
                }
                val cx = (x + 32) * u
                val cy = (NsDims.bayBottomRow + 38 + 80.5f) * u
                rotate(180f, Offset(cx, cy)) {
                    drawImage(parked, IntOffset.Zero, src, IntOffset((x * u).roundToInt(), ((NsDims.bayBottomRow + 38) * u).roundToInt()), dst, alpha = 0.42f)
                }
            }
        }

        // Layer 2 (changes with the phase): the handed-over bay's lines and the route
        Canvas(Modifier.fillMaxSize()) {
            val u = 1.dp.toPx()
            if (look != null) {
                val w = look.width * u
                bayEdge(lineColor, (606 + look.width / 2) * u, w, look.dashed, u)
                bayEdge(lineColor, (741 - look.width / 2) * u, w, look.dashed, u)
            }
            if (showRoute) {
                val hx = bx.value + 87f
                val dash = PathEffect.dashPathEffect(floatArrayOf(12 * u, 10 * u))
                if (hx < 675f) drawLine(NsColors.main, Offset(hx * u, 620 * u), Offset(675 * u, 620 * u), 6 * u, pathEffect = dash)
                drawLine(NsColors.main, Offset(672 * u, 290 * u), Offset(672 * u, 620 * u), 6 * u, pathEffect = dash)
            }
        }

        // status word next to the handed-over bay
        if (look != null) {
            Row(Modifier.offset(760.dp, 304.dp), verticalAlignment = Alignment.CenterVertically) {
                NsIcon(look.icon, look.color, 28.dp, strokeWidth = 2.4f, square = true)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(look.tag), style = NsType.status, color = look.color, maxLines = 1, softWrap = false)
                if ((phase == Phase.OFFER || phase == Phase.RESERVED) && offerMeters != null) {
                    Text(
                        " · ${offerMeters}m", style = NsType.status.copy(fontWeight = FontWeight.Normal),
                        color = NsColors.text, maxLines = 1, softWrap = false,
                    )
                }
            }
        }

        // cars: 180x180 box, the rotation only inside it
        CarBox(ax, ay, arot, aAlpha, R.drawable.car_leaving, 8, 6, 64, 161)
        CarBox(bx, by, brot, null, R.drawable.car_mine, 4, 8, 72, 141)

        // pin on the searching car: stays upright and follows the car
        Box(
            Modifier.size(48.dp).graphicsLayer {
                translationX = (bx.value - 24f).dp.toPx()
                translationY = (by.value - (if (brot.value > 45f) 40f else 86f) - 46f).dp.toPx()
            }
        ) { NsPin(48.dp) }

        CarTag(stringResource(R.string.leaving_car), NsColors.leavingCar, ax, ay, arot, tagAAlpha)
        CarTag(stringResource(R.string.searching_car), NsColors.main, bx, by, brot, tagBAlpha)
    }
}

private fun DrawScope.bayEdge(color: Color, x: Float, w: Float, dashed: Boolean, u: Float) {
    drawLine(
        color, Offset(x, 48 * u), Offset(x, 288 * u), w,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(w * 2f, w * 1.6f)) else null,
    )
}

@Composable
private fun CarBox(
    x: Anim, y: Anim, rot: Anim, alpha: Anim?, res: Int,
    imgX: Int, imgY: Int, imgW: Int, imgH: Int,
) {
    Box(
        Modifier
            .size(180.dp)
            .graphicsLayer {   // moved while drawing: no measure / layout per frame
                translationX = (x.value - 90f).dp.toPx()
                translationY = (y.value - 90f).dp.toPx()
                this.alpha = alpha?.value ?: 1f
            }
    ) {
        Box(Modifier.offset(50.dp, 3.dp).size(80.dp, 173.dp).graphicsLayer { rotationZ = rot.value }) {
            Image(
                painterResource(res), null,
                Modifier.offset(imgX.dp, imgY.dp).size(imgW.dp, imgH.dp),
                contentScale = ContentScale.FillBounds,
            )
        }
    }
}

/** Name tag: in a bay it sits at the top of the map, in the lane it sits under the car. */
@Composable
private fun CarTag(text: String, color: Color, x: Anim, y: Anim, rot: Anim, alpha: State<Float>) {
    Box(
        Modifier
            .size(180.dp, 28.dp)
            .graphicsLayer {
                val top = if (y.value < 290f) 10f else y.value + (if (rot.value > 45f) 46f else 88f)
                translationX = (x.value - 90f).dp.toPx()
                translationY = top.dp.toPx()
                this.alpha = alpha.value
            },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = NsType.tag, color = color, maxLines = 1, softWrap = false) }
}
