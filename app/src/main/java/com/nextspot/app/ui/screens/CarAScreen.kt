package com.nextspot.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextspot.app.car.DepartureDetector
import com.nextspot.app.car.Platform
import com.nextspot.app.car.Speaker
import com.nextspot.app.data.DemoPlaces
import com.nextspot.app.data.Spot
import com.nextspot.app.data.SpotRepository
import com.nextspot.app.data.SpotStatus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import com.nextspot.app.R
import com.nextspot.app.ui.components.CarButton
import com.nextspot.app.ui.components.CarCard
import com.nextspot.app.ui.components.CarPage
import com.nextspot.app.ui.theme.NsColors
import com.nextspot.app.ui.theme.NsType
import com.nextspot.app.ui.components.statusLabel
import com.nextspot.app.ui.theme.NsTheme
import com.nextspot.app.ui.theme.NsShape
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay

private const val CAR_A_ID = "car-A"

/** Car A: the car that is leaving its parking spot. */
@Composable
fun CarAScreen(speaker: Speaker, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SpotRepository() }
    val logs = remember { mutableStateListOf<String>() }
    var spotId by remember { mutableStateOf<String?>(null) }
    var spot by remember { mutableStateOf<Spot?>(null) }
    var credits by remember { mutableLongStateOf(0L) }
    var reward by remember { mutableStateOf(false) }

    // the "+1 credit" overlay stays 2.5 s, then fades out
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
            onLog = { logs.add(0, it) },
        )
    }
    val signals = remember { Platform.carSignals(context) }

    // Real car signals (Pleos variant). In the sim variant this does nothing.
    DisposableEffect(Unit) {
        signals.start(detector)
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
                    }
                    lastStatus = status
                }
            }
        }
        onDispose { reg?.remove() }
    }

    CarPage(stringResource(R.string.leaving_car) + " \u00b7 Car A", onBack) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            // left: what is going on
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("신호: ${signals.label} · 크레딧 (credits): $credits", style = NsType.body, color = NsColors.text2)
                CarCard("내 자리 상태 · My spot", statusLabel(spot?.status))
                Text("기록 · Log", style = NsType.body, color = NsColors.text2)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    logs.forEach { Text("• $it", style = NsType.body, color = NsColors.leavingCar) }
                }
            }
            // right: the car signals (buttons stand in) and the two small actions
            Column(Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("차 신호 (버튼으로 대신하기) · Car signals (buttons stand in)", style = NsType.body, color = NsColors.text2)
                CarButton("1. 문 열고 닫기 · Door opened and closed", { detector.onDoorOpenedAndClosed() }, Modifier.fillMaxWidth())
                CarButton("2. 집으로 내비 설정 · Set navigation home", { detector.onNavigationSet() }, Modifier.fillMaxWidth())
                CarButton("3. 주차 브레이크 해제 · Release parking brake", { detector.onParkingBrakeReleased() }, Modifier.fillMaxWidth())
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CarButton("안 떠나요 · Cancel", { detector.cancel() }, Modifier.weight(1f))
                    CarButton("데모 초기화 · Reset", {
                        detector.reset()
                        spotId = null
                        spot = null
                        reward = false
                        logs.clear()
                    }, Modifier.weight(1f))
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = reward,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(500)),
        ) {
            Column(
                Modifier.clip(NsShape.button).background(NsColors.surface).padding(horizontal = 120.dp, vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("+1 크레딧 · +1 credit", style = NsType.logo, color = NsColors.main)
                Text("주차 자리를 넘겨드렸어요 · You handed over the spot", style = NsType.nowQuestion, color = NsColors.text)
            }
        }
        }
    }
}
