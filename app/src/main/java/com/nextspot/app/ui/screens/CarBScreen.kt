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
import com.nextspot.app.ui.components.NsButton
import com.nextspot.app.ui.theme.NsDims
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
    var title by remember { mutableStateOf("목적지로 이동 중") }
    var message by remember { mutableStateOf("목적지 근처에 가면 자리를 찾아요") }
    var searchNote by remember { mutableStateOf("근처 자리를 기다리는 중…") }
    var refused by remember { mutableStateOf(false) }   // the driver said "아니요" to the offer

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
                    searchNote = "자리가 취소돼서 다시 찾는 중이에요"
                    accepted = null
                } else if (s != null && s.status == SpotStatus.MATCHED) {
                    accepted = s   // keep the latest copy (e.g. confirmed = true)
                }
            }
        }
        onDispose { reg?.remove() }
    }

    CarPage(stringResource(R.string.searching_car), onBack) {
        Column(Modifier.fillMaxWidth().widthIn(max = 1100.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            val acc = accepted
            when {
                acc != null -> {
                    CarCard(
                        "안내 중",
                        "${acc.metersFromCarB()}m 앞 자리로 이동 중",
                    )
                    Text(
                        if (acc.confirmed) "앞차가 출발했어요" else "앞차가 출발을 준비하고 있어요",
                        style = NsType.caption, color = NsColors.text2,
                    )
                    CarButton(
                        "주차했어요",
                        {
                            repo.markTaken(acc)
                            speaker.say("주차 완료. 감사합니다.")
                            message = "주차를 완료했어요"
                            title = "주차 완료"
                            accepted = null
                            searching = false
                        },
                        Modifier.fillMaxWidth(), primary = true,
                    )
                }

                offer != null -> {
                    // same look as the voice offer of the one-screen demo: question, mic hint, yes / no, sound bars.
                    // likely = "might open" (yellow), confirmed = "leaving now" (green): the change must be visible
                    val confirmedNow = offer.status == SpotStatus.CONFIRMED || offer.confirmed
                    OfferBadge(confirmedNow)
                    Text(
                        stringResource(R.string.offer, offer.metersFromCarB()),
                        style = NsType.nowQuestion.copy(lineBreak = NsType.keepAll), color = NsColors.text,
                    )
                    // TODO(zol): connect Platform.voiceInput(context).listen(...) here -
                    //   YES -> same as the "네" button (repo.accept), NO -> same as "아니요" (declined.add + refused = true),
                    //   UNKNOWN -> ask again; start listening after the TTS offer ends;
                    //   pass listening = true while the microphone is open; onTalk opens the microphone again
                    VoiceHint(listening = false, onTalk = { })
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NsButton(
                            stringResource(R.string.yes),
                            {
                                repo.accept(offer.id, CAR_B_ID) { ok ->
                                    if (ok) {
                                        refused = false
                                        accepted = offer
                                        navigator.navigateTo(offer.lat, offer.lng, "NextSpot 주차 자리")
                                        speaker.say("그 자리로 안내할게요.")
                                    } else {
                                        declined.add(offer.id)
                                        speaker.say("다른 차가 먼저 가져갔어요.")
                                    }
                                }
                            },
                            Modifier.weight(1f), NsDims.bigButtonH,
                            NsColors.main, NsColors.onMain, NsType.yesNo, padH = 0.dp,
                        )
                        NsButton(
                            stringResource(R.string.no),
                            { declined.add(offer.id); refused = true },
                            Modifier.weight(1f), NsDims.bigButtonH,
                            NsColors.surfaceHigh, NsColors.text, NsType.yesNo, padH = 0.dp,
                        )
                    }
                    VoiceWave(listening = false)
                }

                searching && refused -> {
                    // "No": say it clearly and offer to search again (same idea as the demo's closing card)
                    CarCard("제안을 거절했어요", "다른 자리가 나면 알려 드릴게요")
                    CarButton(
                        "다시 찾기",
                        { declined.clear(); refused = false; searchNote = "근처 자리를 기다리는 중…" },
                        Modifier.fillMaxWidth(), primary = true,
                    )
                }

                searching -> CarCard("자리 찾는 중", searchNote)

                else -> {
                    CarCard(title, message)
                    // Search starts by itself near the destination (or by voice) - not by a "find parking" button.
                    // Simulator: this button stands in for the navigation arrival event.
                    // TODO: in the real car replace with the NaviHelper arrival event:
                    //   Platform.carContext(context).start(onNearDestination = { searching = true }, onPosition = { _, _ -> })
                    //   (pleos threshold is NEAR_DESTINATION_M = 500)
                    // TODO(zol): second start path - the driver asks by voice ("주차할 곳 찾아줘") -> searching = true
                    //   (needs a keyword in VoiceInput.listen)
                    CarButton(
                        "목적지 500m 전 도착",
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
            if (confirmed) "출발 확정" else "곧 빌 가능성",
            style = NsType.button, color = NsColors.onMain, maxLines = 1,
        )
    }
}
