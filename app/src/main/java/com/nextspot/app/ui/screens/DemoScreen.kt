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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextspot.app.car.Speaker
import com.nextspot.app.ui.DemoEngine.Phase
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * One-screen DEMO (both cars at once), styled after the Pleos Connect built-in apps (PLEOS_DESIGN.md).
 * Its job: show the idea clearly and make the demo easy to run. So the main screen always shows
 * the flow (Car A signals -> spot posted -> Car B received), the step, the cars, the demo buttons and
 * the latest events. Only manual signals, SDK info and the full log are in the details panel.
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

/** Wording for each phase: what is happening and why (the demo explains the idea). */
private fun viewFor(p: Phase, meters: Int?): View = when (p) {
    Phase.IDLE -> View("자리 나눔 데모", "시나리오 재생을 누르면 떠나는 차(Car A)와 자리를 찾는 차(Car B)가 함께 움직여요.",
        SpotLook.NONE, false, 0.08f, LOT_LANE_Y, 0f)
    Phase.SEARCHING -> View("Car B가 빈 자리를 찾는 중", "주차장이 꽉 차 있어서 목적지 근처에서 자리를 기다리고 있어요.",
        SpotLook.NONE, false, 0.15f, LOT_LANE_Y, 0f)
    Phase.LIKELY -> View("앞 차가 곧 떠나요", "Car A의 문, 벨트, 내비 신호로 출발을 예측해 이 자리를 등록했어요.",
        SpotLook.LIKELY, false, 0.22f, LOT_LANE_Y, 0f)
    Phase.OFFER -> View("${meters ?: "-"}m 앞에 자리가 곧 납니다. 갈까요?", "$SPOT_NAME · 1분 안에 빌 예정",
        SpotLook.LIKELY, false, 0.30f, LOT_LANE_Y, 0f)
    Phase.RESERVED -> View("자리를 잡아 뒀어요", "다른 차는 이 자리를 받을 수 없어요. ${SPOT_NAME}로 안내할게요.",
        SpotLook.RESERVED, true, 0.40f, LOT_LANE_Y, 0f)
    Phase.CONFIRMED -> View("앞 차가 출발했어요", "주차 브레이크 해제로 출발을 확인했어요. 안내를 따라 들어가세요.",
        SpotLook.CONFIRMED, true, 0.55f, LOT_LANE_Y, 1f)
    Phase.TAKEN -> View("주차를 마쳤어요", "자리를 비워 준 Car A에 크레딧 1점이 쌓여요.",
        SpotLook.TAKEN, false, TARGET_X, SPOT_Y, 1f)
    Phase.WITHDRAWN -> View("자리가 취소됐어요", "Car A가 출발하지 않아 제안을 철회했어요. Car B는 다른 자리를 찾아요.",
        SpotLook.CANCELLED, false, 0.75f, LOT_LANE_Y, 0f)
}

private fun lookColor(l: SpotLook): Color = when (l) {
    SpotLook.NONE, SpotLook.LIKELY, SpotLook.RESERVED -> NsColor.Likely
    SpotLook.CONFIRMED, SpotLook.TAKEN -> NsColor.Active
    SpotLook.CANCELLED -> NsColor.Red
}

/** Everything the screen draws, as plain values (so it can be previewed without DemoEngine). */
internal data class DemoUi(
    val phase: Phase,
    val offerMeters: Int? = null,
    val credits: Long = 0L,
    val door: Boolean = false, val belt: Boolean = false, val nav: Boolean = false, val brake: Boolean = false,
    val posted: Boolean = false,
    val listening: Boolean = false,
    val autoPlaying: Boolean = false,
    val log: List<DemoEngine.LogItem> = emptyList(),
    val sdkLines: List<Pair<String, String>> = emptyList(),
)

/** What the buttons do. Real ones call DemoEngine; previews use the empty default. */
internal class DemoActions(
    val onYes: () -> Unit = {}, val onNo: () -> Unit = {}, val onTalk: () -> Unit = {},
    val onPlay: (cancelCase: Boolean) -> Unit = {}, val onStop: () -> Unit = {},
    val onSearch: () -> Unit = {}, val onParkB: () -> Unit = {},
    val onDoor: () -> Unit = {}, val onNav: () -> Unit = {}, val onBrake: () -> Unit = {},
    val onReset: () -> Unit = {}, val onBack: () -> Unit = {},
)

private fun DemoEngine.toUi() = DemoUi(
    phase = phase, offerMeters = offerMeters, credits = credits,
    door = door, belt = belt, nav = nav, brake = brake,
    posted = spot?.status != null, listening = listening, autoPlaying = autoPlaying,
    log = log.toList(), sdkLines = sdkLines,
)

private fun DemoEngine.toActions(onBack: () -> Unit) = DemoActions(
    onYes = { answerYes() }, onNo = { answerNo() }, onTalk = { listenForAnswer() },
    onPlay = { cancel -> playScenario(cancelCase = cancel) }, onStop = { stopAutoPlay() },
    onSearch = { startSearch() }, onParkB = { parkCarB() },
    onDoor = { onDoorOpenedAndClosed() }, onNav = { onNavigationSet() }, onBrake = { onParkingBrakeReleased() },
    onReset = { reset() }, onBack = onBack,
)

@Composable
fun DemoScreen(speaker: Speaker, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val e = remember { DemoEngine(context, speaker, scope) }
    DisposableEffect(Unit) { onDispose { e.dispose() } }

    // Ask once for the microphone (for the "네" voice answer). Buttons still work without it.
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }

    NsTheme { DemoContent(e.toUi(), e.toActions(onBack)) }
}

@Composable
internal fun DemoContent(ui: DemoUi, act: DemoActions, startPanelOpen: Boolean = false) {
    var showPanel by remember { mutableStateOf(startPanelOpen) }

    // Movement: smooth deceleration, short. These are State objects read only while drawing,
    // so the animation redraws the moving layer and does not recompose the screen every frame.
    val v = viewFor(ui.phase, ui.offerMeters)
    val ease = FastOutSlowInEasing
    val bx = animateFloatAsState(v.bX, tween(900, easing = ease), label = "bx")
    val by = animateFloatAsState(v.bY, tween(900, easing = ease), label = "by")
    val aGone = animateFloatAsState(v.aGone, tween(900, easing = ease), label = "a")
    val routeAlpha = animateFloatAsState(if (v.route) 1f else 0f, tween(400, easing = ease), label = "route")
    val spotAlpha = animateFloatAsState(if (v.look == SpotLook.NONE) 0f else 1f, tween(400, easing = ease), label = "spotA")
    val spotColor = animateColorAsState(lookColor(v.look), tween(400, easing = ease), label = "spot")

    val margin = 24.dp
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val tagStyle = LocalTextStyle.current.merge(TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold))
    BoxWithConstraints(Modifier.fillMaxSize().background(NsColor.BgSecondary)) {
        // Half-width windows are fine: below ~1000 the details panel takes the full width
        // and the latest-events list is hidden.
        val narrow = maxWidth < 1000.dp
        val panelW = if (narrow) maxWidth - margin * 2 else (maxWidth * 0.45f).coerceIn(480.dp, 640.dp)
        val bottomVisible = !(narrow && showPanel)
        val showLog = !narrow && !showPanel

        // The lot keeps clear of the top bar (measured) and of the tallest bottom block seen so far,
        // so it does not resize every time the card text changes.
        var topH by remember { mutableIntStateOf(0) }
        var bottomMax by remember { mutableIntStateOf(0) }
        val topPx = if (topH > 0) topH.toFloat() else with(density) { 170.dp.toPx() }
        val topInset = topPx + with(density) { 12.dp.toPx() }
        val bottomTarget = with(density) { (margin + 12.dp).toPx() + maxOf(bottomMax.toFloat(), 440.dp.toPx()) }
        val rightTarget = if (showPanel && !narrow) with(density) { (panelW + margin * 2).toPx() } else 0f
        val bottomInset = animateFloatAsState(bottomTarget, tween(300, easing = ease), label = "bottomInset")
        val rightInset = animateFloatAsState(rightTarget, tween(300, easing = ease), label = "rightInset")

        // 1) Map, layer 1: floor, spot lines, parked cars. Drawn once into its own layer and cached.
        Canvas(Modifier.fillMaxSize().graphicsLayer { }) {
            drawStaticLot(topInset, rightInset.value, bottomInset.value)
        }
        // Map, layer 2: only what moves (Car A, route, Car B, the empty-spot outline, car labels).
        Canvas(Modifier.fillMaxSize()) {
            drawMovingLot(
                textMeasurer, tagStyle,
                spotColor.value, spotAlpha.value, routeAlpha.value,
                bx.value, by.value, aGone.value, topInset, rightInset.value, bottomInset.value,
            )
        }

        // 2) Top bar: name, credits, details button, and the flow (the idea in one row)
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .onSizeChanged { topH = it.height }
                .background(NsColor.SurfaceBasic.copy(alpha = 0.96f))
                .padding(horizontal = margin, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("NextSpot", fontSize = NsText.Title, lineHeight = NsText.TitleLine, fontWeight = FontWeight.Bold, color = NsColor.Text)
                Spacer(Modifier.width(20.dp))
                Text("Car A 크레딧 ${ui.credits}", fontSize = NsText.Label, color = NsColor.TextSecondary, maxLines = 1)
                Spacer(Modifier.weight(1f))
                NsIconTextButton(
                    label = if (showPanel) "닫기" else "상세",
                    description = if (showPanel) "상세 닫기" else "상세 열기",
                    onClick = { showPanel = !showPanel },
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
            }
            FlowBar(ui.door, ui.belt, ui.nav, ui.brake, ui.posted, ui.offerMeters != null)
        }

        // 3) Bottom: demo buttons, the current step card, and the latest events
        if (bottomVisible) {
            val offer = ui.phase == Phase.OFFER
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .padding(start = margin, end = if (showPanel && !narrow) panelW + margin * 2 else margin, bottom = margin)
                    .onSizeChanged { if (it.height > bottomMax) bottomMax = it.height },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledButton(
                        if (ui.autoPlaying) "정지" else "시나리오 재생",
                        { if (ui.autoPlaying) act.onStop() else act.onPlay(false) }, size = BtnSize.Small,
                    )
                    BasicButton("취소 시나리오", { act.onPlay(true) }, size = BtnSize.Small)
                    BasicButton("초기화", act.onReset, size = BtnSize.Small)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                    Column(
                        Modifier.weight(1.6f).clip(NsCard).background(NsColor.SurfaceHigh).padding(28.dp),
                    ) {
                        StepBar(ui.phase)
                        Spacer(Modifier.height(16.dp))
                        if (offer) {
                            // the card says what the car says out loud, so mark it as a voice message
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(Modifier.size(26.dp)) { drawSpeaker(NsColor.TextTertiary) }
                                Spacer(Modifier.width(10.dp))
                                Text("음성 안내", fontSize = NsText.Label, color = NsColor.TextTertiary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                v.title, fontSize = NsText.Title, lineHeight = NsText.TitleLine, fontWeight = FontWeight.Bold,
                                color = NsColor.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(16.dp).drawBehind {
                                        drawCircle(if (v.look == SpotLook.NONE) NsColor.Gray300 else spotColor.value)
                                    }
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    v.title, fontSize = NsText.Title, lineHeight = NsText.TitleLine, fontWeight = FontWeight.Bold,
                                    color = NsColor.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // the card text stays short: at most 3 lines
                        Text(
                            v.body, fontSize = NsText.Body, lineHeight = NsText.BodyLine, color = NsColor.TextSecondary,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                        if (offer) {
                            Spacer(Modifier.height(24.dp))
                            OfferActions(ui.listening, act.onYes, act.onNo, act.onTalk)
                        }
                    }
                    if (showLog) {
                        RecentLog(
                            ui.log,
                            Modifier.weight(1f).clip(NsCard)
                                .background(NsColor.SurfaceBasic.copy(alpha = 0.96f)).padding(24.dp),
                        )
                    }
                }
            }
        }

        // 4) Details panel (hidden by default): manual signals, SDK info, full log
        if (showPanel) {
            DetailsPanel(
                ui, act,
                Modifier.align(Alignment.TopEnd)
                    .padding(top = with(density) { (topPx + 12.dp.toPx()).toDp() }, end = margin, bottom = margin)
                    .fillMaxHeight().width(panelW),
            )
        }
    }
}

private val STEPS = listOf("찾기", "감지", "제안", "확정", "완료")

private fun stepOf(p: Phase) = when (p) {
    Phase.IDLE -> 0
    Phase.SEARCHING -> 1
    Phase.LIKELY -> 2
    Phase.OFFER, Phase.RESERVED -> 3
    Phase.CONFIRMED -> 4
    Phase.TAKEN -> 5
    Phase.WITHDRAWN -> -1
}

/** Where we are in the 5 steps: done = green dot, now = white and bold, later = faint. Cancel ends in red. */
@Composable
private fun StepBar(phase: Phase) {
    val cur = stepOf(phase)
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        val shown = if (cur == -1) STEPS.take(3) else STEPS
        shown.forEachIndexed { i, label ->
            val n = i + 1
            val done = cur == -1 || n < cur || (cur == 5 && n == 5)
            val now = n == cur && !done
            Step(
                label,
                if (done) NsColor.Active else if (now) NsColor.Text else NsColor.TextQuaternary,
                if (done || now) NsColor.Text else NsColor.TextQuaternary,
                bold = now,
            )
        }
        if (cur == -1) Step("취소", NsColor.Red, NsColor.Red, bold = true)
    }
}

@Composable
private fun RowScope.Step(label: String, dot: Color, text: Color, bold: Boolean) {
    Box(Modifier.size(12.dp).background(dot, CircleShape))
    Spacer(Modifier.width(8.dp))
    Text(label, fontSize = NsText.Label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = text, maxLines = 1)
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(NsColor.Divider2))
}

/** Extra controls and info for development and Q&A: manual signals, SDK info, the full event log. */
@Composable
private fun DetailsPanel(ui: DemoUi, act: DemoActions, modifier: Modifier) {
    Column(
        modifier.clip(NsCard).background(NsColor.SurfaceHigh)
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("상세", fontSize = NsText.Title, lineHeight = NsText.TitleLine, fontWeight = FontWeight.Bold, color = NsColor.Text)
        Text("데모 주차장 · 지도는 데모용 그림이에요", fontSize = NsText.Label, color = NsColor.TextTertiary)
        Divider()
        Text("수동 신호 (차 신호 대신)", fontSize = NsText.Label, color = NsColor.TextTertiary)
        ButtonPair("B 탐색" to act.onSearch, "B 주차" to act.onParkB)
        ButtonPair("A 문" to act.onDoor, "A 내비" to act.onNav)
        ButtonPair("A 브레이크" to act.onBrake, "초기화" to act.onReset)
        Divider()
        SdkInfo(ui.sdkLines)
        Divider()
        Text("이벤트 로그", fontSize = NsText.Label, color = NsColor.TextTertiary)
        EventLog(ui.log, Modifier.height(320.dp))
        // small text button at the very bottom
        Box(
            Modifier.fillMaxWidth().height(56.dp).clickable(role = Role.Button, onClick = act.onBack),
            contentAlignment = Alignment.Center,
        ) { Text("메뉴로", fontSize = NsText.Label, color = NsColor.TextTertiary) }
    }
}

@Composable
private fun ButtonPair(a: Pair<String, () -> Unit>, b: Pair<String, () -> Unit>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        BasicButton(a.first, a.second, Modifier.weight(1f), BtnSize.Small)
        BasicButton(b.first, b.second, Modifier.weight(1f), BtnSize.Small)
    }
}

/** Where everything sits inside the lot area. Cheap to build, so both map layers just make their own. */
private class Lot(val w: Float, val h: Float) {
    // tall, narrow windows (portrait half screen) get fewer, wider spots so cars stay a good size
    val n = if (w < h * 1.1f) 4 else LOT_SPOT_N
    val targetIdx = if (n == LOT_SPOT_N) LOT_TARGET else 2
    val spotW = w * LOT_WIDTH / n
    val spotH = h * LOT_SPOT_H
    val left = w * TARGET_X - (targetIdx + 0.5f) * spotW      // keeps the empty spot at TARGET_X
    val topRowY = h * LOT_MARGIN
    val laneTop = topRowY + spotH
    val laneH = h * LOT_LANE_H
    val laneY = h * LOT_LANE_Y
    // one car size for everybody
    val carW = spotW * 0.66f
    val carL = minOf(spotH * 0.80f, carW * 2.15f)
    fun center(k: Int) = Offset(left + k * spotW + spotW / 2, topRowY + spotH / 2)
    val target get() = center(targetIdx)
}

private fun DrawScope.lotTooSmall(topInset: Float, rightInset: Float, bottomInset: Float) =
    size.height - topInset - bottomInset < 80f || size.width - rightInset < 80f

/** Layer 1 (cached): floor, thin spot lines, lane line, parked cars. Nothing here changes while animating. */
private fun DrawScope.drawStaticLot(topInset: Float, rightInset: Float, bottomInset: Float) {
    drawRect(NsColor.BgSecondary)
    if (lotTooSmall(topInset, rightInset, bottomInset)) return
    inset(0f, topInset, rightInset, bottomInset) {
        val lot = Lot(size.width, size.height)
        val line = NsColor.Divider2
        for (k in 0..lot.n) {
            val x = lot.left + k * lot.spotW
            drawLine(line, Offset(x, lot.topRowY), Offset(x, lot.laneTop), strokeWidth = 2f)
        }
        drawLine(line, Offset(lot.left, lot.laneTop), Offset(lot.left + lot.n * lot.spotW, lot.laneTop), strokeWidth = 2f)
        val dashY = lot.laneTop + lot.laneH * 0.40f
        drawLine(
            line, Offset(size.width * 0.02f, dashY), Offset(size.width * 0.98f, dashY),
            strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 20f)),
        )
        // a full lot: every spot has a car except the target (Car A sits there, drawn on the moving layer)
        for (k in 0 until lot.n) {
            if (k != lot.targetIdx) drawCar(lot.center(k), lot.carL, lot.carW, 90f, NsColor.ParkedCar, 1f)
        }
    }
}

/** Layer 2: the empty-spot outline, Car A leaving, the route and Car B, with name tags. Redrawn every frame. */
private fun DrawScope.drawMovingLot(
    textMeasurer: TextMeasurer, tagStyle: TextStyle,
    spotColor: Color, spotAlpha: Float, routeAlpha: Float, bx: Float, by: Float, aGone: Float,
    topInset: Float, rightInset: Float, bottomInset: Float,
) {
    if (lotTooSmall(topInset, rightInset, bottomInset)) return
    inset(0f, topInset, rightInset, bottomInset) {
        val lot = Lot(size.width, size.height)
        val w = size.width; val h = size.height
        val tc = lot.target

        // the one highlighted spot: status-colored outline
        if (spotAlpha > 0.01f) {
            val tl = Offset(tc.x - lot.spotW / 2 + 6, lot.topRowY + 6)
            val sz = Size(lot.spotW - 12, lot.spotH - 12)
            drawRoundRect(spotColor.copy(alpha = 0.12f * spotAlpha), tl, sz, CornerRadius(12f), style = Fill)
            drawRoundRect(spotColor.copy(alpha = spotAlpha), tl, sz, CornerRadius(12f), style = Stroke(4f))
        }

        // Car A: sits in the spot, then leaves into the lane and fades
        if (aGone < 0.99f) {
            val ax = (tc.x + aGone * (w * 0.35f)).coerceAtMost(w - lot.carL / 2 - 12f)
            val ay = tc.y + aGone * (lot.laneY - tc.y)
            val deg = 90f * (1f - aGone)
            drawCar(Offset(ax, ay), lot.carL, lot.carW, deg, NsColor.CarA, 1f - aGone)
            drawTag(textMeasurer, tagStyle, "Car A", Offset(ax, ay), extent(lot, deg), 1f - aGone)
        }

        // Car B: lighter than the others so it stands out. Kept fully inside the screen.
        val half = lot.carL / 2 + 12f
        val bCenter = Offset((w * bx).coerceIn(half, w - half), h * by)

        // route to the spot: blue
        if (routeAlpha > 0.01f) {
            val p = Path().apply {
                moveTo(bCenter.x + lot.carL / 2, bCenter.y)
                lineTo(tc.x, bCenter.y)
                lineTo(tc.x, tc.y + lot.carL * 0.25f)
            }
            drawPath(
                p, NsColor.Positive.copy(alpha = routeAlpha),
                style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        // turns up into the spot when it parks
        val turn = ((LOT_LANE_Y - by) / (LOT_LANE_Y - SPOT_Y)).coerceIn(0f, 1f)
        val bDeg = -90f * turn
        drawCar(bCenter, lot.carL, lot.carW, bDeg, NsColor.CarB, 1f)
        drawTag(textMeasurer, tagStyle, "Car B", bCenter, extent(lot, bDeg), 1f)
    }
}

/** Half the car's height on screen after rotating by [deg] (so a tag can sit just above it). */
private fun extent(lot: Lot, deg: Float): Float {
    val r = Math.toRadians(deg.toDouble())
    return (abs(sin(r)) * lot.carL + abs(cos(r)) * lot.carW).toFloat() / 2f
}

/** Small name tag above a car ("Car A" / "Car B"), outside the car body. */
private fun DrawScope.drawTag(m: TextMeasurer, style: TextStyle, text: String, c: Offset, ext: Float, alpha: Float) {
    if (alpha < 0.05f) return
    val r = m.measure(text, style.copy(color = NsColor.Text.copy(alpha = alpha)))
    val padX = 12f
    val padY = 5f
    val bw = r.size.width + padX * 2
    val bh = r.size.height + padY * 2
    val cx = c.x.coerceIn(bw / 2, size.width - bw / 2)
    val top = (c.y - ext - 10f - bh).coerceAtLeast(4f)
    drawRoundRect(
        NsColor.SurfaceHigh.copy(alpha = 0.95f * alpha), Offset(cx - bw / 2, top), Size(bw, bh), CornerRadius(10f),
    )
    drawText(r, topLeft = Offset(cx - r.size.width / 2f, top + padY))
}

/**
 * Top-down car: rounded body that narrows at the nose, windshield, rear window, side mirrors.
 * No headlights. Drawn facing right (+x), then rotated by [deg] around its center
 * (90 = nose down, -90 = nose up, 0 = driving right).
 */
private fun DrawScope.drawCar(c: Offset, len: Float, wid: Float, deg: Float, color: Color, alpha: Float) {
    rotate(deg, pivot = c) {
        val hx = len / 2
        val hy = wid / 2
        val rr = wid * 0.30f                              // rear corner radius
        fun x(v: Float) = c.x + v
        fun y(v: Float) = c.y + v
        // mirrors first, so the body covers their roots
        val mirror = color.copy(alpha = alpha)
        val ms = Size(len * 0.07f, wid * 0.10f)
        drawRoundRect(mirror, Offset(x(len * 0.12f), y(-hy - ms.height * 0.75f)), ms, CornerRadius(4f))
        drawRoundRect(mirror, Offset(x(len * 0.12f), y(hy - ms.height * 0.25f)), ms, CornerRadius(4f))
        // body
        val body = Path().apply {
            moveTo(x(-hx + rr), y(-hy))
            lineTo(x(hx * 0.30f), y(-hy))
            quadraticTo(x(hx * 0.86f), y(-hy * 0.97f), x(hx), y(-hy * 0.52f))
            quadraticTo(x(hx * 1.03f), y(0f), x(hx), y(hy * 0.52f))
            quadraticTo(x(hx * 0.86f), y(hy * 0.97f), x(hx * 0.30f), y(hy))
            lineTo(x(-hx + rr), y(hy))
            quadraticTo(x(-hx), y(hy), x(-hx), y(hy - rr))
            lineTo(x(-hx), y(-hy + rr))
            quadraticTo(x(-hx), y(-hy), x(-hx + rr), y(-hy))
            close()
        }
        drawPath(body, color.copy(alpha = alpha))
        val glass = NsColor.Glass.copy(alpha = 0.72f * alpha)
        // windshield: wide at the roof, narrower toward the hood
        drawPath(Path().apply {
            moveTo(x(len * 0.06f), y(-hy * 0.80f))
            quadraticTo(x(len * 0.20f), y(-hy * 0.72f), x(len * 0.27f), y(-hy * 0.58f))
            lineTo(x(len * 0.27f), y(hy * 0.58f))
            quadraticTo(x(len * 0.20f), y(hy * 0.72f), x(len * 0.06f), y(hy * 0.80f))
            close()
        }, glass)
        // rear window
        drawPath(Path().apply {
            moveTo(x(-len * 0.22f), y(-hy * 0.74f))
            quadraticTo(x(-len * 0.31f), y(-hy * 0.66f), x(-len * 0.36f), y(-hy * 0.52f))
            lineTo(x(-len * 0.36f), y(hy * 0.52f))
            quadraticTo(x(-len * 0.31f), y(hy * 0.66f), x(-len * 0.22f), y(hy * 0.74f))
            close()
        }, glass)
    }
}

/** Small speaker icon (cone + two sound waves), drawn on a square canvas. */
private fun DrawScope.drawSpeaker(color: Color) {
    val s = size.width
    val body = Path().apply {
        moveTo(s * 0.06f, s * 0.38f)
        lineTo(s * 0.28f, s * 0.38f)
        lineTo(s * 0.52f, s * 0.16f)
        lineTo(s * 0.52f, s * 0.84f)
        lineTo(s * 0.28f, s * 0.62f)
        lineTo(s * 0.06f, s * 0.62f)
        close()
    }
    drawPath(body, color)
    val sw = s * 0.09f
    for (r in listOf(0.22f, 0.40f)) {
        drawArc(
            color, startAngle = -50f, sweepAngle = 100f, useCenter = false,
            topLeft = Offset(s * 0.52f - s * r, s * 0.5f - s * r), size = Size(s * r * 2, s * r * 2),
            style = Stroke(sw, cap = StrokeCap.Round),
        )
    }
}
