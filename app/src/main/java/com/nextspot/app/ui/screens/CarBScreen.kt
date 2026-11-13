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
import com.nextspot.app.ui.components.VoiceHint
import com.nextspot.app.ui.components.VoiceWave
import com.nextspot.app.ui.theme.NsShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
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
    var message by remember { mutableStateOf("목적지 근처에 가면 자리를 찾아요 · Searching starts near the destination") }

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
                    // likely = "might open" (yellow), confirmed = "leaving now" (green): the change must be visible
                    val confirmedNow = offer.status == SpotStatus.CONFIRMED || offer.confirmed
                    CarCard(
                        "${offer.metersFromCarB()} m 앞 · ${offer.metersFromCarB()} m ahead",
                        if (confirmedNow) "자리가 확정됐어요" else "곧 자리가 날 것 같아요",
                    )
                    OfferBadge(confirmedNow)
                    // voice offer: the driver answers by voice in the car; the buttons stand in here
                    // TODO(zol): connect Platform.voiceInput(context).listen(...) here -
                    //   YES -> same as the "Yes" button (repo.accept), NO -> declined.add(offer.id),
                    //   UNKNOWN -> ask again; start listening after the TTS offer ends; pass listening = true while the mic is open
                    VoiceHint(listening = false, onTalk = { /* TODO(zol): open the microphone again */ })
                    VoiceWave(listening = false)
                    Text(
                        "실차에서는 음성으로 수락 · In the car, the driver accepts by voice",
                        style = NsType.caption, color = NsColors.text3,
                    )
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
                    CarCard("목적지로 이동 중 · Driving to destination", message)
                    // Search starts by itself near the destination (or by voice) - not by a "find parking" button.
                    // SIMULATOR: this button stands in for the navigation arrival event.
                    // TODO: in the real car replace with the NaviHelper arrival event:
                    //   Platform.carContext(context).start(onNearDestination = { searching = true }, onPosition = { _, _ -> })
                    //   (pleos threshold is NEAR_DESTINATION_M = 500)
                    // TODO(zol): second start path - the driver asks by voice ("주차할 곳 찾아줘") -> searching = true
                    //   (needs a keyword in VoiceInput.listen)
                    Text(
                        "SIMULATOR · 실차에서는 내비 도착 이벤트로 자동 시작 · In the car: starts automatically on navigation arrival",
                        style = NsType.caption, color = NsColors.text2,
                    )
                    CarButton(
                        "목적지 500m 전 도착 · Arrive 500 m from destination",
                        {
                            searching = true
                            speaker.say("근처 주차 자리를 찾을게요.")
                        },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** "Likely opening" (yellow) turns into "Confirmed" (green): the two stages Car B sees. */
@Composable
private fun OfferBadge(confirmed: Boolean) {
    val bg by animateColorAsState(if (confirmed) NsColors.ok else NsColors.main, tween(300), label = "badge")
    Row(
        Modifier.clip(NsShape.button).background(bg).padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (confirmed) "출발 확정 · Confirmed" else "곧 빌 가능성 · Likely opening",
            style = NsType.button, color = NsColors.onMain, maxLines = 1,
        )
    }
}
