package com.nextspot.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextspot.app.car.CarSignals
import com.nextspot.app.car.DepartureDetector
import com.nextspot.app.car.Platform
import com.nextspot.app.car.Speaker
import com.nextspot.app.data.DemoPlaces
import com.nextspot.app.data.Spot
import com.nextspot.app.data.SpotRepository
import com.nextspot.app.data.SpotStatus
import com.nextspot.app.ui.components.CarBox
import com.nextspot.app.ui.components.CarFrame
import com.nextspot.app.ui.components.CarGlyph
import com.nextspot.app.ui.components.CenterColumn
import com.nextspot.app.ui.components.CheckDisc
import com.nextspot.app.ui.components.Eyebrow
import com.nextspot.app.ui.components.Headline
import com.nextspot.app.ui.components.HintRow
import com.nextspot.app.ui.components.MarkKind
import com.nextspot.app.ui.components.NsIcon
import com.nextspot.app.ui.components.NsPaths
import com.nextspot.app.ui.components.Pill
import com.nextspot.app.ui.components.SimButton
import com.nextspot.app.ui.components.StatusMark
import com.nextspot.app.ui.components.SubText
import com.nextspot.app.ui.components.TextLink
import com.nextspot.app.ui.theme.NsCar
import com.nextspot.app.ui.theme.NsType
import kotlinx.coroutines.delay

private const val CAR_A_ID = "car-A"

/**
 * Car A: the car that is leaving its parking spot. Screens follow the design photos:
 * waiting -> A1 signal detected -> A2 waiting for the brake -> A3 departure confirmed, or A4 cancelled.
 * In the real car there are no buttons: the car's own sensors drive everything and this screen only shows
 * which sensors fired. The simulator row (hidden by default) stands in for the sensors.
 */
@Composable
fun CarAScreen(speaker: Speaker, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SpotRepository() }
    var spotId by remember { mutableStateOf<String?>(null) }
    var spot by remember { mutableStateOf<Spot?>(null) }
    var credits by remember { mutableLongStateOf(0L) }
    var reward by remember { mutableStateOf(false) }
    // which sensors have fired (for the display only; the logic stays in DepartureDetector)
    var door by remember { mutableStateOf(false) }
    var belt by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    var brake by remember { mutableStateOf(false) }
    val seat = door || belt   // either one counts as "driver got in" (same as DepartureDetector)

    // the "+1 credit" card stays 2.5 s, then fades out
    LaunchedEffect(reward) {
        if (reward) { delay(2500); reward = false }
    }

    val detector = remember {
        DepartureDetector(
            repo = repo,
            carId = CAR_A_ID,
            group = DemoPlaces.GROUP,
            lat = DemoPlaces.CAR_A_LAT,
            lng = DemoPlaces.CAR_A_LNG,
            onSpotPosted = { spotId = it },
            onLog = { },
        )
    }
    // every sensor (real or simulated) goes through here, so the screen can show which ones arrived
    val sensors = remember {
        object : CarSignals.Listener {
            override fun onDoorOpenedAndClosed() { door = true; detector.onDoorOpenedAndClosed() }
            override fun onSeatbeltBuckled() { belt = true; detector.onSeatbeltBuckled() }
            override fun onNavigationSet() { nav = true; detector.onNavigationSet() }
            override fun onParkingBrakeReleased() { brake = true; detector.onParkingBrakeReleased() }
        }
    }
    val signals = remember { Platform.carSignals(context) }

    fun clearSensors() { door = false; belt = false; nav = false; brake = false }

    // Real car signals (Pleos variant). In the sim variant this does nothing.
    DisposableEffect(Unit) {
        signals.start(sensors)
        onDispose { signals.stop() }
    }

    // Credits earned by this car
    DisposableEffect(Unit) {
        val reg = repo.listenCredits(CAR_A_ID) { credits = it }
        onDispose { reg.remove() }
    }

    // Watch our own spot and tell the driver by voice what happens to it
    DisposableEffect(spotId) {
        val id = spotId
        var lastStatus: String? = null
        val reg = id?.let {
            repo.listenSpot(it) { s ->
                spot = s
                val status = s?.status
                if (status != lastStatus) {
                    when (status) {
                        SpotStatus.MATCHED -> speaker.say("다음 차가 이 자리로 오고 있어요.")
                        SpotStatus.TAKEN -> {
                            speaker.say("자리를 넘겨드렸어요. 크레딧 1점이 적립됐어요.")
                            reward = true   // big "+1 credit" for a moment (the voice may not be audible)
                        }
                        SpotStatus.WITHDRAWN -> clearSensors()   // the detector starts over; so does the display
                    }
                    lastStatus = status
                }
            }
        }
        onDispose { reg?.remove() }
    }

    val status = spot?.status
    val registered = status == SpotStatus.LIKELY || status == SpotStatus.CONFIRMED ||
        status == SpotStatus.MATCHED || status == SpotStatus.TAKEN
    val confirmedOn = status == SpotStatus.CONFIRMED || status == SpotStatus.TAKEN || spot?.confirmed == true

    // A1 stays on screen for a moment after the spot is registered, so the detected signals can be read
    var a1Done by remember { mutableStateOf(false) }
    LaunchedEffect(registered) {
        if (registered) { delay(2000); a1Done = true } else a1Done = false
    }

    Box(Modifier.fillMaxSize()) {
        CarFrame(onBack) {
          Column(Modifier.fillMaxSize()) {
            // The four car sensors, always on screen. In the real car a cell turns yellow when the car reports the
            // signal by itself; in the simulator a tap sends the same signal (no separate button panel).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SensorCell("문 열림", door, Modifier.weight(1f)) { sensors.onDoorOpenedAndClosed() }
                SensorCell("안전벨트 착용", belt, Modifier.weight(1f)) { sensors.onSeatbeltBuckled() }
                SensorCell("내비 목적지 설정", nav, Modifier.weight(1f)) { sensors.onNavigationSet() }
                SensorCell("브레이크 해제", brake, Modifier.weight(1f)) { sensors.onParkingBrakeReleased() }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                // A3 departure confirmed
                confirmedOn -> {
                    CenterColumn {
                        StatusMark(MarkKind.FILLED)
                        Spacer(Modifier.height(36.dp))
                        Headline("출발이 확정됐어요", center = true)
                        Spacer(Modifier.height(12.dp))
                        SubText("뒤차에게 자리 안내를 시작했어요", center = true)
                        Spacer(Modifier.height(36.dp))
                        Pill(
                            when (status) {
                                SpotStatus.TAKEN -> "자리 전달 완료"
                                SpotStatus.MATCHED -> "뒤차가 오고 있어요"
                                else -> "뒤차를 기다리는 중"
                            },
                        )
                        if (credits > 0) {
                            Spacer(Modifier.height(20.dp))
                            Text("크레딧 $credits", style = NsType.body, color = NsCar.text3)
                        }
                    }
                }

                // A4 cancelled: the driver did not really leave (45 s without the brake, or "출발 안 함")
                status == SpotStatus.WITHDRAWN && !seat && !nav -> {
                    CenterColumn {
                        StatusMark(MarkKind.CANCEL)
                        Spacer(Modifier.height(36.dp))
                        Headline("출발이 취소됐어요", center = true)
                        Spacer(Modifier.height(12.dp))
                        SubText("다시 주차하신 것 같아요 · 자리 안내를 중단했어요", center = true)
                        Spacer(Modifier.height(36.dp))
                        Pill("안내 중단됨", cancel = true)
                    }
                }

                // A2 waiting: registered, waits for the parking brake
                registered && a1Done -> {
                    Column(Modifier.fillMaxSize()) {
                        Eyebrow("대기 중")
                        Spacer(Modifier.height(10.dp))
                        Headline("뒤차에게 알렸어요")
                        Spacer(Modifier.height(10.dp))
                        SubText("출발하시면 자동으로 뒤차에게 자리를 넘겨드려요")
                        Spacer(Modifier.height(36.dp))
                        CarBox(pad = 40.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                                CarGlyph(NsCar.accent, 48.dp)
                                Text(
                                    if (status == SpotStatus.MATCHED) "1대의 차량이 이 자리를 기다리고 있어요"
                                    else "근처 차량에게 이 자리를 알렸어요",
                                    style = NsType.title.copy(fontSize = 32.sp), color = NsCar.text,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        HintRow(
                            "브레이크를 놓으면 출발이 확정돼요",
                            icon = { NsIcon(NsPaths.BRAKE, NsCar.text2, 34.dp, strokeWidth = 1.8f) },
                        )
                    }
                }

                // A1 signal detected: door / belt, destination set (no driver action needed)
                seat || nav || registered -> {
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.weight(1f))
                        Headline(if (registered) "곧 출발 준비가 됐네요" else "출발 신호를 확인하고 있어요")
                        Spacer(Modifier.height(10.dp))
                        SubText(if (registered) "근처에서 자리를 찾는 차에게 알릴게요" else "문, 안전벨트, 내비를 차가 알아서 확인해요")
                        Spacer(Modifier.weight(1f))
                        HintRow(if (registered) "주변 차량에게 자리를 알리는 중" else "신호를 기다리는 중")
                    }
                }

                // nothing yet: parked, waiting for the first signal
                else -> {
                    CenterColumn {
                        Headline("주차 중이에요", center = true)
                        Spacer(Modifier.height(12.dp))
                        SubText("출발 신호를 기다리고 있어요", center = true)
                    }
                }
            }

            // Demo only: cancel the departure / start over (the real car needs neither)
            Row(Modifier.align(Alignment.BottomEnd)) {
                TextLink("출발 안 함", { detector.cancel(); clearSensors() }, NsCar.text3)
                TextLink("처음부터", {
                    detector.reset()
                    clearSensors()
                    spotId = null
                    spot = null
                    reward = false
                }, NsCar.text3)
            }
            }
          }
        }

        // "+1 credit" when the spot has been taken
        androidx.compose.animation.AnimatedVisibility(
            visible = reward,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(500)),
        ) {
            CarBox(Modifier.width(900.dp), pad = 56.dp) {
                CenterColumn2 {
                    Text("+1 크레딧", style = NsType.logo.copy(fontSize = 80.sp, lineHeight = 92.sp), color = NsCar.accent)
                    Spacer(Modifier.height(10.dp))
                    SubText("주차 자리를 넘겨드렸어요", center = true)
                }
            }
        }
    }
}

@Composable
private fun CenterColumn2(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)

/**
 * One car sensor. Fired = yellow outline and yellow text, not fired = grey outline and text.
 * The real car fires it by itself; in the simulator a tap sends the same signal.
 */
@Composable
private fun SensorCell(label: String, on: Boolean, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val line by animateColorAsState(if (on) NsCar.accent else NsCar.border, tween(250), label = "line")
    val textColor by animateColorAsState(if (on) NsCar.accent else NsCar.text2, tween(250), label = "text")
    Box(
        modifier.clip(NsCar.shape).border(if (on) 2.dp else 1.dp, line, NsCar.shape)
            .clickable(role = Role.Button, onClick = onTap).padding(vertical = 30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = NsType.title.copy(fontSize = 28.sp), color = textColor, maxLines = 1)
    }
}
