package com.nextspot.app.car

/**
 * Signals that come from the car. In the "sim" variant they come from buttons;
 * in the "pleos" variant they come from the Pleos SDK / Android Automotive.
 */
interface CarSignals {
    interface Listener {
        fun onDoorOpenedAndClosed()
        fun onNavigationSet()
        fun onParkingBrakeReleased()
    }

    fun start(listener: Listener)
    fun stop()

    /** Short label shown on screen, e.g. "Simulation" or "Pleos SDK". */
    val label: String
}

/** Sends the searching car to a spot using the car's built-in navigation. */
interface CarNavigator {
    fun start()
    fun navigateTo(lat: Double, lng: Double, name: String)
    fun stop()
}
