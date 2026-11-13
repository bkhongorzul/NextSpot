package com.nextspot.app.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextspot.app.R
import com.nextspot.app.car.CarSignals
import com.nextspot.app.car.DepartureDetector
import com.nextspot.app.car.Platform
import com.nextspot.app.car.Speaker
import com.nextspot.app.data.DemoPlaces
import com.nextspot.app.data.Spot
import com.nextspot.app.data.SpotRepository
import com.nextspot.app.data.SpotStatus
import com.nextspot.app.ui.components.CarButton
import com.nextspot.app.ui.components.CarCard
import com.nextspot.app.ui.components.CarPage
import com.nextspot.app.ui.components.NsButton
import com.nextspot.app.ui.components.SignalGroup
import com.nextspot.app.ui.components.SignalLine
import com.nextspot.app.ui.components.SignalResult
import com.nextspot.app.ui.components.statusLabel
import com.nextspot.app.ui.theme.NsColors
import com.nextspot.app.ui.theme.NsShape
import com.nextspot.app.ui.theme.NsType
import kotlinx.coroutines.delay

private const val CAR_A_ID = "car-A"

/**
 * Car A: the car that is leaving its parking spot.
 * In the real car there are no buttons: the car's own sensors drive everything and this screen only shows
 * which sensors fired. The simulator panel (hidden by default) stands in for the sensors.
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
    var seat by remember { mutableStateOf(false) }
    var nav by remember { mutableStateOf(false) }
    var brake by remember { mutableStateOf(false) }
    var simOpen by remember { mutableStateOf(false) }

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
            onLog = { },
        )
    }
    // every sensor (real or simulated) goes through here, so the screen can show which ones arrived
    val sensors = remember {
        object : CarSignals.Listener {
            override fun onDoorOpenedAndClosed() { seat = true; detector.onDoorOpenedAndClosed() }
            override fun onSeatbeltBuckled() { seat = true; detector.onSeatbeltBuckled() }
            override fun onNavigationSet() { nav = true; detector.onNavigationSet() }
            override fun onParkingBrakeReleased() { brake = true; detector.onParkingBrakeReleased() }
        }
    }
    val signals = remember { Platform.carSignals(context) }

    fun clearSensors() { seat = false; nav = false; brake = false }

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
    val likelyOn = status == SpotStatus.LIKELY || status == SpotStatus.CONFIRMED ||
        status == SpotStatus.MATCHED || status == SpotStatus.TAKEN
    val confirmedOn = status == SpotStatus.CONFIRMED || status == SpotStatus.TAKEN || spot?.confirmed == true

    CarPage(stringResource(R.string.leaving_car), onBack) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                // left: what the car's sensors see and what happens to my spot
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    CarCard("내 자리 상태", statusLabel(status))
                    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SignalGroup(stringResource(R.string.sigs_group1)) {
                            SignalLine(stringResource(R.string.sigs_seat), seat)
                            SignalLine(stringResource(R.string.sigs_nav), nav)
                            SignalResult(stringResource(R.string.sigs_result1), likelyOn)
                        }
                        SignalGroup(stringResource(R.string.sigs_group2)) {
                            SignalLine(stringResource(R.string.sigs_brake), brake)
                            SignalResult(stringResource(R.string.sigs_result2), confirmedOn)
                        }
                    }
                    Text("크레딧 $credits", style = NsType.body, color = NsColors.text2)
                    Spacer(Modifier.weight(1f))
                    // simulator: only for the demo, closed by default
                    NsButton(
                        if (simOpen) "시뮬레이터 닫기" else stringResource(R.string.signals_open),
                        { simOpen = !simOpen },
                    )
                }
                if (simOpen) {
                    // right: buttons that stand in for the sensors (the real car sends these by itself)
                    Column(Modifier.width(760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.signals_sub), style = NsType.body, color = NsColors.text2)
                        CarButton(stringResource(R.string.sig_door), { sensors.onDoorOpenedAndClosed() }, Modifier.fillMaxWidth())
                        CarButton(stringResource(R.string.sig_nav), { sensors.onNavigationSet() }, Modifier.fillMaxWidth())
                        CarButton(stringResource(R.string.sig_brake), { sensors.onParkingBrakeReleased() }, Modifier.fillMaxWidth())
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CarButton(stringResource(R.string.sig_cancel), { detector.cancel(); clearSensors() }, Modifier.weight(1f))
                            CarButton(stringResource(R.string.reset), {
                                detector.reset()
                                clearSensors()
                                spotId = null
                                spot = null
                                reward = false
                            }, Modifier.weight(1f))
                        }
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
                    Text("+1 크레딧", style = NsType.logo, color = NsColors.main)
                    Text("주차 자리를 넘겨드렸어요", style = NsType.nowQuestion, color = NsColors.text)
                }
            }
        }
    }
}
