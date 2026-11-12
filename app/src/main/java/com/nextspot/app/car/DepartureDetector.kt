package com.nextspot.app.car

import android.os.Handler
import android.os.Looper
import com.nextspot.app.data.SpotRepository
import com.nextspot.app.data.SpotStatus

/**
 * Car A's departure logic (the core idea):
 *  1. Door opened/closed AND navigation set  -> post spot as LIKELY (heads-up)
 *  2. Parking brake released                 -> CONFIRMED
 *  3. No confirm within WITHDRAW_AFTER_MS     -> WITHDRAWN (the driver didn't actually leave)
 */
class DepartureDetector(
    private val repo: SpotRepository,
    private val carId: String,
    private val group: String,
    private val lat: Double,
    private val lng: Double,
    private val onSpotPosted: (spotId: String) -> Unit,
    private val onLog: (String) -> Unit,
) : CarSignals.Listener {

    companion object {
        /** Short for the demo. A real app would use about 3 minutes. */
        const val WITHDRAW_AFTER_MS = 45_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var doorSeen = false
    private var navSeen = false
    private var spotId: String? = null
    private var confirmed = false

    private val withdrawTask = Runnable {
        val id = spotId ?: return@Runnable
        if (!confirmed) {
            repo.withdrawIfNotConfirmed(id)
            onLog("No confirm in time: offer withdrawn")
            reset()
        }
    }

    override fun onDoorOpenedAndClosed() {
        doorSeen = true
        onLog("Door opened and closed")
        maybePostLikely()
    }

    override fun onNavigationSet() {
        navSeen = true
        onLog("Navigation set")
        maybePostLikely()
    }

    override fun onParkingBrakeReleased() {
        if (confirmed) return  // brake and gear can both fire; confirm only once
        onLog("Leaving: parking brake released / out of Park")
        val id = spotId
        if (id == null) {
            // Brake released without a heads-up: post directly as confirmed.
            val newId = repo.postSpot(carId, lat, lng, group, SpotStatus.CONFIRMED)
            spotId = newId
            onSpotPosted(newId)
        } else {
            repo.confirm(id)
        }
        confirmed = true
        handler.removeCallbacks(withdrawTask)
        onLog("Spot confirmed")
    }

    private fun maybePostLikely() {
        if (doorSeen && navSeen && spotId == null) {
            val id = repo.postSpot(carId, lat, lng, group, SpotStatus.LIKELY)
            spotId = id
            onSpotPosted(id)
            onLog("Spot posted: likely opening")
            handler.postDelayed(withdrawTask, WITHDRAW_AFTER_MS)
        }
    }

    /** Driver cancels ("I'm staying"). */
    fun cancel() {
        spotId?.let { repo.withdrawIfNotConfirmed(it) }
        onLog("Cancelled by driver")
        reset()
    }

    /** Start over for the next demo run. */
    fun reset() {
        handler.removeCallbacks(withdrawTask)
        doorSeen = false
        navSeen = false
        spotId = null
        confirmed = false
    }
}
