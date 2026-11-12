package com.nextspot.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextspot.app.car.Speaker
import kotlinx.coroutines.delay

/**
 * One-screen demo: a parking lot with Car A (leaving) and Car B (searching).
 * Each step = one real driving action that the Pleos car detects by itself.
 */
private data class Step(
    val title: String,
    val caption: String,
    val signal: String,
    val voice: String?,
    val spot: SpotLook,
    val showRoute: Boolean,
    val bX: Float, val bY: Float,      // Car B position (0..1 of canvas)
    val aGone: Float,                  // 0 = Car A parked, 1 = Car A drove away
    val credits: Int,
)

private enum class SpotLook { NONE, LIKELY, RESERVED, CONFIRMED, TAKEN, CANCELLED }

private val NORMAL = listOf(
    Step("1 · 자리 찾는 중 · Searching",
        "Car B는 몇 분째 빈자리를 찾아 돌고 있어요. 주차장은 꽉 찬 것처럼 보여요.\nCar B has been circling for minutes. The lot looks full.",
        "센서 없는 주차장 · No sensors in this lot", null,
        SpotLook.NONE, false, 0.08f, 0.72f, 0f, 0),
    Step("2 · 곧 나갈 차 감지 · Leaving soon",
        "Car A 운전자가 차에 타서 문을 닫고 목적지를 설정했어요. 버튼은 누르지 않아요.\nCar A's driver closes the door and sets a destination. No button needed.",
        "Pleos 신호: 문 + 내비 목적지 → 곧 빌 가능성 (노란색)", null,
        SpotLook.LIKELY, false, 0.22f, 0.72f, 0f, 0),
    Step("3 · Car B에게 음성 제안 · Voice offer",
        "Car B에 음성 안내: \"150미터 앞에 자리가 곧 납니다. 갈까요?\" 운전자: \"네\".\nCar B hears the offer and just says \"yes\".",
        "자리 예약 → 내비가 그 자리로 경로 안내", "150미터 앞에 자리가 곧 납니다. 갈까요?",
        SpotLook.RESERVED, true, 0.40f, 0.72f, 0f, 0),
    Step("4 · 출발 확정 · Confirmed",
        "Car A가 주차 브레이크를 풀고 출발해요. 이제 자리가 확실히 비어요.\nCar A releases the parking brake and drives off.",
        "Pleos 신호: 주차 브레이크 해제 → 확정 (초록색)", "다음 차가 이 자리로 오고 있어요.",
        SpotLook.CONFIRMED, true, 0.55f, 0.72f, 1f, 0),
    Step("5 · 핸드오버 완료 · Handed over",
        "Car B가 바로 그 자리에 주차했어요. Car A는 자리를 넘겨줘서 크레딧 1점을 받아요.\nCar B parks in the exact spot. Car A earns +1 credit.",
        "주차 완료 → 크레딧 +1", "자리를 넘겨드렸어요. 크레딧 1점이 적립됐어요.",
        SpotLook.TAKEN, false, 0.585f, 0.22f, 1f, 1),
)

private val CANCEL = listOf(
    NORMAL[0], NORMAL[1], NORMAL[2],
    Step("4 · 출발 취소 · Driver stays",
        "Car A 운전자가 출발하지 않았어요 (45초 동안 주차 브레이크 해제 없음). 제안을 자동으로 철회해요.\nCar A never leaves, so the offer is withdrawn automatically.",
        "확정 신호 없음 → 철회 → Car B는 다른 자리 탐색", "자리가 취소됐어요. 다른 자리를 찾을게요.",
        SpotLook.CANCELLED, false, 0.75f, 0.72f, 0f, 0),
)

@Composable
fun DemoScreen(speaker: Speaker, onBack: () -> Unit) {
    var cancelMode by remember { mutableStateOf(false) }
    val steps = if (cancelMode) CANCEL else NORMAL
    var i by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    val s = steps[i.coerceIn(0, steps.lastIndex)]

    LaunchedEffect(i, cancelMode) { s.voice?.let { speaker.say(it) } }
    LaunchedEffect(playing) {
        while (playing) {
            delay(5500)
            if (i < steps.lastIndex) i++ else playing = false
        }
    }

    val bx by animateFloatAsState(s.bX, tween(1400), label = "bx")
    val by by animateFloatAsState(s.bY, tween(1400), label = "by")
    val aGone by animateFloatAsState(s.aGone, tween(1400), label = "a")
    val spotColor by animateColorAsState(
        when (s.spot) {
            SpotLook.NONE -> Color.Transparent
            SpotLook.LIKELY, SpotLook.RESERVED -> Color(0xFFEF9F27)
            SpotLook.CONFIRMED, SpotLook.TAKEN -> Color(0xFF1D9E75)
            SpotLook.CANCELLED -> Color(0xFFE24B4A)
        }, tween(600), label = "spot"
    )

    Column(Modifier.fillMaxSize().background(Color(0xFF111418)).padding(16.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("NextSpot", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.weight(1f))
            Text("Car A 크레딧: ${s.credits}", fontSize = 16.sp, color = Color(0xFF9FE1CB))
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawLot(spotColor, s.spot, s.showRoute, bx, by, aGone)
            }
        }

        Spacer(Modifier.height(8.dp))
        Column(
            Modifier.fillMaxWidth().background(Color(0xFF1C2128), RoundedCornerShape(12.dp)).padding(14.dp)
        ) {
            Text(s.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text(s.caption, fontSize = 15.sp, color = Color(0xFFD3D1C7), lineHeight = 21.sp)
            Spacer(Modifier.height(6.dp))
            Text(s.signal, fontSize = 13.sp, color = Color(0xFFEF9F27))
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { playing = false; if (i > 0) i-- }, modifier = Modifier.weight(1f)) { Text("◀ 이전") }
            Button(
                onClick = { if (i >= steps.lastIndex) i = 0; playing = !playing },
                modifier = Modifier.weight(1.4f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D9E75)),
            ) { Text(if (playing) "■ 정지" else "▶ 시나리오 재생", fontSize = 16.sp) }
            OutlinedButton(onClick = { playing = false; if (i < steps.lastIndex) i++ }, modifier = Modifier.weight(1f)) { Text("다음 ▶") }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { playing = false; i = 0; cancelMode = !cancelMode }, modifier = Modifier.weight(1f)) {
                Text(if (cancelMode) "정상 시나리오로" else "취소 시나리오 보기")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("메뉴") }
        }
    }
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
