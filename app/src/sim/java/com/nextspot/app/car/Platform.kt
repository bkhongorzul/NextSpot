package com.nextspot.app.car

import android.content.Context

/**
 * "sim" variant: no real car (runs on any phone emulator).
 * On-screen buttons stand in for the car signals, and Android's own voice is used.
 * The "pleos" variant replaces every one of these with the Hyundai Pleos Connect SDK.
 */
object Platform {
    const val NAME = "Simulation"
    fun carSignals(context: Context): CarSignals = SimCarSignals()
    fun navigator(context: Context): CarNavigator = SimNavigator()
    fun carContext(context: Context): CarContext = SimCarContext()
    fun speaker(context: Context): Speaker = AndroidSpeaker(context)
    fun voiceInput(context: Context): VoiceInput = AndroidVoiceInput(context)
}

private class SimCarSignals : CarSignals {
    override val label = "Simulation (buttons)"
    override fun start(listener: CarSignals.Listener) = Unit
    override fun stop() = Unit
}

private class SimNavigator : CarNavigator {
    override fun start() = Unit
    override fun navigateTo(lat: Double, lng: Double, name: String) = Unit
    override fun stop() = Unit
}

private class SimCarContext : CarContext {
    override val label = "Simulation (button)"
    override fun start(onNearDestination: (Int) -> Unit, onPosition: (Double, Double) -> Unit) = Unit
    override fun stop() = Unit
}
