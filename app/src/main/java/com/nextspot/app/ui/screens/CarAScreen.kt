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
import com.nextspot.app.ui.components.StatusCard
import com.nextspot.app.ui.components.statusLabel
import com.nextspot.app.ui.theme.NsTheme

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
                        SpotStatus.TAKEN -> speaker.say("자리를 넘겨드렸어요. 크레딧 1점이 적립됐어요.")
                    }
                    lastStatus = status
                }
            }
        }
        onDispose { reg?.remove() }
    }

    NsTheme {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = com.nextspot.app.ui.theme.NsColors.appBg) {
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())
    ) {
        Row {
            TextButton(onClick = onBack) { Text("← 역할 바꾸기 · Change role") }
        }
        Text("차 A · 떠나는 차 (Car A, leaving)", fontSize = 24.sp)
        Text("신호: ${signals.label} · 크레딧 (credits): $credits", fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))

        StatusCard(title = "내 자리 상태 · My spot", body = statusLabel(spot?.status))
        Spacer(Modifier.height(24.dp))

        Text("차 신호 (버튼으로 대신하기) · Car signals (buttons stand in)", fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Button(onClick = { detector.onDoorOpenedAndClosed() }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text("1. 문 열고 닫기 · Door opened and closed", fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { detector.onNavigationSet() }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text("2. 집으로 내비 설정 · Set navigation home", fontSize = 18.sp)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { detector.onParkingBrakeReleased() }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
            Text("3. 주차 브레이크 해제 · Release parking brake", fontSize = 18.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row {
            OutlinedButton(onClick = { detector.cancel() }) { Text("안 떠나요 · Cancel") }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = {
                detector.reset()
                spotId = null
                spot = null
                logs.clear()
            }) { Text("데모 초기화 · Reset") }
        }

        Spacer(Modifier.height(24.dp))
        Text("기록 · Log", fontSize = 16.sp)
        logs.forEach { Text("• $it", fontSize = 14.sp) }
    }
        }
    }
}
