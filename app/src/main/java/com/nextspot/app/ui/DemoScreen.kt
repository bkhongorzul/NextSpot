package com.nextspot.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nextspot.app.car.Speaker
import com.nextspot.app.ui.DemoEngine.Phase

/**
 * One-screen demo. Everything here is driven by DemoEngine, which runs the real logic
 * (car signals -> DepartureDetector -> Firebase -> Car B). The screen only draws the state.
 * Design / text: teammate. Logic: DemoEngine.kt.
 */
private enum class SpotLook { NONE, LIKELY, RESERVED, CONFIRMED, TAKEN, CANCELLED }

private data class View(
    val title: String, val caption: String,
    val look: SpotLook, val route: Boolean,
    val bX: Float, val bY: Float, val aGone: Float,
)

// ---- parking-lot drawing layout (fractions of the drawing area) ----
// Top row of spots, a lane in the middle, bottom row of spots: the whole area is used evenly.
private const val LOT_MARGIN = 0.03f
private const val LOT_SPOT_H = 0.30f
private const val LOT_LANE_Y = 0.585f                     // where cars drive (lower half of the lane)
private const val LOT_SPOT_N = 7
private const val LOT_LEFT = 0.10f
private const val LOT_WIDTH = 0.80f
private const val LOT_TARGET = 4                          // Car A's spot (top row)
private const val TARGET_X = LOT_LEFT + LOT_WIDTH * (LOT_TARGET + 0.5f) / LOT_SPOT_N
private const val SPOT_Y = LOT_MARGIN + LOT_SPOT_H / 2

private fun viewFor(p: Phase, meters: Int?): View = when (p) {
    Phase.IDLE -> View("대기 중", "시나리오 재생을 누르거나, 상세 버튼에서 차 신호를 직접 보내 보세요.",
        SpotLook.NONE, false, 0.08f, LOT_LANE_Y, 0f)
    Phase.SEARCHING -> View("1 · Car B 자리 찾는 중", "Car B가 근처 빈자리를 기다리고 있어요. 주차장은 꽉 찬 것처럼 보여요.",
        SpotLook.NONE, false, 0.15f, LOT_LANE_Y, 0f)
    Phase.LIKELY -> View("2 · 곧 나갈 차 감지", "Car A가 문을 닫고 목적지를 설정했어요. 앱이 클라우드에 \"곧 빌 자리\"로 등록했어요.",
        SpotLook.LIKELY, false, 0.22f, LOT_LANE_Y, 0f)
    Phase.OFFER -> View("3 · Car B에게 음성 제안", "클라우드에서 자리를 받았어요. ${meters ?: "-"}m 앞. 운전자는 \"네\"라고 말하면 돼요.",
        SpotLook.LIKELY, false, 0.30f, LOT_LANE_Y, 0f)
    Phase.RESERVED -> View("3 · Car B에게 예약됨", "다른 차는 이 자리를 받을 수 없어요. 내비가 그 자리로 안내 중이에요.",
        SpotLook.RESERVED, true, 0.40f, LOT_LANE_Y, 0f)
    Phase.CONFIRMED -> View("4 · 출발 확정", "Car A가 주차 브레이크를 풀고 출발했어요. 이제 자리가 확실히 비어요.",
        SpotLook.CONFIRMED, true, 0.55f, LOT_LANE_Y, 1f)
    Phase.TAKEN -> View("5 · 핸드오버 완료", "Car B가 바로 그 자리에 주차했어요. Car A는 크레딧 1점을 받아요.",
        SpotLook.TAKEN, false, TARGET_X, SPOT_Y, 1f)
    Phase.WITHDRAWN -> View("출발 취소 → 제안 철회", "Car A가 출발하지 않았어요. 제안을 자동으로 철회하고 Car B는 다른 자리를 찾아요.",
        SpotLook.CANCELLED, false, 0.75f, LOT_LANE_Y, 0f)
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

    val v = viewFor(e.phase, e.offerMeters)
    val bx by animateFloatAsState(v.bX, tween(1400), label = "bx")
    val by by animateFloatAsState(v.bY, tween(1400), label = "by")
    val aGone by animateFloatAsState(v.aGone, tween(1400), label = "a")
    val spotColor by animateColorAsState(
        when (v.look) {
            SpotLook.NONE -> Color.Transparent
            SpotLook.LIKELY, SpotLook.RESERVED -> NsColor.Warn
            SpotLook.CONFIRMED, SpotLook.TAKEN -> NsColor.Accent
            SpotLook.CANCELLED -> NsColor.Danger
        }, tween(600), label = "spot"
    )

    // Layout: the map fills the whole area, with a thin status bar on top and one floating card
    // at the bottom. Everything is sized from the area the app actually gets (Pleos keeps the
    // left part of the screen for its own car-info panel), so nothing here is a fixed screen size.
    val barBg = NsColor.overlay(NsColor.Bg, 0.92f)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(NsColor.Bg)) {
        val panelW = (maxWidth * 0.4f).coerceIn(420.dp, 640.dp)
        val topInset = with(density) { 132.dp.toPx() }      // status bar + "demo lot" label strip
        val bottomInset = with(density) { (if (e.phase == Phase.OFFER) 350.dp else 280.dp).toPx() }

        // 1) Map (demo parking-lot drawing) fills everything
        Canvas(Modifier.fillMaxSize()) {
            drawLot(spotColor, v.look, v.route, bx, by, aGone, topInset, bottomInset)
        }
        Text(
            "데모 주차장 (그림)", fontSize = NsText.Label, fontWeight = FontWeight.Bold, color = NsColor.TextDim,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 20.dp, top = 92.dp)
                .background(barBg, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
        )

        // 2) Thin status bar: app name, credits, signal chips, details toggle
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(barBg)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("NextSpot", fontSize = NsText.Title, fontWeight = FontWeight.Bold, color = NsColor.Text)
            Spacer(Modifier.width(16.dp))
            Text("크레딧 ${e.credits}", fontSize = NsText.Body, color = NsColor.Accent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(20.dp))
            SignalPanel(e, Modifier.weight(1f))
            Spacer(Modifier.width(20.dp))
            SecondaryButton(if (showPanel) "닫기" else "상세", { showPanel = !showPanel }, Modifier.height(60.dp))
        }

        // 3) Bottom: scenario buttons in the corner + one floating card
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 20.dp, bottom = 20.dp, end = if (showPanel) panelW + 32.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(
                    if (e.autoPlaying) "정지" else "시나리오 재생",
                    { if (e.autoPlaying) e.stopAutoPlay() else e.playScenario(cancelCase = false) },
                    Modifier.height(72.dp).width(240.dp),
                )
                SecondaryButton("취소 시나리오", { e.playScenario(cancelCase = true) }, Modifier.height(72.dp).width(240.dp))
            }
            if (e.phase == Phase.OFFER) {
                VoiceOfferCard(e)
            } else {
                Column(
                    Modifier.fillMaxWidth()
                        .background(NsColor.overlay(NsColor.Surface, 0.96f), RoundedCornerShape(18.dp)).padding(22.dp)
                ) {
                    Text(v.title, fontSize = NsText.Title, fontWeight = FontWeight.Bold, color = NsColor.Text)
                    Spacer(Modifier.height(8.dp))
                    Text(v.caption, fontSize = NsText.Body, color = NsColor.TextDim, lineHeight = NsText.Body * 1.35f)
                }
            }
        }

        // 4) Details side panel (hidden by default): log, SDK info, manual signals, reset
        if (showPanel) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 100.dp, end = 12.dp, bottom = 12.dp)
                    .fillMaxHeight().width(panelW)
                    .background(NsColor.overlay(NsColor.Surface, 0.97f), RoundedCornerShape(18.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("수동 신호 (차 신호 대신)", fontSize = NsText.Label, color = NsColor.TextDim)
                PanelButtonRow("B 탐색" to { e.startSearch() }, "B 주차" to { e.parkCarB() })
                PanelButtonRow("A 문" to { e.onDoorOpenedAndClosed() }, "A 내비" to { e.onNavigationSet() })
                PanelButtonRow("A 브레이크" to { e.onParkingBrakeReleased() }, "초기화" to { e.reset() })
                PanelButtonRow("메뉴로" to { onBack() })
                SdkInfo(e)
                Text("이벤트 로그", fontSize = NsText.Label, color = NsColor.TextDim)
                EventLog(e, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PanelButtonRow(vararg items: Pair<String, () -> Unit>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (label, action) ->
            SecondaryButton(label, action, Modifier.weight(1f).height(64.dp))
        }
        if (items.size == 1) Spacer(Modifier.weight(1f))
    }
}

private fun DrawScope.drawLot(
    spotColor: Color, look: SpotLook, route: Boolean, bx: Float, by: Float, aGone: Float,
    topInset: Float, bottomInset: Float,
) {
    // asphalt fills the whole screen; the lot itself is drawn between the top bar and the bottom card
    drawRect(NsColor.Asphalt)
    inset(0f, topInset, 0f, bottomInset) { drawLotBody(spotColor, look, route, bx, by, aGone) }
}

private fun DrawScope.drawLotBody(
    spotColor: Color, look: SpotLook, route: Boolean, bx: Float, by: Float, aGone: Float,
) {
    val w = size.width; val h = size.height
    val n = LOT_SPOT_N
    val spotW = w * LOT_WIDTH / n
    val spotH = h * LOT_SPOT_H
    val left = w * LOT_LEFT
    val topRowY = h * LOT_MARGIN
    val botRowY = h * (1f - LOT_MARGIN - LOT_SPOT_H)
    val laneY = h * LOT_LANE_Y

    // one car size for everybody (A, B, parked)
    val carWid = spotW * 0.62f
    val carLen = minOf(spotH * 0.78f, carWid * 1.9f)

    // spot lines for both rows
    for (k in 0..n) {
        val x = left + k * spotW
        drawLine(NsColor.LotLine, Offset(x, topRowY), Offset(x, topRowY + spotH), strokeWidth = 3f)
        drawLine(NsColor.LotLine, Offset(x, botRowY), Offset(x, botRowY + spotH), strokeWidth = 3f)
    }
    // lane center dashes
    drawLine(
        NsColor.LotLine, Offset(w * 0.04f, h * 0.5f), Offset(w * 0.96f, h * 0.5f),
        strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 24f)),
    )

    fun topCenter(k: Int) = Offset(left + k * spotW + spotW / 2, topRowY + spotH / 2)
    fun botCenter(k: Int) = Offset(left + k * spotW + spotW / 2, botRowY + spotH / 2)

    // parked cars: top row noses down (toward the lane), bottom row noses up; the target spot stays empty
    for (k in 0 until n) {
        if (k != LOT_TARGET) drawCar(topCenter(k), carLen, carWid, 90f, NsColor.CarParked)
        drawCar(botCenter(k), carLen, carWid, -90f, NsColor.CarParked)
    }

    // highlighted target spot
    val tc = topCenter(LOT_TARGET)
    if (look != SpotLook.NONE) {
        val tl = Offset(tc.x - spotW / 2 + 4, topRowY + 4)
        val sz = Size(spotW - 8, spotH - 8)
        drawRoundRect(spotColor.copy(alpha = 0.25f), tl, sz, CornerRadius(10f))
        drawRoundRect(spotColor, tl, sz, CornerRadius(10f), style = Stroke(6f))
    }

    // Car A: leaves its spot (nose first), turns into the lane, drives right and fades
    if (aGone < 0.99f) {
        val ax = tc.x + aGone * (w * 0.35f)
        val ay = tc.y + aGone * (laneY - tc.y)
        val alpha = 1f - aGone * 0.9f
        val deg = 90f * (1f - aGone)
        drawCar(Offset(ax, ay), carLen, carWid, deg, NsColor.CarA, alpha)
        drawLetter("A", Offset(ax, ay), alpha)
    }

    // route for Car B
    val bCenter = Offset(w * bx, h * by)
    if (route) {
        val p = Path().apply {
            moveTo(bCenter.x + carLen * 0.6f, bCenter.y)
            lineTo(tc.x, bCenter.y)
            lineTo(tc.x, tc.y + carLen * 0.5f)
        }
        drawPath(p, NsColor.Accent, style = Stroke(8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f))))
    }
    // Car B: faces right in the lane, turns up into the spot when it parks
    val turn = ((LOT_LANE_Y - by) / (LOT_LANE_Y - SPOT_Y)).coerceIn(0f, 1f)
    drawCar(bCenter, carLen, carWid, -90f * turn, NsColor.CarB)
    drawLetter("B", bCenter, 1f)
}

/**
 * Top-down car: rounded body, windshield, rear window, headlights.
 * Drawn facing right (+x), then rotated by [deg] around its center (90 = nose down, -90 = nose up).
 */
private fun DrawScope.drawCar(c: Offset, len: Float, wid: Float, deg: Float, body: Color, alpha: Float = 1f) {
    rotate(deg, pivot = c) {
        val x0 = c.x - len / 2; val y0 = c.y - wid / 2
        drawRoundRect(body.copy(alpha = alpha), Offset(x0, y0), Size(len, wid), CornerRadius(wid * 0.34f))
        val glass = NsColor.Glass.copy(alpha = 0.7f * alpha)
        // windshield (wider at the roof, narrower at the hood)
        drawPath(Path().apply {
            moveTo(c.x + len * 0.10f, c.y - wid * 0.40f)
            lineTo(c.x + len * 0.28f, c.y - wid * 0.32f)
            lineTo(c.x + len * 0.28f, c.y + wid * 0.32f)
            lineTo(c.x + len * 0.10f, c.y + wid * 0.40f)
            close()
        }, glass)
        // rear window
        drawPath(Path().apply {
            moveTo(c.x - len * 0.26f, c.y - wid * 0.36f)
            lineTo(c.x - len * 0.38f, c.y - wid * 0.30f)
            lineTo(c.x - len * 0.38f, c.y + wid * 0.30f)
            lineTo(c.x - len * 0.26f, c.y + wid * 0.36f)
            close()
        }, glass)
        // headlights
        val hl = Color.White.copy(alpha = 0.85f * alpha)
        val hlSize = Size(len * 0.05f, wid * 0.16f)
        drawRoundRect(hl, Offset(c.x + len / 2 - hlSize.width - len * 0.01f, c.y - wid * 0.40f), hlSize, CornerRadius(4f))
        drawRoundRect(hl, Offset(c.x + len / 2 - hlSize.width - len * 0.01f, c.y + wid * 0.24f), hlSize, CornerRadius(4f))
    }
}

private fun DrawScope.drawLetter(t: String, c: Offset, alpha: Float) {
    drawContext.canvas.nativeCanvas.drawText(
        t, c.x - 0f, c.y + 14f,
        android.graphics.Paint().apply {
            color = android.graphics.Color.argb(255, 4, 33, 26)   // NsColor.OnAccent
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            textSize = 40f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
    )
}
