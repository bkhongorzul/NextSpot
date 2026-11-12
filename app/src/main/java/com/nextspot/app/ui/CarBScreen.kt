package com.nextspot.app.ui

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
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextspot.app.car.Platform
import com.nextspot.app.car.Speaker
import com.nextspot.app.data.DemoPlaces
import com.nextspot.app.data.Spot
import com.nextspot.app.data.SpotRepository
import com.nextspot.app.data.SpotStatus
import com.nextspot.app.data.distanceMeters
import kotlin.math.roundToInt

private const val CAR_B_ID = "car-B"

private fun Spot.metersFromCarB(): Int =
    distanceMeters(DemoPlaces.CAR_B_LAT, DemoPlaces.CAR_B_LNG, lat, lng).roundToInt()

/** Car B: the car searching for a parking spot. */
@Composable
fun CarBScreen(speaker: Speaker, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SpotRepository() }
    val navigator = remember { Platform.navigator(context) }

    var searching by remember { mutableStateOf(false) }
    var openSpots by remember { mutableStateOf<List<Spot>>(emptyList()) }
    val declined = remember { mutableStateListOf<String>() }
    val announced = remember { mutableStateListOf<String>() }
    var accepted by remember { mutableStateOf<Spot?>(null) }
    var message by remember { mutableStateOf("주차 자리 찾기를 눌러 주세요 · Tap Find parking") }

    DisposableEffect(Unit) {
        navigator.start()
        onDispose { navigator.stop() }
    }

    // While searching: listen for open spots in our group
    DisposableEffect(searching) {
        val reg = if (searching) {
            repo.listenOpenSpots(DemoPlaces.GROUP, CAR_B_ID) { openSpots = it }
        } else null
        onDispose { reg?.remove() }
    }

    // The closest open spot we haven't declined = the current offer
    val offer: Spot? = if (searching && accepted == null) {
        openSpots.filter { it.id !in declined }.minByOrNull { it.metersFromCarB() }
    } else null

    // Speak the offer once, when it first appears
    LaunchedEffect(offer?.id) {
        val o = offer ?: return@LaunchedEffect
        if (o.id !in announced) {
            announced.add(o.id)
            speaker.say("${o.metersFromCarB()}미터 앞에 자리가 곧 납니다. 갈까요?")
        }
    }

    // After accepting: watch the spot in case Car A cancels
    DisposableEffect(accepted?.id) {
        val id = accepted?.id
        val reg = id?.let {
            repo.listenSpot(it) { s ->
                if (s != null && s.status == SpotStatus.WITHDRAWN) {
                    speaker.say("자리가 취소됐어요. 다른 자리를 찾을게요.")
                    message = "자리 취소됨, 다시 찾는 중 · Spot cancelled, searching again"
                    accepted = null
                } else if (s != null && s.status == SpotStatus.MATCHED) {
                    accepted = s   // keep the latest copy (e.g. confirmed = true)
                }
            }
        }
        onDispose { reg?.remove() }
    }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Row { TextButton(onClick = onBack) { Text("← 역할 바꾸기 · Change role") } }
        Text("차 B · 자리 찾는 차 (Car B, searching)", fontSize = 24.sp)
        Spacer(Modifier.height(16.dp))

        val acc = accepted
        when {
            acc != null -> {
                StatusCard(
                    title = "안내 중 · Guiding you",
                    body = "${acc.metersFromCarB()} m 앞 자리로 이동 중",
                )
                Text(
                    if (acc.confirmed) "앞차 출발 확정 · Car A is leaving now"
                    else "앞차 출발 준비 중 · Car A getting ready",
                    fontSize = 18.sp,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        repo.markTaken(acc)
                        speaker.say("주차 완료. 감사합니다.")
                        message = "주차 완료 · Parked"
                        accepted = null
                        searching = false
                    },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) { Text("주차했어요 · I parked", fontSize = 20.sp) }
            }

            offer != null -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("${offer.metersFromCarB()} m 앞에 자리가 납니다", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("A spot is opening ${offer.metersFromCarB()} m ahead", fontSize = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(statusLabel(offer.status), fontSize = 18.sp)
                        Spacer(Modifier.height(16.dp))
                        Row {
                            Button(
                                onClick = {
                                    repo.accept(offer.id, CAR_B_ID) { ok ->
                                        if (ok) {
                                            accepted = offer
                                            navigator.navigateTo(offer.lat, offer.lng, "NextSpot 주차 자리")
                                            speaker.say("그 자리로 안내할게요.")
                                        } else {
                                            declined.add(offer.id)
                                            speaker.say("다른 차가 먼저 가져갔어요.")
                                        }
                                    }
                                },
                                modifier = Modifier.height(64.dp),
                            ) { Text("응, 갈게 · Yes", fontSize = 20.sp) }
                            Spacer(Modifier.width(16.dp))
                            OutlinedButton(
                                onClick = { declined.add(offer.id) },
                                modifier = Modifier.height(64.dp),
                            ) { Text("아니 · No", fontSize = 20.sp) }
                        }
                    }
                }
            }

            searching -> StatusCard(title = "찾는 중 · Searching", body = "근처 자리를 기다리는 중…")

            else -> {
                Text(message, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        searching = true
                        speaker.say("근처 주차 자리를 찾을게요.")
                    },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                ) { Text("주차 자리 찾기 · Find parking", fontSize = 22.sp) }
            }
        }
    }
}
