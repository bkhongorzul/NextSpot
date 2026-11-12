package com.nextspot.app.car

import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * "pleos" variant: real signals from the Pleos Connect Emulator / car.
 *  - Door opened -> closed : Pleos Vehicle SDK, Door (driver door)
 *  - Navigation set        : Pleos NaviHelper, onRouteStarted
 *  - Leaving (confirm)     : parking brake released (Android Automotive PARKING_BRAKE_ON)
 *                            OR gear moved out of Park (Pleos Vehicle SDK, Gear)
 * The on-screen buttons keep working too, so the demo never gets stuck.
 */
object Platform {
    fun carSignals(context: Context): CarSignals = PleosCarSignals(context.applicationContext)
    fun navigator(context: Context): CarNavigator = PleosNavigator(context.applicationContext)
}

private const val TAG = "NextSpot"
private val main = Handler(Looper.getMainLooper())

private class PleosCarSignals(private val context: Context) : CarSignals {
    override val label = "Pleos SDK + buttons"

    private var listener: CarSignals.Listener? = null
    private val vehicle = Vehicle(context)
    private val navi = NaviHelper(context)
    private val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null

    private var doorWasOpen = false
    private var brakeWasOn: Boolean? = null
    private var lastGear: VehicleGear? = null

    // Door: open -> closed counts as "driver got in"
    private val doorListener = object : DoorPositionListener {
        override fun onPositionUpdated(position: Int?) {
            val open = (position ?: 0) > 0
            if (doorWasOpen && !open) main.post { listener?.onDoorOpenedAndClosed() }
            doorWasOpen = open
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "Door listener failed", e)
        }
    }

    // Gear: Park -> anything else counts as "leaving"
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            if (lastGear == VehicleGear.GEAR_PARK && gear != VehicleGear.GEAR_PARK) {
                main.post { listener?.onParkingBrakeReleased() }
            }
            lastGear = gear
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "Gear listener failed", e)
        }
    }

    // Parking brake: ON -> OFF counts as "leaving"
    private val brakeCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val on = value.value as? Boolean ?: return
            if (brakeWasOn == true && !on) main.post { listener?.onParkingBrakeReleased() }
            brakeWasOn = on
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "Parking brake property error: $propertyId")
        }
    }

    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            main.post { listener?.onNavigationSet() }
        }
    }

    override fun start(listener: CarSignals.Listener) {
        this.listener = listener

        try {
            vehicle.initialize()
            vehicle.getDoor().registerDoorPosition(driverDoor, doorListener)
            vehicle.getGear().registerGearSelection(gearListener)
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle SDK not available, use the on-screen buttons", e)
        }

        try {
            val c = Car.createCar(context)
            car = c
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            propertyManager = pm
            pm.registerCallback(brakeCallback, VehiclePropertyIds.PARKING_BRAKE_ON, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        } catch (e: Exception) {
            Log.w(TAG, "Parking brake property not available, use the on-screen button", e)
        }

        try {
            navi.initialize()
            navi.addListener(naviListener)
        } catch (e: Exception) {
            Log.w(TAG, "NaviHelper not available", e)
        }
    }

    override fun stop() {
        try {
            vehicle.getDoor().unregisterDoorPosition(driverDoor, doorListener)
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.release()
        } catch (_: Exception) {}
        try { propertyManager?.unregisterCallback(brakeCallback) } catch (_: Exception) {}
        try { car?.disconnect() } catch (_: Exception) {}
        try { navi.removeListener(naviListener); navi.release() } catch (_: Exception) {}
        listener = null
    }
}

private class PleosNavigator(context: Context) : CarNavigator {
    private val navi = NaviHelper(context)

    override fun start() {
        try { navi.initialize() } catch (e: Exception) { Log.w(TAG, "NaviHelper init failed", e) }
    }

    /** Sets the spot as the destination in the car's built-in navigation. */
    override fun navigateTo(lat: Double, lng: Double, name: String) {
        try {
            navi.requestRoute(
                RouteInfo(
                    latitude = lat,
                    longitude = lng,
                    poiId = "nextspot",
                    poiName = name,
                    poiSubId = "0",
                    address = name,
                    routeOption = RouteOption.RECOMMENDED,
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "requestRoute failed", e)
        }
    }

    override fun stop() {
        try { navi.release() } catch (_: Exception) {}
    }
}
