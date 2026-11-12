package com.nextspot.app.data

import com.google.firebase.firestore.DocumentSnapshot

/** Status flow: LIKELY -> CONFIRMED -> MATCHED -> TAKEN, or WITHDRAWN if no confirm. */
object SpotStatus {
    const val LIKELY = "likely"        // door + navigation: car will probably leave
    const val CONFIRMED = "confirmed"  // parking brake released: car is leaving
    const val MATCHED = "matched"      // a searching car accepted the spot
    const val TAKEN = "taken"          // the searching car parked in it
    const val WITHDRAWN = "withdrawn"  // no confirm in time, or cancelled

    val OPEN = setOf(LIKELY, CONFIRMED)
}

data class Spot(
    val id: String,
    val status: String,
    val lat: Double,
    val lng: Double,
    val group: String,
    val spotType: String,
    val leavingCar: String,
    val matchedCar: String?,
    /** True once Car A released the parking brake (it is really leaving). */
    val confirmed: Boolean,
    val updatedAt: Long,
) {
    val isOpen: Boolean get() = status in SpotStatus.OPEN && matchedCar == null

    companion object {
        fun from(doc: DocumentSnapshot): Spot? {
            if (!doc.exists()) return null
            return Spot(
                id = doc.id,
                status = doc.getString("status") ?: return null,
                lat = doc.getDouble("lat") ?: return null,
                lng = doc.getDouble("lng") ?: return null,
                group = doc.getString("group") ?: "",
                spotType = doc.getString("spotType") ?: "normal",
                leavingCar = doc.getString("leavingCar") ?: "",
                matchedCar = doc.getString("matchedCar"),
                confirmed = doc.getBoolean("confirmed") ?: false,
                updatedAt = doc.getLong("updatedAt") ?: 0L,
            )
        }
    }
}
