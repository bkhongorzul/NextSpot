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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private fun viewFor(p: Phase, meters: Int?): View = when (p) {
    Phase.IDLE -> View("대기 중", "▶ 시나리오 재생을 누르거나, 아래 버튼으로 차 신호를 직접 보내 보세요.",
        SpotLook.NONE, false, 0.08f, 0.72f, 0f)
    Phase.SEARCHING -> View("1 · Car B 자리 찾는 중", "Car B가 근처 빈자리를 기다리고 있어요. 주차장은 꽉 찬 것처럼 보여요.",
        SpotLook.NONE, false, 0.15f, 0.72f, 0f)
    Phase.LIKELY -> View("2 · 곧 나갈 차 감지", "Car A가 문을 닫고 목적지를 설정했어요. 앱이 클라우드에 \"곧 빌 자리\"로 등록했어요.",
        SpotLook.LIKELY, false, 0.22f, 0.72f, 0f)
    Phase.OFFER -> View("3 · Car B에게 음성 제안", "클라우드에서 자리를 받았어요. ${meters ?: "-"}m 앞. 운전자는 \"네\"라고 말하면 돼요.",
        SpotLook.LIKELY, false, 0.30f, 0.72f, 0f)
    Phase.RESERVED -> View("3 · Car B에게 예약됨", "다른 차는 이 자리를 받을 수 없어요. 내비가 그 자리로 안내 중이에요.",
        SpotLook.RESERVED, true, 0.40f, 0.72f, 0f)
    Phase.CONFIRMED -> View("4 · 출발 확정", "Car A가 주차 브레이크를 풀고 출발했어요. 이제 자리가 확실히 비어요.",
        SpotLook.CONFIRMED, true, 0.55f, 0.72f, 1f)
    Phase.TAKEN -> View("5 · 핸드오버 완료", "Car B가 바로 그 자리에 주차했어요. Car A는 크레딧 1점을 받아요.",
        SpotLook.TAKEN, false, 0.585f, 0.22f, 1f)
    Phase.WITHDRAWN -> View("출발 취소 → 제안 철회", "Car A가 출발하지 않았어요. 제안을 자동으로 철회하고 Car B는 다른 자리를 찾아요.",
        SpotLook.CANCELLED, false, 0.75f, 0.72f, 0f)
}

@Composable
fun DemoScreen(speaker: Speaker, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val e = remember { DemoEngine(context, speaker, scope) }
    var showSdk by remember { mutableStateOf(false) }
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
            SpotLook.LIKELY, SpotLook.RESERVED -> Color(0xFFEF9F27)
            SpotLook.CONFIRMED, SpotLook.TAKEN -> Color(0xFF1D9E75)
            SpotLook.CANCELLED -> Color(0xFFE24B4A)
        }, tween(600), label = "spot"
    )

    Column(Modifier.fillMaxSize().background(Color(0xFF111418)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NextSpot", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.weight(1f))
            Text("Car A 크레딧 ${e.credits}", fontSize = 14.sp, color = Color(0xFF9FE1CB))
        }
        Spacer(Modifier.height(6.dp))
        SignalPanel(e)
        Spacer(Modifier.height(6.dp))

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxSize()) { drawLot(spotColor, v.look, v.route, bx, by, aGone) }
        }
        Spacer(Modifier.height(6.dp))

        if (e.phase == Phase.OFFER) {
            VoiceOfferCard(e)
        } else {
            Column(Modifier.fillMaxWidth().background(Color(0xFF1C2128), RoundedCornerShape(12.dp)).padding(12.dp)) {
                Text(v.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(v.caption, fontSize = 14.sp, color = Color(0xFFD3D1C7), lineHeight = 20.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        EventLog(e)
        if (showSdk) { Spacer(Modifier.height(6.dp)); SdkInfo(e) }
        Spacer(Modifier.height(8.dp))

        // Scenario controls
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { if (e.autoPlaying) e.stopAutoPlay() else e.playScenario(cancelCase = false) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D9E75)),
                modifier = Modifier.weight(1.3f),
            ) { Text(if (e.autoPlaying) "■ 정지" else "▶ 시나리오 재생") }
            OutlinedButton(onClick = { e.playScenario(cancelCase = true) }, modifier = Modifier.weight(1f)) { Text("취소 시나리오") }
            OutlinedButton(onClick = { e.reset() }, modifier = Modifier.weight(0.8f)) { Text("초기화") }
        }
        // Manual car signals (stand in for the real Pleos signals)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Small("B 탐색", Modifier.weight(1f)) { e.startSearch() }
            Small("A 문", Modifier.weight(1f)) { e.onDoorOpenedAndClosed() }
            Small("A 내비", Modifier.weight(1f)) { e.onNavigationSet() }
            Small("A 브레이크", Modifier.weight(1.2f)) { e.onParkingBrakeReleased() }
            Small("B 주차", Modifier.weight(1f)) { e.parkCarB() }
            Small("SDK", Modifier.weight(0.8f)) { showSdk = !showSdk }
            Small("메뉴", Modifier.weight(0.9f)) { onBack() }
        }
    }
}

@Composable
private fun Small(label: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 0.dp),
    ) { Text(label, fontSize = 11.sp, maxLines = 1) }
}

private fun DrawScope.drawLot(
    spotColor: Color, look: SpotLook, route: Boolean, bx: Float, by: Float, aGone: Float,
) {
    val w = size.width; val h = size.height
    // asphalt
    drawRoundRect(Color(0xFF2C2C2A), cornerRadius = CornerRadius(24f))
    // parking spots row (top)
    val n = 7
    val spotW = w * 0.8f / n
    val spotH = h * 0.42f
    val left = w * 0.1f
    val top = h * 0.04f
    val line = Color(0xFFB4B2A9)
    for (k in 0..n) {
        val x = left + k * spotW
        drawLine(line, Offset(x, top), Offset(x, top + spotH), strokeWidth = 4f)
    }
    val targetIdx = 4
    val parked = listOf(0, 1, 2, 3, 5, 6)
    val carW = spotW * 0.7f; val carH = spotH * 0.6f
    fun spotCenter(k: Int) = Offset(left + k * spotW + spotW / 2, top + spotH / 2)
    // other parked cars (grey)
    parked.forEach { k ->
        val c = spotCenter(k)
        drawRoundRect(Color(0xFF5F5E5A), Offset(c.x - carW / 2, c.y - carH / 2), Size(carW, carH), CornerRadius(18f))
    }
    // highlighted target spot
    val tc = spotCenter(targetIdx)
    if (look != SpotLook.NONE) {
        drawRoundRect(
            spotColor.copy(alpha = 0.25f), Offset(tc.x - spotW / 2 + 4, top + 4), Size(spotW - 8, spotH - 8), CornerRadius(10f)
        )
        drawRoundRect(
            spotColor, Offset(tc.x - spotW / 2 + 4, top + 4), Size(spotW - 8, spotH - 8), CornerRadius(10f), style = Stroke(6f)
        )
    }
    // lane dashes
    val laneY = h * 0.72f
    drawLine(Color(0xFFFAC775), Offset(w * 0.04f, laneY + h * 0.12f), Offset(w * 0.96f, laneY + h * 0.12f),
        strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 24f)))

    // Car A: leaves spot, drives right and fades
    if (aGone < 0.99f) {
        val ax = tc.x + aGone * (w * 0.35f)
        val ay = tc.y + aGone * (laneY - tc.y)
        drawRoundRect(
            Color(0xFFD85A30).copy(alpha = 1f - aGone * 0.9f),
            Offset(ax - carW / 2, ay - carH / 2), Size(carW, carH), CornerRadius(18f)
        )
        drawLetter("A", Offset(ax, ay), 1f - aGone)
    }
    // route for Car B
    val bCenter = Offset(w * bx, h * by)
    if (route) {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(bCenter.x + carW * 0.6f, bCenter.y)
            lineTo(tc.x, bCenter.y)
            lineTo(tc.x, tc.y + spotH * 0.4f)
        }
        drawPath(p, Color(0xFF5DCAA5), style = Stroke(8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f))))
    }
    // Car B
    val bw = if (by < 0.5f) carW else carW * 1.25f
    val bh = if (by < 0.5f) carH else carH * 0.75f
    drawRoundRect(Color(0xFF7F77DD), Offset(bCenter.x - bw / 2, bCenter.y - bh / 2), Size(bw, bh), CornerRadius(18f))
    drawLetter("B", bCenter, 1f)
}

private fun DrawScope.drawLetter(t: String, c: Offset, alpha: Float) {
    drawContext.canvas.nativeCanvas.drawText(
        t, c.x, c.y + 16f,
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            textSize = 44f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
    )
}
