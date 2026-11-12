package com.nextspot.app.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Fixed demo positions near Pusan National University (change freely). */
object DemoPlaces {
    const val GROUP = "office-A"

    // Car A is parked here; this becomes the spot.
    const val CAR_A_LAT = 35.23310
    const val CAR_A_LNG = 129.08290

    // Car B is searching about 150 m away.
    const val CAR_B_LAT = 35.23440
    const val CAR_B_LNG = 129.08320
}

/** Distance in metres between two points. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}
