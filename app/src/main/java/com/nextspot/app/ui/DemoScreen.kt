package com.nextspot.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextspot.app.car.Speaker
import com.nextspot.app.ui.DemoEngine.Phase

/**
 * One-screen demo, styled after the Pleos Connect built-in apps (see PLEOS_DESIGN.md).
 * Main screen = only what a driver needs: the map, the spot marker, one bottom card.
 * Everything developer-facing (signals, flow, log, scenario buttons, SDK info) is in the details panel.
 * All state comes from DemoEngine; this file only draws it.
 */
private enum class SpotLook { NONE, LIKELY, RESERVED, CONFIRMED, TAKEN, CANCELLED }

// Demo content: the spot name shown to the driver (the demo has no real floor/number data).
private const val SPOT_NAME = "B2층 14번 자리"

private data class View(
    val title: String, val body: String,
    val look: SpotLook, val route: Boolean,
    val bX: Float, val bY: Float, val aGone: Float,
)

// ---- parking-lot drawing layout (fractions of the drawing area) ----
// One row of spots at the top, the lane directly below it.
private const val LOT_MARGIN = 0.04f
private const val LOT_SPOT_H = 0.50f
private const val LOT_LANE_H = 0.40f
private const val LOT_LANE_Y = LOT_MARGIN + LOT_SPOT_H + LOT_LANE_H * 0.60f   // cars drive in the lower half
private const val LOT_SPOT_N = 7
private const val LOT_LEFT = 0.04f
private const val LOT_WIDTH = 0.92f
private const val LOT_TARGET = 4
private const val TARGET_X = LOT_LEFT + LOT_WIDTH * (LOT_TARGET + 0.5f) / LOT_SPOT_N
private const val SPOT_Y = LOT_MARGIN + LOT_SPOT_H / 2

/** Driver-facing wording for each phase. */
private fun viewFor(p: Phase, meters: Int?): View = when (p) {
    Phase.IDLE -> View("근처 빈 자리를 알려 드려요", "목적지에 가까워지면 자동으로 찾아요.",
        SpotLook.NONE, false, 0.08f, LOT_LANE_Y, 0f)
    Phase.SEARCHING -> View("빈 자리를 찾는 중이에요", "곧 떠나는 차가 있으면 바로 알려 드릴게요.",
        SpotLook.NONE, false, 0.15f, LOT_LANE_Y, 0f)
    Phase.LIKELY -> View("앞 차가 곧 떠나요", "1분 안에 빌 예정이에요.",
        SpotLook.LIKELY, false, 0.22f, LOT_LANE_Y, 0f)
    Phase.OFFER -> View("이 자리로 갈까요?", "$SPOT_NAME · ${meters ?: "-"}m · 1분 안에 빌 예정",
        SpotLook.LIKELY, false, 0.30f, LOT_LANE_Y, 0f)
    Phase.RESERVED -> View("자리를 잡아 뒀어요", "${SPOT_NAME}로 안내할게요.",
        SpotLook.RESERVED, true, 0.40f, LOT_LANE_Y, 0f)
    Phase.CONFIRMED -> View("앞 차가 출발했어요", "자리가 비었어요. 안내를 따라 들어가세요.",
        SpotLook.CONFIRMED, true, 0.55f, LOT_LANE_Y, 1f)
    Phase.TAKEN -> View("주차를 마쳤어요", "자리를 비워 준 차에 크레딧 1점이 쌓여요.",
        SpotLook.TAKEN, false, TARGET_X, SPOT_Y, 1f)
    Phase.WITHDRAWN -> View("자리가 취소됐어요", "앞 차가 출발하지 않았어요. 다른 자리를 찾을게요.",
        SpotLook.CANCELLED, false, 0.75f, LOT_LANE_Y, 0f)
}

private fun lookColor(l: SpotLook): Color = when (l) {
    SpotLook.NONE, SpotLook.LIKELY, SpotLook.RESERVED -> NsColor.Likely
    SpotLook.CONFIRMED, SpotLook.TAKEN -> NsColor.Active
    SpotLook.CANCELLED -> NsColor.Red
}

@Composable
fun DemoScreen(speaker: Speaker, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val e = remember { DemoEngine(context, speaker, scope) }
    var showPanel by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { e.dispose() } }

    // Ask once for the microphone (for the "네" voice answer). Buttons still work without it.
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    // Everything on the map eases in and out (no jumps)
    val v = viewFor(e.phase, e.offerMeters)
    val ease = FastOutSlowInEasing
    val bx by animateFloatAsState(v.bX, tween(1400, easing = ease), label = "bx")
    val by by animateFloatAsState(v.bY, tween(1400, easing = ease), label = "by")
    val aGone by animateFloatAsState(v.aGone, tween(1400, easing = ease), label = "a")
    val routeAlpha by animateFloatAsState(if (v.route) 1f else 0f, tween(600, easing = ease), label = "route")
    val spotAlpha by animateFloatAsState(if (v.look == SpotLook.NONE) 0f else 1f, tween(600, easing = ease), label = "spotA")
    val spotColor by animateColorAsState(lookColor(v.look), tween(600, easing = ease), label = "spot")

    val margin = 24.dp
    val density = LocalDensity.current
    NsTheme { BoxWithConstraints(Modifier.fillMaxSize().background(NsColor.BgSecondary)) {
        // Half-width windows (about 1280) are fine: below ~1000 the details panel takes the full width.
        val narrow = maxWidth < 1000.dp
        val panelW = if (narrow) maxWidth - margin * 2 else (maxWidth * 0.45f).coerceIn(480.dp, 640.dp)
        val cardVisible = !(narrow && showPanel)

        // The map keeps clear of the top button, the bottom card (measured) and the open panel
        var cardH by remember { mutableIntStateOf(0) }
        val topInset = with(density) { (margin + 64.dp + 8.dp).toPx() }
        val bottomTarget = with(density) { (margin * 2).toPx() } + if (cardVisible) cardH else 0
        val rightTarget = if (showPanel && !narrow) with(density) { (panelW + margin * 2).toPx() } else 0f
        val bottomInset by animateFloatAsState(bottomTarget.toFloat(), tween(400, easing = ease), label = "bottomInset")
        val rightInset by animateFloatAsState(rightTarget, tween(400, easing = ease), label = "rightInset")

        // 1) Map
        Canvas(Modifier.fillMaxSize()) {
            drawLot(spotColor, spotAlpha, routeAlpha, bx, by, aGone, topInset, rightInset, bottomInset)
        }

        // 2) Details button: one small icon, top right
        NsIconButton(
            description = if (showPanel) "상세 닫기" else "상세 열기",
            onClick = { showPanel = !showPanel },
            modifier = Modifier.align(Alignment.TopEnd).padding(margin),
        ) { c ->
            val sw = 3.dp.toPx()
            val pad = sw / 2
            if (!showPanel) {
                for (i in 0..2) {
                    val y = size.height * (0.2f + 0.3f * i)
                    drawLine(c, Offset(pad, y), Offset(size.width - pad, y), sw, StrokeCap.Round)
                }
            } else {
                drawLine(c, Offset(pad, pad), Offset(size.width - pad, size.height - pad), sw, StrokeCap.Round)
                drawLine(c, Offset(size.width - pad, pad), Offset(pad, size.height - pad), sw, StrokeCap.Round)
            }
        }

        // 3) Bottom card: the only text a driver reads
        if (cardVisible) {
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .padding(start = margin, end = if (showPanel && !narrow) panelW + margin * 2 else margin, bottom = margin)
                    .widthIn(max = 1000.dp).fillMaxWidth()
                    .onSizeChanged { cardH = it.height }
                    .clip(NsCard).background(NsColor.SurfaceHigh).padding(28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(16.dp)
                            .background(if (v.look == SpotLook.NONE) NsColor.Gray300 else spotColor, CircleShape)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(v.title, fontSize = NsText.Title, lineHeight = NsText.TitleLine, fontWeight = FontWeight.Bold, color = NsColor.Text,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                // the card text stays short: at most 3 lines
                Text(v.body, fontSize = NsText.Body, lineHeight = NsText.BodyLine, color = NsColor.TextSecondary,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (e.phase == Phase.OFFER) {
                    Spacer(Modifier.height(24.dp))
                    OfferActions(e)
                }
            }
        }

        // 4) Details panel (hidden by default)
        if (showPanel) {
            DetailsPanel(
                e, onBack,
                Modifier.align(Alignment.TopEnd)
                    .padding(top = margin + 64.dp + 8.dp, end = margin, bottom = margin)
                    .fillMaxHeight().width(panelW),
            )
        }
    } }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(NsColor.Divider2))
}

/** Developer / demo controls and info. Not part of the driver's main screen. */
@Composable
private fun DetailsPanel(e: DemoEngine, onBack: () -> Unit, modifier: Modifier) {
    Column(
        modifier.clip(NsCard).background(NsColor.SurfaceHigh)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("상세", fontSize = NsText.Title, lineHeight = NsText.TitleLine, fontWeight = FontWeight.Bold, color = NsColor.Text, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Car A 크레딧", fontSize = NsText.Label, color = NsColor.TextTertiary)
                Text("${e.credits}", fontSize = NsText.Body, fontWeight = FontWeight.Bold, color = NsColor.Text)
            }
        }
        Text("데모 주차장 · 지도는 데모용 그림이에요", fontSize = NsText.Label, color = NsColor.TextTertiary)
        Divider()
        SignalSection(e)
        Divider()
        Text("시나리오", fontSize = NsText.Label, color = NsColor.TextTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            FilledButton(
                if (e.autoPlaying) "정지" else "시나리오 재생",
                { if (e.autoPlaying) e.stopAutoPlay() else e.playScenario(cancelCase = false) },
                Modifier.weight(1f), BtnSize.Small,
            )
            BasicButton("취소 시나리오", { e.playScenario(cancelCase = true) }, Modifier.weight(1f), BtnSize.Small)
        }
        Divider()
        Text("수동 신호 (차 신호 대신)", fontSize = NsText.Label, color = NsColor.TextTertiary)
        ButtonPair("B 탐색" to { e.startSearch() }, "B 주차" to { e.parkCarB() })
        ButtonPair("A 문" to { e.onDoorOpenedAndClosed() }, "A 내비" to { e.onNavigationSet() })
        ButtonPair("A 브레이크" to { e.onParkingBrakeReleased() }, "초기화" to { e.reset() })
        ButtonPair("메뉴로" to { onBack() }, null)
        Divider()
        SdkInfo(e)
        Divider()
        Text("이벤트 로그", fontSize = NsText.Label, color = NsColor.TextTertiary)
        EventLog(e, Modifier.height(320.dp))
    }
}

@Composable
private fun ButtonPair(a: Pair<String, () -> Unit>, b: Pair<String, () -> Unit>?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        BasicButton(a.first, a.second, Modifier.weight(1f), BtnSize.Small)
        if (b != null) BasicButton(b.first, b.second, Modifier.weight(1f), BtnSize.Small) else Spacer(Modifier.weight(1f))
    }
}

private fun DrawScope.drawLot(
    spotColor: Color, spotAlpha: Float, routeAlpha: Float, bx: Float, by: Float, aGone: Float,
    topInset: Float, rightInset: Float, bottomInset: Float,
) {
    drawRect(NsColor.BgSecondary)
    if (size.height - topInset - bottomInset < 80f || size.width - rightInset < 80f) return
    inset(0f, topInset, rightInset, bottomInset) { drawLotBody(spotColor, spotAlpha, routeAlpha, bx, by, aGone) }
}

private fun DrawScope.drawLotBody(
    spotColor: Color, spotAlpha: Float, routeAlpha: Float, bx: Float, by: Float, aGone: Float,
) {
    val w = size.width; val h = size.height
    // tall, narrow windows (portrait half screen) get fewer, wider spots so cars stay a good size
    val n = if (w < h * 1.1f) 4 else LOT_SPOT_N
    val targetIdx = if (n == LOT_SPOT_N) LOT_TARGET else 2
    val spotW = w * LOT_WIDTH / n
    val spotH = h * LOT_SPOT_H
    val left = w * TARGET_X - (targetIdx + 0.5f) * spotW   // keeps the target spot at TARGET_X
    val topRowY = h * LOT_MARGIN
    val laneTop = topRowY + spotH
    val laneH = h * LOT_LANE_H
    val laneY = h * LOT_LANE_Y

    // parked cars are plain soft blocks; the moving car is the navigation arrow
    val blockW = spotW * 0.66f
    val blockL = minOf(spotH * 0.80f, blockW * 2.15f)

    // thin spot lines and a faint lane line
    for (k in 0..n) {
        val x = left + k * spotW
        drawLine(NsColor.Divider2, Offset(x, topRowY), Offset(x, laneTop), strokeWidth = 2f)
    }
    drawLine(NsColor.Divider2, Offset(left, laneTop), Offset(left + n * spotW, laneTop), strokeWidth = 2f)
    val dashY = laneTop + laneH * 0.40f
    drawLine(
        NsColor.Divider2, Offset(w * 0.02f, dashY), Offset(w * 0.98f, dashY),
        strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 20f)),
    )

    fun topCenter(k: Int) = Offset(left + k * spotW + spotW / 2, topRowY + spotH / 2)
    val tc = topCenter(targetIdx)

    // parked cars: just slightly lighter than the floor. The target spot's car is Car A (drawn below).
    for (k in 0 until n) {
        if (k != targetIdx) drawBlock(topCenter(k), blockL, blockW, 90f, NsColor.SurfaceBasic, 1f)
    }

    // the one highlighted spot: status-colored outline
    if (spotAlpha > 0.01f) {
        val tl = Offset(tc.x - spotW / 2 + 6, topRowY + 6)
        val sz = Size(spotW - 12, spotH - 12)
        drawRoundRect(spotColor.copy(alpha = 0.12f * spotAlpha), tl, sz, CornerRadius(12f), style = Fill)
        drawRoundRect(spotColor.copy(alpha = spotAlpha), tl, sz, CornerRadius(12f), style = Stroke(4f))
    }

    // Car A: sits in the spot like the others, then leaves into the lane and fades
    if (aGone < 0.99f) {
        val ax = tc.x + aGone * (w * 0.35f)
        val ay = tc.y + aGone * (laneY - tc.y)
        drawBlock(Offset(ax, ay), blockL, blockW, 90f * (1f - aGone), NsColor.SurfaceBasic, 1f - aGone)
    }

    // route to the spot: blue
    val bCenter = Offset(w * bx, h * by)
    val arrow = blockW * 0.95f
    if (routeAlpha > 0.01f) {
        val p = Path().apply {
            moveTo(bCenter.x + arrow * 0.6f, bCenter.y)
            lineTo(tc.x, bCenter.y)
            lineTo(tc.x, tc.y + blockL * 0.25f)
        }
        drawPath(
            p, NsColor.Positive.copy(alpha = routeAlpha),
            style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    // Car B: navigation arrow, turns up into the spot when it parks
    val turn = ((LOT_LANE_Y - by) / (LOT_LANE_Y - SPOT_Y)).coerceIn(0f, 1f)
    drawNavArrow(bCenter, arrow, -90f * turn)
}

/** Soft rounded block (a parked car), [deg] 90 = standing in a spot, 0 = driving right. */
private fun DrawScope.drawBlock(c: Offset, len: Float, wid: Float, deg: Float, color: Color, alpha: Float) {
    rotate(deg, pivot = c) {
        drawRoundRect(
            color.copy(alpha = alpha), Offset(c.x - len / 2, c.y - wid / 2), Size(len, wid),
            CornerRadius(wid * 0.2f),
        )
    }
}

/** Navigation arrow (like a map's "you are here" pointer), pointing right before rotation. */
private fun DrawScope.drawNavArrow(c: Offset, s: Float, deg: Float) {
    drawCircle(NsColor.Positive.copy(alpha = 0.18f), radius = s * 0.85f, center = c)
    rotate(deg, pivot = c) {
        val p = Path().apply {
            moveTo(c.x + s * 0.55f, c.y)
            lineTo(c.x - s * 0.45f, c.y + s * 0.45f)
            lineTo(c.x - s * 0.20f, c.y)
            lineTo(c.x - s * 0.45f, c.y - s * 0.45f)
            close()
        }
        drawPath(p, NsColor.Positive)
        drawPath(p, NsColor.Text, style = Stroke(3f, join = StrokeJoin.Round))
    }
}
