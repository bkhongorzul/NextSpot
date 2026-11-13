package com.nextspot.app.car

/**
 * Everything that comes from (or goes to) the car is behind these interfaces.
 *  - "sim" build  : buttons + Android's built-in voice (works on any phone emulator)
 *  - "pleos" build: Hyundai Pleos Connect SDK (Vehicle, NaviHelper, TextToSpeech, SpeechToText, Fused Location)
 */

/** Car A: signals that the driver is about to leave. */
interface CarSignals {
    interface Listener {
        fun onDoorOpenedAndClosed()
        fun onNavigationSet()
        fun onParkingBrakeReleased()
        /** Driver's seatbelt buckled (Pleos Vehicle SDK, Seat). Counts like "driver got in". */
        fun onSeatbeltBuckled() {}
    }

    fun start(listener: Listener)
    fun stop()

    /** Short label shown on screen, e.g. "Simulation" or "Pleos Vehicle SDK". */
    val label: String
}

/** Car B: sends the searching car to a spot using the car's built-in navigation. */
interface CarNavigator {
    fun start()
    fun navigateTo(lat: Double, lng: Double, name: String)
    fun stop()
}

/** Car B: tells the app when the car is close to its destination, so the search can start by itself. */
interface CarContext {
    val label: String
    fun start(onNearDestination: (remainingMeters: Int) -> Unit, onPosition: (lat: Double, lng: Double) -> Unit)
    fun stop()
}

/** Voice output (Car B's offer, Car A's thank-you). */
interface Speaker {
    val label: String
    fun say(text: String, onDone: (() -> Unit)? = null)
    fun setLanguage(code: String)
    fun shutdown()
}

/** Voice input: the driver's "네" / "아니요". */
interface VoiceInput {
    enum class Answer { YES, NO, UNKNOWN }

    val label: String
    val available: Boolean
    fun listen(onAnswer: (Answer, String) -> Unit, onUnavailable: () -> Unit)
    fun stop()

    companion object {
        private val YES = listOf("네", "예", "응", "어", "좋아", "가자", "갈게", "yes", "yeah", "ok", "okay", "예스", "오케이", "그래")
        private val NO = listOf("아니", "아뇨", "싫어", "괜찮아", "no", "nope", "노우", "됐어")

        fun classify(heard: List<String>): Answer {
            for (h in heard) {
                val t = h.lowercase().trim()
                if (NO.any { t.contains(it) }) return Answer.NO
                if (YES.any { t.contains(it) }) return Answer.YES
            }
            return Answer.UNKNOWN
        }
    }
}
