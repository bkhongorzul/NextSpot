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
import com.nextspot.app.R
import com.nextspot.app.car.VoiceInput
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

    private val appContext = context.applicationContext
    private fun str(id: Int, vararg a: Any) = appContext.getString(id, *a)

    private val repo = SpotRepository()
    private val signals = Platform.carSignals(context)   // real Pleos signals in the "pleos" build
    private val navigator = Platform.navigator(context)
    private val carContext = Platform.carContext(context)   // Car B: near destination (Pleos NaviHelper)
    val voice: VoiceInput = Platform.voiceInput(context)

    // ---- state the screen reads ----
    var door by mutableStateOf(false); private set
    var belt by mutableStateOf(false); private set
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

    /** Which SDK powers each part (shown on screen so judges can see the Pleos SDK use). */
    val sdkLines: List<Pair<String, String>>
        get() = listOf(
            "Car A 신호" to signals.label,
            "Car B 목적지 근처" to carContext.label,
            "음성 안내" to speaker.label,
            "음성 대답" to voice.label,
            "자리 공유" to "Firebase Firestore",
        )

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
        carContext.start(
            onNearDestination = { m ->
                add("Car B", str(R.string.log_near, m))
                startSearch()
            },
            onPosition = { lat, lng -> lastPosition = "%.5f, %.5f".format(lat, lng) },
        )
        add("시스템", str(R.string.log_ready))
    }

    /** Car B's real position from Pleos (shown in the log when it arrives). */
    var lastPosition by mutableStateOf<String?>(null); private set

    // ---------------- Car A: signals ----------------
    override fun onDoorOpenedAndClosed() {
        door = true
        add("Car A", str(R.string.log_door))
        detector.onDoorOpenedAndClosed()
    }

    override fun onSeatbeltBuckled() {
        belt = true
        add("Car A", str(R.string.log_belt))
        detector.onSeatbeltBuckled()
    }

    override fun onNavigationSet() {
        nav = true
        add("Car A", str(R.string.log_nav))
        detector.onNavigationSet()
    }

    override fun onParkingBrakeReleased() {
        if (brake) return
        brake = true
        add("Car A", str(R.string.log_brake))
        detector.onParkingBrakeReleased()
    }

    /** Car A's driver decides to stay. */
    fun cancelDeparture() {
        add("Car A", str(R.string.log_cancel))
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
            SpotStatus.LIKELY -> add("클라우드", str(R.string.log_likely))
            SpotStatus.CONFIRMED -> add("클라우드", str(R.string.log_spot_confirmed))
            SpotStatus.MATCHED -> add("클라우드", str(R.string.log_matched))
            SpotStatus.TAKEN -> add("클라우드", str(R.string.log_taken))
            SpotStatus.WITHDRAWN -> {
                add("클라우드", str(R.string.log_withdrawn))
                if (accepted || asking) {
                    voice.stop(); listening = false; asking = false; accepted = false
                    add("Car B", str(R.string.log_bcancel))
                    speaker.say("자리가 취소됐어요. 다른 자리를 찾을게요.")
                }
            }
        }
        if (s.status == SpotStatus.MATCHED && s.confirmed) add("클라우드", str(R.string.log_confirmed))
    }

    // ---------------- Car B: search, offer, answer ----------------
    fun startSearch() {
        if (searching) return
        searching = true
        add("Car B", str(R.string.log_search))
        openReg?.remove()
        openReg = repo.listenOpenSpots(DemoPlaces.GROUP, CAR_B) { list ->
            if (accepted || asking || parked) return@listenOpenSpots
            val fresh = list.filter { it.updatedAt >= runStart }
            val best = fresh.minByOrNull { metersFromB(it) } ?: return@listenOpenSpots
            offer(best)
        }
    }

    private var offerPending = false

    /**
     * The spot is posted as "opening soon" at once, but the offer comes a moment later (DESIGN.md section 8:
     * 2.8 s in the auto play, 1.2 s by hand), so the "opening soon" step can be seen and read first.
     */
    private fun offer(s: Spot) {
        if (offerPending) return
        offerPending = true
        val run = runStart
        scope.launch {
            delay(if (autoPlaying) 2800 else 1200)
            offerPending = false
            if (run != runStart || !searching || accepted || asking || parked) return@launch
            if (spot?.status == SpotStatus.WITHDRAWN) return@launch
            offerNow(s)
        }
    }

    private fun offerNow(s: Spot) {
        val m = metersFromB(s)
        offerMeters = m
        asking = true
        add("Car B", str(R.string.log_recv, m))
        val sentence = "${m}미터 앞에 자리가 곧 납니다. 갈까요?"
        add("Car B", str(R.string.log_say, m))
        speaker.say(sentence) {
            if (!asking) return@say
            if (autoPlaying) {
                scope.launch {
                    delay(1200)
                    if (asking) { add("Car B", str(R.string.log_yes_auto)); answerYes() }
                }
            } else {
                listenForAnswer()
            }
        }
    }

    fun listenForAnswer() {
        if (!asking) return
        if (!voice.available) {
            add("Car B", str(R.string.log_voice_unavailable))
            return
        }
        listening = true
        add("Car B", str(R.string.log_listening))
        voice.listen(
            onAnswer = { answer, heard ->
                listening = false
                when (answer) {
                    VoiceInput.Answer.YES -> { add("Car B", str(R.string.log_heard_yes, heard)); answerYes() }
                    VoiceInput.Answer.NO -> { add("Car B", str(R.string.log_heard_no, heard)); answerNo() }
                    VoiceInput.Answer.UNKNOWN -> add("Car B", str(R.string.log_heard_unknown))
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
                add("Car B", str(R.string.log_yes)); add("Car B", str(R.string.log_guide))
                navigator.navigateTo(s.lat, s.lng, "NextSpot 주차 자리")
                speaker.say("그 자리로 안내할게요.")
            } else {
                add("Car B", str(R.string.log_taken_by_other))
                speaker.say("다른 차가 먼저 가져갔어요.")
            }
        }
    }

    fun answerNo() {
        voice.stop(); listening = false
        asking = false
        add("Car B", str(R.string.log_no))
    }

    fun parkCarB() {
        val s = spot ?: return
        if (!accepted || parked) return
        parked = true
        repo.markTaken(s)
        add("Car B", str(R.string.log_park))
        speaker.say("자리를 넘겨드렸어요. 크레딧 1점이 적립됐어요.")
    }

    // ---------------- reset + auto play ----------------
    fun reset(onReady: () -> Unit = {}) {
        autoJob?.cancel(); autoPlaying = false
        voice.stop()
        detector.reset()
        spotReg?.remove(); openReg?.remove()
        spot = null; lastSpotStatus = null
        door = false; belt = false; nav = false; brake = false
        searching = false; asking = false; listening = false; accepted = false; parked = false
        offerMeters = null; offerPending = false
        log.clear()
        add("시스템", str(R.string.log_reset))
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
                delay(800); startSearch()
                delay(2500); onDoorOpenedAndClosed()
                delay(400); onSeatbeltBuckled()
                delay(800); onNavigationSet()
                waitUntil(15_000) { accepted }
                delay(3000)
                if (cancelCase) {
                    cancelDeparture()
                } else {
                    onParkingBrakeReleased()
                    waitUntil(8_000) { spot?.confirmed == true }
                    delay(4000); parkCarB()
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
        signals.stop(); navigator.stop(); carContext.stop()
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
