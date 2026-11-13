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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.widthIn
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

    CarPage(stringResource(R.string.searching_car) + " \u00b7 Car B", onBack) {
        Column(Modifier.fillMaxWidth().widthIn(max = 1100.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            val acc = accepted
            when {
                acc != null -> {
                    CarCard(
                        "안내 중 · Guiding you",
                        "${acc.metersFromCarB()} m 앞 자리로 이동 중",
                    )
                    Text(
                        if (acc.confirmed) "앞차 출발 확정 · Car A is leaving now"
                        else "앞차 출발 준비 중 · Car A getting ready",
                        style = NsType.caption, color = NsColors.text2,
                    )
                    CarButton(
                        "주차했어요 · I parked",
                        {
                            repo.markTaken(acc)
                            speaker.say("주차 완료. 감사합니다.")
                            message = "주차 완료 · Parked"
                            accepted = null
                            searching = false
                        },
                        Modifier.fillMaxWidth(), primary = true,
                    )
                }

                offer != null -> {
                    CarCard("${offer.metersFromCarB()} m 앞에 자리가 납니다", "A spot is opening ${offer.metersFromCarB()} m ahead")
                    Text(statusLabel(offer.status), style = NsType.caption, color = NsColors.text2)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CarButton(
                            "응, 갈게 · Yes",
                            {
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
                            Modifier.weight(1f), primary = true,
                        )
                        CarButton("아니 · No", { declined.add(offer.id) }, Modifier.weight(1f))
                    }
                }

                searching -> CarCard("찾는 중 · Searching", "근처 자리를 기다리는 중…")

                else -> {
                    Text(message, style = NsType.caption, color = NsColors.text2)
                    CarButton(
                        "주차 자리 찾기 · Find parking",
                        {
                            searching = true
                            speaker.say("근처 주차 자리를 찾을게요.")
                        },
                        Modifier.fillMaxWidth(), primary = true,
                    )
                }
            }
        }
    }
}
