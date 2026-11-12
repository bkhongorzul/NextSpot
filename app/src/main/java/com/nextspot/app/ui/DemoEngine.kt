package com.nextspot.app.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.ListenerRegistration
import com.nextspot.app.car.CarSignals
import com.nextspot.app.car.DepartureDetector
import com.nextspot.app.car.Platform
import com.nextspot.app.car.Speaker
import com.nextspot.app.car.VoiceAnswer
import com.nextspot.app.data.DemoPlaces
import com.nextspot.app.data.Spot
import com.nextspot.app.data.SpotRepository
import com.nextspot.app.data.SpotStatus
import com.nextspot.app.data.distanceMeters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Runs BOTH cars on one screen using the real logic:
 *  - Car A: car signals -> DepartureDetector -> Firebase (likely / confirmed / withdrawn)
 *  - Car B: listens to Firebase -> distance -> voice offer -> accept (transaction) -> navigate -> parked
 * Nothing on the map is scripted: every color, distance and voice line comes from Firebase data.
 * The screen only reads the state below; the "Play scenario" button just presses the car signals in order.
 */
class DemoEngine(
    context: Context,
    private val speaker: Speaker,
    private val scope: CoroutineScope,
) : CarSignals.Listener {

    data class LogItem(val time: String, val who: String, val text: String)

    enum class Phase { IDLE, SEARCHING, LIKELY, OFFER, RESERVED, CONFIRMED, TAKEN, WITHDRAWN }

    private val repo = SpotRepository()
    private val signals = Platform.carSignals(context)   // real Pleos signals in the "pleos" build
    private val navigator = Platform.navigator(context)
    val voice = VoiceAnswer(context)

    // ---- state the screen reads ----
    var door by mutableStateOf(false); private set
    var nav by mutableStateOf(false); private set
    var brake by mutableStateOf(false); private set
    var searching by mutableStateOf(false); private set
    var spot by mutableStateOf<Spot?>(null); private set            // Car A's spot, live from Firebase
    var offerMeters by mutableStateOf<Int?>(null); private set       // what Car B received
    var asking by mutableStateOf(false); private set                 // voice offer on screen
    var listening by mutableStateOf(false); private set              // microphone open
    var accepted by mutableStateOf(false); private set
    var parked by mutableStateOf(false); private set
    var credits by mutableLongStateOf(0L); private set
    var autoPlaying by mutableStateOf(false); private set
    val log = mutableStateListOf<LogItem>()
    val signalLabel: String get() = signals.label

    private var runStart = System.currentTimeMillis()
    private var spotReg: ListenerRegistration? = null
    private var openReg: ListenerRegistration? = null
    private val creditsReg = repo.listenCredits(CAR_A) { credits = it }
    private var autoJob: Job? = null
    private var lastSpotStatus: String? = null

    private val detector = DepartureDetector(
        repo = repo, carId = CAR_A, group = DemoPlaces.GROUP,
        lat = DemoPlaces.CAR_A_LAT, lng = DemoPlaces.CAR_A_LNG,
        onSpotPosted = { id -> watchCarASpot(id) },
        onLog = { },
    )

    val phase: Phase
        get() {
            val s = spot
            return when {
                s?.status == SpotStatus.WITHDRAWN -> Phase.WITHDRAWN
                parked || s?.status == SpotStatus.TAKEN -> Phase.TAKEN
                s != null && s.confirmed -> Phase.CONFIRMED
                accepted -> Phase.RESERVED
                asking -> Phase.OFFER
                s?.status == SpotStatus.LIKELY -> Phase.LIKELY
                searching -> Phase.SEARCHING
                else -> Phase.IDLE
            }
        }

    init {
        signals.start(this)
        navigator.start()
        add("시스템", "준비 완료 · 신호: ${signals.label}")
    }

    // ---------------- Car A: signals ----------------
    override fun onDoorOpenedAndClosed() {
        door = true
        add("Car A", "🚪 문 열고 닫음 (차 신호)")
        detector.onDoorOpenedAndClosed()
    }

    override fun onNavigationSet() {
        nav = true
        add("Car A", "🧭 내비 목적지 설정 (차 신호)")
        detector.onNavigationSet()
    }

    override fun onParkingBrakeReleased() {
        if (brake) return
        brake = true
        add("Car A", "🅿️ 주차 브레이크 해제 (차 신호)")
        detector.onParkingBrakeReleased()
    }

    /** Car A's driver decides to stay. */
    fun cancelDeparture() {
        add("Car A", "✋ 운전자가 출발하지 않음")
        detector.cancel()
    }

    private fun watchCarASpot(id: String) {
        spotReg?.remove()
        spotReg = repo.listenSpot(id) { s ->
            spot = s
            val st = s?.status
            if (st != null && st != lastSpotStatus) {
                lastSpotStatus = st
                onSpotChanged(s)
            }
        }
    }

    private fun onSpotChanged(s: Spot) {
        when (s.status) {
            SpotStatus.LIKELY -> add("클라우드", "📡 자리 등록: 곧 빌 가능성 (likely)")
            SpotStatus.CONFIRMED -> add("클라우드", "📡 자리 확정 (confirmed)")
            SpotStatus.MATCHED -> add("클라우드", "🔒 Car B에게 예약됨 (matched)")
            SpotStatus.TAKEN -> add("클라우드", "✅ 주차 완료 · Car A 크레딧 +1")
            SpotStatus.WITHDRAWN -> {
                add("클라우드", "❌ 제안 철회 (withdrawn)")
                if (accepted || asking) {
                    voice.stop(); listening = false; asking = false; accepted = false
                    add("Car B", "🔊 \"자리가 취소됐어요. 다른 자리를 찾을게요.\"")
                    speaker.say("자리가 취소됐어요. 다른 자리를 찾을게요.")
                }
            }
        }
        if (s.status == SpotStatus.MATCHED && s.confirmed) add("클라우드", "📡 출발 확정 (confirmed)")
    }

    // ---------------- Car B: search, offer, answer ----------------
    fun startSearch() {
        if (searching) return
        searching = true
        add("Car B", "🔍 주차 자리 탐색 시작")
        openReg?.remove()
        openReg = repo.listenOpenSpots(DemoPlaces.GROUP, CAR_B) { list ->
            if (accepted || asking || parked) return@listenOpenSpots
            val fresh = list.filter { it.updatedAt >= runStart }
            val best = fresh.minByOrNull { metersFromB(it) } ?: return@listenOpenSpots
            offer(best)
        }
    }

    private fun offer(s: Spot) {
        val m = metersFromB(s)
        offerMeters = m
        asking = true
        add("Car B", "📥 자리 수신: ${m}m 앞 (${s.status})")
        val sentence = "${m}미터 앞에 자리가 곧 납니다. 갈까요?"
        add("Car B", "🔊 \"$sentence\"")
        speaker.say(sentence) {
            if (!asking) return@say
            if (autoPlaying) {
                scope.launch {
                    delay(1200)
                    if (asking) { add("Car B", "🗣 운전자: \"네\" (자동 재생)"); answerYes() }
                }
            } else {
                listenForAnswer()
            }
        }
    }

    fun listenForAnswer() {
        if (!asking) return
        if (!voice.available) {
            add("Car B", "🎤 음성 인식 사용 불가 → 버튼으로 답해 주세요")
            return
        }
        listening = true
        add("Car B", "🎤 듣는 중… (\"네\" / \"아니요\")")
        voice.listen(
            onAnswer = { answer, heard ->
                listening = false
                when (answer) {
                    VoiceAnswer.Answer.YES -> { add("Car B", "🗣 운전자: \"$heard\" → 수락"); answerYes() }
                    VoiceAnswer.Answer.NO -> { add("Car B", "🗣 운전자: \"$heard\" → 거절"); answerNo() }
                    VoiceAnswer.Answer.UNKNOWN -> add("Car B", "🎤 못 알아들었어요 → 다시 말하거나 버튼")
                }
            },
            onUnavailable = { listening = false },
        )
    }

    fun answerYes() {
        val s = spot ?: return
        voice.stop(); listening = false
        repo.accept(s.id, CAR_B) { ok ->
            asking = false
            if (ok) {
                accepted = true
                add("Car B", "✅ 수락 → 내비가 그 자리로 안내")
                navigator.navigateTo(s.lat, s.lng, "NextSpot 주차 자리")
                speaker.say("그 자리로 안내할게요.")
            } else {
                add("Car B", "⚠️ 다른 차가 먼저 가져감")
                speaker.say("다른 차가 먼저 가져갔어요.")
            }
        }
    }

    fun answerNo() {
        voice.stop(); listening = false
        asking = false
        add("Car B", "↩️ 거절 → 계속 탐색")
    }

    fun parkCarB() {
        val s = spot ?: return
        if (!accepted || parked) return
        parked = true
        repo.markTaken(s)
        add("Car B", "🚗 주차 완료")
        speaker.say("자리를 넘겨드렸어요. 크레딧 1점이 적립됐어요.")
    }

    // ---------------- reset + auto play ----------------
    fun reset(onReady: () -> Unit = {}) {
        autoJob?.cancel(); autoPlaying = false
        voice.stop()
        detector.reset()
        spotReg?.remove(); openReg?.remove()
        spot = null; lastSpotStatus = null
        door = false; nav = false; brake = false
        searching = false; asking = false; listening = false; accepted = false; parked = false
        offerMeters = null
        log.clear()
        add("시스템", "초기화 · 이전 데모 자리 정리 중")
        repo.clearGroup(DemoPlaces.GROUP) {
            runStart = System.currentTimeMillis()
            onReady()
        }
    }

    /** Presses the car signals in order and waits for the real Firebase result at each step. */
    fun playScenario(cancelCase: Boolean) {
        reset {
            autoPlaying = true
            autoJob = scope.launch {
                delay(1200); startSearch()
                delay(2500); onDoorOpenedAndClosed()
                delay(1500); onNavigationSet()
                waitUntil(15_000) { accepted }
                delay(2500)
                if (cancelCase) {
                    cancelDeparture()
                } else {
                    onParkingBrakeReleased()
                    waitUntil(8_000) { spot?.confirmed == true }
                    delay(3500); parkCarB()
                }
                delay(1500)
                autoPlaying = false
            }
        }
    }

    fun stopAutoPlay() { autoJob?.cancel(); autoPlaying = false }

    private suspend fun waitUntil(maxMs: Long, cond: () -> Boolean) {
        var t = 0L
        while (!cond() && t < maxMs) { delay(200); t += 200 }
    }

    fun dispose() {
        autoJob?.cancel()
        voice.stop()
        signals.stop(); navigator.stop()
        spotReg?.remove(); openReg?.remove(); creditsReg.remove()
    }

    private fun metersFromB(s: Spot): Int =
        distanceMeters(DemoPlaces.CAR_B_LAT, DemoPlaces.CAR_B_LNG, s.lat, s.lng).roundToInt()

    private fun add(who: String, text: String) {
        log.add(0, LogItem(SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date()), who, text))
    }

    companion object {
        const val CAR_A = "car-A"
        const val CAR_B = "car-B"
    }
}
