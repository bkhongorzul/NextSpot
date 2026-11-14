package com.nextspot.app.car

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import android.content.Context
import com.nextspot.app.BuildConfig
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit

/**
 * "pleos" variant: everything comes from the Hyundai Pleos Connect SDK.
 *
 *  Car A (leaving)                                   Pleos SDK
 *   - driver door opened -> closed                   Vehicle SDK · Door
 *   - driver seatbelt buckled                        Vehicle SDK · Seat
 *   - destination set, route started                 NaviHelper SDK · onRouteStarted
 *   - parking brake released                         Vehicle SDK · Safety (parking brake telltale)
 *   - gear moved out of P                            Vehicle SDK · Gear
 *  Car B (searching)
 *   - near its destination -> start searching        NaviHelper SDK · onDrivingInfo (remaining distance)
 *   - car position                                   Fused Location SDK (or NaviHelper current location)
 *   - guide to the spot                              NaviHelper SDK · requestRoute
 *  Both
 *   - voice offer / thank-you                        TextToSpeech SDK (Gleo AI)
 *   - driver answers "네" / "아니요"                  SpeechToText SDK (Gleo AI)
 *
 * If any SDK is missing (e.g. not on a Pleos emulator), that part falls back to Android / the on-screen buttons,
 * so the demo never gets stuck.
 */
object Platform {
    const val NAME = "Pleos Connect"
    fun carSignals(context: Context): CarSignals = PleosCarSignals(context.applicationContext)
    fun navigator(context: Context): CarNavigator = PleosNavigator(context.applicationContext)
    fun carContext(context: Context): CarContext = PleosCarContext(context.applicationContext)
    fun speaker(context: Context): Speaker = PleosSpeaker(context.applicationContext)
    fun voiceInput(context: Context): VoiceInput = PleosVoiceInput(context.applicationContext)
}

private const val TAG = "NextSpot"
private val main = Handler(Looper.getMainLooper())
private const val NEAR_DESTINATION_M = 500

// ------------------------------------------------------------------ Car A signals
private class PleosCarSignals(private val context: Context) : CarSignals {
    override val label = "Pleos Vehicle + NaviHelper SDK"

    private var listener: CarSignals.Listener? = null
    private val vehicle = Vehicle(context)
    private val navi = NaviHelper(context)
    private val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)

    private var doorWasOpen = false
    private var beltWasOn: Boolean? = null
    private var brakeWasOn: Boolean? = null
    private var lastGear: VehicleGear? = null

    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(position: Int?) {
            val open = (position ?: 0) > 0
            if (doorWasOpen && !open) main.post { listener?.onDoorOpenedAndClosed() }
            doorWasOpen = open
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "Door listener failed", e) }
    }

    private val beltListener = object : SeatBeltBuckledStateListener {
        override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) {
            if (beltWasOn == false && buckled == true) main.post { listener?.onSeatbeltBuckled() }
            beltWasOn = buckled
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "Seatbelt listener failed", e) }
    }

    /** Parking brake comes from the dashboard telltale: ON -> OFF means it was released. */
    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) {
            val brake = telltales?.telltales?.firstOrNull {
                it.type == TelltaleType.ELECTRONIC_PARKING_BRAKE || it.type == TelltaleType.PARKING_BRAKE
            } ?: return
            val on = brake.state != TelltaleState.OFF
            if (brakeWasOn == true && !on) main.post { listener?.onParkingBrakeReleased() }
            if (brakeWasOn == false && on) main.post { listener?.onParked() }
            brakeWasOn = on
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "Telltale listener failed", e) }
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            if (lastGear == VehicleGear.GEAR_PARK && gear != VehicleGear.GEAR_PARK) {
                main.post { listener?.onParkingBrakeReleased() }
            }
            if (lastGear != null && lastGear != VehicleGear.GEAR_PARK && gear == VehicleGear.GEAR_PARK) {
                main.post { listener?.onParked() }
            }
            lastGear = gear
        }
        override fun onFailed(e: Exception) { Log.w(TAG, "Gear listener failed", e) }
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            main.post { listener?.onNavigationSet() }
        }
    }

    override fun start(listener: CarSignals.Listener) {
        this.listener = listener
        safe("Vehicle SDK") {
            vehicle.initialize()
            vehicle.getDoor().registerDoorPosition(driverDoor, doorListener)
            vehicle.getSeat().registerSeatBeltBuckledState(driverSeat, beltListener)
            vehicle.getSafety().registerTelltales(telltaleListener)
            vehicle.getGear().registerGearSelection(gearListener)
        }
        safe("NaviHelper") {
            navi.initialize(onConnected = { navi.addListener(naviListener) }, onFailed = { Log.w(TAG, "NaviHelper failed", it) })
        }
    }

    override fun stop() {
        safe("Vehicle release") {
            vehicle.getDoor().unregisterDoorPosition(driverDoor, doorListener)
            vehicle.getSeat().unregisterSeatBeltBuckledState(driverSeat, beltListener)
            vehicle.getSafety().unregisterTelltales(telltaleListener)
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.release()
        }
        safe("NaviHelper release") { navi.removeListener(naviListener); navi.release() }
        listener = null
    }
}

// ------------------------------------------------------------------ Car B: navigation to the spot
private class PleosNavigator(context: Context) : CarNavigator {
    private val navi = NaviHelper(context)

    override fun start() = safe("NaviHelper") { navi.initialize() }

    override fun navigateTo(lat: Double, lng: Double, name: String) = safe("requestRoute") {
        navi.requestRoute(
            RouteInfo(
                latitude = lat,
                longitude = lng,
                displayLatitude = null,
                displayLongitude = null,
                poiName = name,
                poiId = "nextspot",
                address = name,
                poiSubId = "0",
                routeOption = RouteOption.RECOMMENDED,
                poiProvider = "",
                isMobileRequest = false,
            )
        )
    }

    override fun stop() = safe("NaviHelper release") { navi.release() }
}

// ------------------------------------------------------------------ Car B: near destination + position
private class PleosCarContext(context: Context) : CarContext {
    override val label = "Pleos NaviHelper + Fused Location"
    private val navi = NaviHelper(context)
    private val fused = FusedLocation(context)
    private var nearFired = false
    private var onNear: ((Int) -> Unit)? = null
    private var onPos: ((Double, Double) -> Unit)? = null

    private val naviListener = object : NaviHelperEventListener {
        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            val remain = drivingInfo.destination?.distance ?: return
            if (remain in 1..NEAR_DESTINATION_M && !nearFired) {
                nearFired = true
                main.post { onNear?.invoke(remain) }
            }
            if (remain > NEAR_DESTINATION_M * 2) nearFired = false
        }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            main.post { onPos?.invoke(currentLocationInfo.latitude, currentLocationInfo.longitude) }
        }
    }

    private val fusedListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val l = location ?: return
            main.post { onPos?.invoke(l.latitude, l.longitude) }
        }
        override fun onError(e: Exception) { Log.w(TAG, "Fused location error", e) }
    }

    override fun start(onNearDestination: (Int) -> Unit, onPosition: (Double, Double) -> Unit) {
        onNear = onNearDestination
        onPos = onPosition
        safe("NaviHelper") {
            navi.initialize(onConnected = {
                navi.addListener(naviListener)
                navi.getCurrentLocationInfo()
            }, onFailed = { Log.w(TAG, "NaviHelper failed", it) })
        }
        safe("Fused Location") {
            fused.initialize()
            if (fused.isSupported()) fused.registerFusedLocationCallback(fusedListener)
        }
    }

    override fun stop() {
        safe("NaviHelper release") { navi.removeListener(naviListener); navi.release() }
        safe("Fused release") { fused.unregisterFusedLocationCallback(fusedListener); fused.release() }
        onNear = null; onPos = null
    }
}

// ------------------------------------------------------------------ Voice: Pleos TextToSpeech
private class PleosSpeaker(context: Context) : Speaker {
    private val fallback = AndroidSpeaker(context)
    private val tts = TextToSpeech(context, TtsMode.HYBRID)
    private var ready = false
    private var failed = false
    private var pendingDone: (() -> Unit)? = null

    private var server = false
    override val label: String get() = when {
        ready && !failed && server -> "Pleos TextToSpeech (server + on-device)"
        ready && !failed -> "Pleos TextToSpeech (on-device)"
        else -> "Android TTS (fallback)"
    }

    init {
        safe("Pleos TTS") {
            tts.initialize(object : TtsInit {
                override fun onSuccess() {
                    ready = true
                    tts.setLocale(Locale.KOREAN)
                    tts.addEventListener(object : EventListener {
                        override fun onStart() = Unit
                        override fun onReady() = Unit
                        override fun onDone() = finish()
                        override fun onStop() = finish()
                        override fun onError(errorType: TtsError) = finish()
                        override fun onUpdatedRms(rms: Double) = Unit
                    })
                    // Server voice needs the Pleos project key (secrets.properties). On-device works without it.
                    if (hasKey()) safe("Pleos TTS registerApp") {
                        tts.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET,
                            object : ai.pleos.playground.tts.listener.OnServerConnectionListener {
                                override fun onConnected() { server = true; Log.i(TAG, "Pleos TTS registered") }
                                override fun onFailed(errorType: TtsError) { Log.w(TAG, "Pleos TTS registerApp failed: $errorType") }
                            })
                    }
                }
                override fun onFailure(errorType: TtsError) {
                    failed = true
                    Log.w(TAG, "Pleos TTS init failed: $errorType -> Android TTS")
                }
            })
        }
    }

    private fun finish() {
        val cb = pendingDone ?: return
        pendingDone = null
        main.post(cb)
    }

    override fun say(text: String, onDone: (() -> Unit)?) {
        if (!ready || failed) { fallback.say(text, onDone); return }
        pendingDone = onDone
        try { tts.speak(text, true) } catch (e: Exception) {
            Log.w(TAG, "Pleos TTS speak failed", e); pendingDone = null; fallback.say(text, onDone)
        }
    }

    override fun setLanguage(code: String) {
        val loc = if (code == "en") Locale.ENGLISH else Locale.KOREAN
        safe("TTS locale") { if (ready) tts.setLocale(loc) }
        fallback.setLanguage(code)
    }

    override fun shutdown() {
        safe("TTS release") { tts.stop(); tts.release() }
        fallback.shutdown()
    }
}

// ------------------------------------------------------------------ Voice: Pleos SpeechToText
private class PleosVoiceInput(context: Context) : VoiceInput {
    private val fallback = AndroidVoiceInput(context)
    private val stt = SpeechToText(context, SttMode.HYBRID)
    private var ready = false
    private var onAnswer: ((VoiceInput.Answer, String) -> Unit)? = null

    private var server = false
    override val label: String get() = when {
        ready && server -> "Pleos SpeechToText (server + on-device)"
        ready -> "Pleos SpeechToText (on-device)"
        else -> "Android speech (fallback)"
    }
    override val available: Boolean get() = ready || fallback.available

    private val resultListener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            if (!isFinal) return
            val cb = onAnswer ?: return
            onAnswer = null
            main.post { cb(VoiceInput.classify(listOf(text)), text) }
        }
        override fun onError(errorType: SttError) {
            val cb = onAnswer ?: return
            onAnswer = null
            main.post { cb(VoiceInput.Answer.UNKNOWN, "") }
        }
        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    init {
        safe("Pleos STT") {
            stt.initialize(object : SttInit {
                override fun onSuccess() {
                    ready = true
                    stt.setLocale(Locale.KOREAN)
                    stt.addListener(resultListener)
                    if (hasKey()) safe("Pleos STT registerApp") {
                        stt.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET,
                            object : ai.pleos.playground.stt.listener.OnServerConnectionListener {
                                override fun onConnected() { server = true; Log.i(TAG, "Pleos STT registered") }
                                override fun onFailed(errorType: SttError) { Log.w(TAG, "Pleos STT registerApp failed: $errorType") }
                            })
                    }
                }
                override fun onFailure(errorType: SttError) {
                    Log.w(TAG, "Pleos STT init failed: $errorType -> Android speech")
                }
            })
        }
    }

    override fun listen(onAnswer: (VoiceInput.Answer, String) -> Unit, onUnavailable: () -> Unit) {
        if (!ready) { fallback.listen(onAnswer, onUnavailable); return }
        this.onAnswer = onAnswer
        try { stt.request() } catch (e: Exception) {
            Log.w(TAG, "Pleos STT request failed", e); this.onAnswer = null; fallback.listen(onAnswer, onUnavailable)
        }
    }

    override fun stop() {
        onAnswer = null
        safe("STT stop") { if (ready) stt.stop() }
        fallback.stop()
    }
}

private fun hasKey() = BuildConfig.PLEOS_CLIENT_ID.isNotBlank() && BuildConfig.PLEOS_CLIENT_SECRET.isNotBlank()

private inline fun safe(what: String, block: () -> Unit) {
    try { block() } catch (e: Throwable) { Log.w(TAG, "$what not available", e) }
}
