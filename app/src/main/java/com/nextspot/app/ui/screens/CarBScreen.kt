package com.nextspot.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextspot.app.car.Platform
import com.nextspot.app.car.CarSignals
import com.nextspot.app.car.Speaker
import com.nextspot.app.car.VoiceInput
import com.nextspot.app.data.DemoPlaces
import com.nextspot.app.data.Spot
import com.nextspot.app.data.SpotRepository
import com.nextspot.app.data.SpotStatus
import com.nextspot.app.data.distanceMeters
import com.nextspot.app.ui.components.CarBox
import com.nextspot.app.ui.components.CarFrame
import com.nextspot.app.ui.components.CarGlyph
import com.nextspot.app.ui.components.CenterColumn
import com.nextspot.app.ui.components.Eyebrow
import com.nextspot.app.ui.components.Headline
import com.nextspot.app.ui.components.HintRow
import com.nextspot.app.ui.components.MarkKind
import com.nextspot.app.ui.components.MicBadge
import com.nextspot.app.ui.components.NsIcon
import com.nextspot.app.ui.components.NsPaths
import com.nextspot.app.ui.components.Pill
import com.nextspot.app.ui.components.Radar
import com.nextspot.app.ui.components.SimButton
import com.nextspot.app.ui.components.StatusMark
import com.nextspot.app.ui.components.StepTrack
import com.nextspot.app.ui.components.SubText
import com.nextspot.app.ui.components.TextLink
import com.nextspot.app.ui.theme.NsCar
import com.nextspot.app.ui.theme.NsType
import kotlin.math.roundToInt

private const val CAR_B_ID = "car-B"

private fun Spot.metersFromCarB(): Int =
    distanceMeters(DemoPlaces.CAR_B_LAT, DemoPlaces.CAR_B_LNG, lat, lng).roundToInt()

/**
 * Car B: the car searching for a parking spot. Screens follow the design photos:
 * driving -> B1 start offer -> B2 searching -> B3 candidate -> B4 on the way -> B5 spot confirmed -> B6 parked.
 * Voice is the main way to answer; the underlined texts are the quiet touch alternative.
 */
@Composable
fun CarBScreen(speaker: Speaker, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SpotRepository() }
    val navigator = remember { Platform.navigator(context) }
    val voice = remember { Platform.voiceInput(context) }          // Pleos SpeechToText in the pleos build
    val carContext = remember { Platform.carContext(context) }     // Pleos NaviHelper: near the destination
    val ownSignals = remember { Platform.carSignals(context) }     // Car B's own parking brake / gear

    var arrived by remember { mutableStateOf(false) }      // near the destination: B1
    var searching by remember { mutableStateOf(false) }    // B2 and on
    var openSpots by remember { mutableStateOf<List<Spot>>(emptyList()) }
    val declined = remember { mutableStateListOf<String>() }
    val announced = remember { mutableStateListOf<String>() }
    var accepted by remember { mutableStateOf<Spot?>(null) }
    var parked by remember { mutableStateOf(false) }       // B6
    var thanked by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }      // the driver said "아니요" to the offer
    var searchNote by remember { mutableStateOf("비슷한 경로로 움직이는 차들을 확인하고 있어요") }
    var listening by remember { mutableStateOf(false) }
    var lastLeavingCar by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        navigator.start()
        // Real car: NaviHelper reports < 500 m to the destination -> B1 appears by itself
        carContext.start(
            onNearDestination = { if (!arrived && !searching && !parked) arrived = true },
            onPosition = { _, _ -> },
        )
        onDispose { navigator.stop(); carContext.stop(); voice.stop() }
    }

    /** Listen once for the driver's answer. Calls back with what was heard. */
    fun listenOnce(onHeard: (VoiceInput.Answer, String) -> Unit) {
        if (!voice.available) return            // no mic / no recognizer: the touch links still work
        listening = true
        voice.listen(
            onAnswer = { a, heard -> listening = false; onHeard(a, heard) },
            onUnavailable = { listening = false },
        )
    }

    fun startSearch() {
        searching = true
        speaker.say("근처 주차 자리를 찾을게요.")
    }

    // B1: ask once by voice, then listen for "자리 찾아줘" (or a plain "네")
    LaunchedEffect(arrived) {
        if (!arrived || searching) return@LaunchedEffect
        speaker.say("목적지 근처예요. 자리를 찾아드릴까요?") {
            listenOnce { a, heard ->
                val wants = a == VoiceInput.Answer.YES || listOf("자리", "찾아", "주차").any { heard.contains(it) }
                if (wants && !searching) startSearch()
            }
        }
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

    // Speak the offer once, when it first appears; voice answer is wired below (after accept() is defined)
    var offerToHear by remember { mutableStateOf<Spot?>(null) }
    LaunchedEffect(offer?.id) {
        val o = offer ?: return@LaunchedEffect
        if (o.id !in announced) {
            announced.add(o.id)
            speaker.say("${o.metersFromCarB()}미터 앞에 자리가 곧 납니다. 갈까요?") { offerToHear = o }
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

    fun accept(o: Spot) {
        repo.accept(o.id, CAR_B_ID) { ok ->
            if (ok) {
                refused = false
                accepted = o
                navigator.navigateTo(o.lat, o.lng, "NextSpot 주차 자리")
                speaker.say("그 자리로 안내할게요.")
            } else {
                declined.add(o.id)
                speaker.say("다른 차가 먼저 가져갔어요.")
            }
        }
    }

    // B3 voice answer: "네" -> accept, "아니요" -> next spot, not understood -> ask once more
    fun hearOffer(o: Spot, retry: Boolean) {
        listenOnce { a, _ ->
            if (accepted != null || o.id in declined) return@listenOnce
            when (a) {
                VoiceInput.Answer.YES -> accept(o)
                VoiceInput.Answer.NO -> {
                    declined.add(o.id); refused = true
                    speaker.say("알겠어요. 다른 자리를 찾을게요.")
                }
                VoiceInput.Answer.UNKNOWN -> if (retry) speaker.say("네 또는 아니요로 말씀해 주세요.") { hearOffer(o, false) }
            }
        }
    }
    LaunchedEffect(offerToHear?.id) {
        val o = offerToHear ?: return@LaunchedEffect
        hearOffer(o, retry = true)
    }

    fun parkNow(acc: Spot) {
        lastLeavingCar = acc.leavingCar
        repo.markTaken(acc)
        speaker.say("주차 완료. 감사합니다.")
        accepted = null
        searching = false
        parked = true
    }

    // Car B's own car signals: parking brake set / gear into P while a spot is reserved -> parked
    DisposableEffect(Unit) {
        ownSignals.start(object : CarSignals.Listener {
            override fun onDoorOpenedAndClosed() = Unit
            override fun onNavigationSet() = Unit
            override fun onParkingBrakeReleased() = Unit
            override fun onParked() { accepted?.let { if (!parked) parkNow(it) } }
        })
        onDispose { ownSignals.stop() }
    }

    val acc = accepted
    CarFrame(onBack) {
        when {
            // B6 parked
            parked -> {
                CenterColumn {
                    StatusMark(MarkKind.FILLED)
                    Spacer(Modifier.height(40.dp))
                    Headline("주차 완료", center = true)
                    Spacer(Modifier.height(12.dp))
                    SubText("자리를 양보해준 차량 덕분에 더 빨리 찾았어요", center = true)
                    Spacer(Modifier.height(44.dp))
                    // "고마워요" is stored on the leaving car's credits document (field "thanks")
                    Row(
                        Modifier.clip(NsCar.shape).background(NsCar.cardHigh)
                            .clickableBox { if (!thanked) lastLeavingCar?.let { repo.sendThanks(it) }; thanked = true }.padding(horizontal = 48.dp, vertical = 30.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(if (thanked) "고마움을 전했어요" else "고마워요 보내기", style = NsType.button.copy(fontSize = 30.sp), color = NsCar.text)
                    }
                }
            }

            // B5 spot confirmed: the parking brake of Car B finishes the trip by itself in the real car
            acc != null && acc.confirmed -> {
                CenterColumn {
                    StatusMark(MarkKind.RING)
                    Spacer(Modifier.height(40.dp))
                    Headline("자리가 확정됐어요", center = true)
                    Spacer(Modifier.height(12.dp))
                    SubText("이제 편하게 주차하시면 돼요", center = true)
                    Spacer(Modifier.height(44.dp))
                    HintRow("브레이크 신호로 주차 완료를 자동으로 확인할게요")
                }
                // Simulator: stands in for Car B's own parking brake signal.
                // Real car: ownSignals.onParked (Pleos Vehicle SDK: parking brake set / gear into P) finishes by itself.
                Box(Modifier.align(Alignment.BottomEnd)) { SimButton("시뮬레이터 · 주차 브레이크 체결") { parkNow(acc) } }
            }

            // B4 on the way: Car A's live state is shown
            acc != null -> {
                Column(Modifier.fillMaxSize()) {
                    Eyebrow("안내 중")
                    Spacer(Modifier.height(10.dp))
                    Headline("지정 자리로 이동 중")
                    Spacer(Modifier.height(10.dp))
                    SubText("${acc.metersFromCarB()}m 앞")
                    Spacer(Modifier.weight(1f))
                    StepTrack(current = 2)
                    Spacer(Modifier.weight(1f))
                    CarBox(pad = 36.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Box(Modifier.size(64.dp).clip(androidx.compose.foundation.shape.CircleShape).background(NsCar.cardHigh), contentAlignment = Alignment.Center) {
                                NsIcon(NsPaths.CLOCK, NsCar.accent, 34.dp, strokeWidth = 1.8f)
                            }
                            Column {
                                Text("앞차가 출발을 준비하고 있어요", style = NsType.title.copy(fontSize = 32.sp), color = NsCar.text)
                                Text("자리가 곧 확정돼요", style = NsType.caption, color = NsCar.text2)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    // "다른 자리 찾기": the reserved spot goes back to the open list for other cars
                    Box(
                        Modifier.fillMaxWidth().clip(NsCar.shape).background(NsCar.cardHigh)
                            .clickableBox { repo.release(acc.id, CAR_B_ID); declined.add(acc.id); accepted = null; refused = false; searchNote = "다른 자리를 찾는 중이에요" }
                            .padding(vertical = 34.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("다른 자리 찾기", style = NsType.button.copy(fontSize = 32.sp), color = NsCar.text) }
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.align(Alignment.End)) { SimButton("시뮬레이터 · 주차 브레이크 체결") { parkNow(acc) } }
                }
            }

            // B3 candidate
            offer != null -> {
                val confirmedNow = offer.status == SpotStatus.CONFIRMED || offer.confirmed
                Column(Modifier.fillMaxSize()) {
                    Eyebrow("후보 발견")
                    Spacer(Modifier.height(10.dp))
                    Headline(if (confirmedNow) "앞차가 출발해요" else "곧 빈자리가 생겨요")
                    Spacer(Modifier.height(28.dp))
                    CarBox(pad = 36.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NsIcon(NsPaths.PIN, NsCar.accent, 44.dp, strokeWidth = 1.8f)
                            Spacer(Modifier.width(22.dp))
                            Text("약 ${offer.metersFromCarB()}m 앞", style = NsType.title.copy(fontSize = 38.sp), color = NsCar.text)
                            Spacer(Modifier.weight(1f))
                            // likely = might open, confirmed = leaving now (the change must be visible): plain yellow words
                            Text(
                                if (confirmedNow) "출발 확정" else "곧 비는 자리",
                                style = NsType.bodyBold.copy(fontSize = 30.sp), color = NsCar.accent,
                            )
                        }
                        Spacer(Modifier.height(26.dp))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(NsCar.border))
                        Spacer(Modifier.height(24.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            NsIcon(if (confirmedNow) NsPaths.BRAKE else NsPaths.DETECT, NsCar.text2, 32.dp, strokeWidth = 1.8f)
                            Text(
                                if (confirmedNow) "앞차가 브레이크를 풀고 출발해요" else "앞차 운전자가 출발을 준비하고 있어요",
                                style = NsType.bodyBold.copy(fontSize = 28.sp), color = NsCar.text2,
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    // voice first: the big card asks for the voice, touch is the small underlined text below
                    // voice: hearOffer() listens after the spoken offer ends ("네" / "아니요")
                    // no extra card here: the voice prompt stands free, the only card is the info above
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CenterColumn {
                            MicBadge()
                            Text("이 자리로 갈까요?", style = NsType.logo.copy(fontSize = 46.sp, lineHeight = 56.sp), color = NsCar.text)
                            Spacer(Modifier.height(12.dp))
                            SubText("이 자리로 안내할게요", center = true)
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        TextLink("네", { accept(offer) })
                        Text("·", color = NsCar.text3, style = NsType.body)
                        TextLink("아니요", { declined.add(offer.id); refused = true })
                    }
                }
            }

            // B2 searching (also after "아니요" or a cancelled spot)
            searching -> {
                CenterColumn {
                    Radar()
                    Spacer(Modifier.height(36.dp))
                    Headline("근처 자리를 찾는 중", center = true)
                    Spacer(Modifier.height(12.dp))
                    SubText(searchNote, center = true)
                    Spacer(Modifier.height(36.dp))
                    HintRow("잠시만 기다려주세요")
                    if (refused) {
                        Spacer(Modifier.height(12.dp))
                        TextLink("방금 자리 다시 보기", { declined.clear(); refused = false; searchNote = "비슷한 경로로 움직이는 차들을 확인하고 있어요" })
                    }
                }
            }

            // B1 start offer: voice "자리 찾아줘", tap is the quiet alternative
            arrived -> {
                // Real car: carContext (Pleos NaviHelper) opens this screen < 500 m before the destination,
                // then the app asks by voice and listens for "자리 찾아줘" (see LaunchedEffect(arrived) above).
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(1f))
                    // the distance line sits right on top of the mic card
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NsIcon(NsPaths.PIN, NsCar.text2, 34.dp, strokeWidth = 1.8f)
                        Spacer(Modifier.width(16.dp))
                        Text("목적지까지 500m · 근처에서 자리를 찾아볼까요?", style = NsType.bodyBold.copy(fontSize = 28.sp), color = NsCar.text2)
                    }
                    CarBox(pad = 36.dp) {   // height follows the content, so nothing is cut off
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            MicBadge()
                            Text("\"자리 찾아줘\"", style = NsType.logo.copy(fontSize = 58.sp, lineHeight = 68.sp), color = NsCar.text)
                            Spacer(Modifier.height(10.dp))
                            SubText("이렇게 말하면 바로 찾기 시작해요", center = true)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextLink(if (listening) "듣는 중… 또는 화면을 탭해서 시작" else "또는 화면을 탭해서 시작", { startSearch() })
                    }
                }
            }

            // driving to the destination (before B1)
            else -> {
                CenterColumn {
                    Headline("목적지로 이동 중", center = true)
                    Spacer(Modifier.height(12.dp))
                    SubText("목적지 근처에 가면 자리를 찾아드려요", center = true)
                }
                // Simulator: stands in for the navigation arrival event (see the TODO at B1)
                Box(Modifier.align(Alignment.BottomEnd)) { SimButton("시뮬레이터 · 목적지 500m 전 도착") { arrived = true } }
            }
        }
    }
}

private fun Modifier.clickableBox(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
