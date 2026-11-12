package com.nextspot.app.car

import android.content.Context

/**
 * "sim" variant: no real car. The on-screen buttons stand in for the car signals,
 * and navigation is only shown on screen.
 */
object Platform {
    fun carSignals(context: Context): CarSignals = SimCarSignals()
    fun navigator(context: Context): CarNavigator = SimNavigator()
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
