package com.nextspot.app.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

/**
 * All reads and writes to Firestore.
 * Collections:
 *   spots/{spotId}     one document per spot being handed over
 *   credits/{carId}    { count: Number }  credits earned by sharing spots
 */
class SpotRepository {
    private val db = FirebaseFirestore.getInstance()
    private val spots = db.collection("spots")
    private val credits = db.collection("credits")

    /** Car A: post a new spot as LIKELY (or CONFIRMED). Returns the new spot id. */
    fun postSpot(
        leavingCar: String,
        lat: Double,
        lng: Double,
        group: String,
        status: String,
        spotType: String = "normal",
    ): String {
        val doc = spots.document()
        doc.set(
            mapOf(
                "status" to status,
                "lat" to lat,
                "lng" to lng,
                "group" to group,
                "spotType" to spotType,
                "leavingCar" to leavingCar,
                "matchedCar" to null,
                "confirmed" to (status == SpotStatus.CONFIRMED),
                "updatedAt" to System.currentTimeMillis(),
            )
        )
        return doc.id
    }

    fun setStatus(spotId: String, status: String) {
        spots.document(spotId).update(
            mapOf("status" to status, "updatedAt" to System.currentTimeMillis())
        )
    }

    /**
     * Car A released the parking brake. If nobody accepted yet the spot becomes CONFIRMED;
     * if a car already accepted, it stays MATCHED and is only marked as confirmed.
     */
    fun confirm(spotId: String) {
        val ref = spots.document(spotId)
        db.runTransaction { tx ->
            val spot = Spot.from(tx.get(ref))
            val now = System.currentTimeMillis()
            when (spot?.status) {
                SpotStatus.LIKELY -> tx.update(
                    ref, mapOf("status" to SpotStatus.CONFIRMED, "confirmed" to true, "updatedAt" to now)
                )
                SpotStatus.MATCHED -> tx.update(ref, mapOf("confirmed" to true, "updatedAt" to now))
                else -> Unit
            }
            null
        }
    }

    /**
     * Car A did not really leave (no confirm in time, or the driver cancelled):
     * withdraw the spot, even if a car already accepted it. That car is told and searches again.
     */
    fun withdrawIfNotConfirmed(spotId: String) {
        val ref = spots.document(spotId)
        db.runTransaction { tx ->
            val spot = Spot.from(tx.get(ref))
            if (spot != null && !spot.confirmed &&
                spot.status in setOf(SpotStatus.LIKELY, SpotStatus.MATCHED)
            ) {
                tx.update(ref, mapOf("status" to SpotStatus.WITHDRAWN, "updatedAt" to System.currentTimeMillis()))
            }
            null
        }
    }

    /** Watch one spot (Car A watches its own spot; Car B watches the one it accepted). */
    fun listenSpot(spotId: String, onChange: (Spot?) -> Unit): ListenerRegistration =
        spots.document(spotId).addSnapshotListener { snap, _ ->
            onChange(snap?.let { Spot.from(it) })
        }

    /** Car B: watch open spots in the same group (filtering is done in the app). */
    fun listenOpenSpots(group: String, myCar: String, onChange: (List<Spot>) -> Unit): ListenerRegistration =
        spots.whereEqualTo("group", group).addSnapshotListener { snap, _ ->
            val list = snap?.documents
                ?.mapNotNull { Spot.from(it) }
                ?.filter { it.isOpen && it.leavingCar != myCar }
                .orEmpty()
            onChange(list)
        }

    /**
     * Car B: accept a spot. Runs in a transaction so two cars can never get the same spot:
     * the first car to accept wins, the others get `false`.
     */
    fun accept(spotId: String, myCar: String, onResult: (Boolean) -> Unit) {
        val ref = spots.document(spotId)
        db.runTransaction { tx ->
            val spot = Spot.from(tx.get(ref))
            if (spot != null && spot.isOpen) {
                tx.update(
                    ref,
                    mapOf(
                        "status" to SpotStatus.MATCHED,
                        "matchedCar" to myCar,
                        "updatedAt" to System.currentTimeMillis(),
                    )
                )
                true
            } else {
                false
            }
        }.addOnSuccessListener { ok -> onResult(ok == true) }
            .addOnFailureListener { onResult(false) }
    }

    /** Car B parked: mark the spot taken and give the leaving car +1 credit. */
    fun markTaken(spot: Spot) {
        setStatus(spot.id, SpotStatus.TAKEN)
        credits.document(spot.leavingCar)
            .set(mapOf("count" to FieldValue.increment(1)), SetOptions.merge())
    }

    /** Demo reset: close every open or in-progress spot left in this group from earlier runs. */
    fun clearGroup(group: String, onDone: () -> Unit = {}) {
        spots.whereEqualTo("group", group).get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                snap.documents.forEach { d ->
                    val st = d.getString("status")
                    if (st != SpotStatus.TAKEN && st != SpotStatus.WITHDRAWN) {
                        batch.update(d.reference, mapOf("status" to SpotStatus.WITHDRAWN))
                    }
                }
                batch.commit().addOnCompleteListener { onDone() }
            }
            .addOnFailureListener { onDone() }
    }

    fun listenCredits(carId: String, onChange: (Long) -> Unit): ListenerRegistration =
        credits.document(carId).addSnapshotListener { snap, _ ->
            onChange(snap?.getLong("count") ?: 0L)
        }
}
