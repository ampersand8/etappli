package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng

/**
 * How far the stop you are heading for is from where you are standing, as Google routes it
 * for one GPS fix.
 *
 * Never stored, and deliberately not a [com.nuelto.etappli.data.model.StopLeg]:
 * that is the planned drive between two stops and stays true until the plan changes, while
 * this is true only until you move. It carries [from] so a later fix can tell whether it
 * still describes the situation.
 */
data class DriveFromHere(
    val from: LatLng,
    val to: LatLng,
    val distanceMeters: Int,
    val durationSeconds: Int,
)

/** When to ask Google again for the drive from here, and when not to bother at all. */
object LiveDrive {

    /**
     * Refetch once the fix has moved this far. Below it the answer barely changes, and
     * every fetch is a billed route — a phone sitting on the dashboard must not spend the
     * monthly allowance on its own GPS jitter.
     */
    const val REFRESH_AFTER_METERS = 2_000.0

    /** Inside this you are there, and "0 km · 0 min" is noise rather than information. */
    const val ARRIVED_WITHIN_METERS = 150.0

    fun arrived(from: LatLng, to: LatLng): Boolean =
        GeoUtils.haversineKm(from, to) * 1000 < ARRIVED_WITHIN_METERS

    /** True when nothing usable is held for this fix and this destination. */
    fun needsFetch(current: DriveFromHere?, from: LatLng, to: LatLng): Boolean = when {
        current == null -> true
        // A different destination is a different question, however little you moved.
        current.to != to -> true
        else -> GeoUtils.haversineKm(current.from, from) * 1000 >= REFRESH_AFTER_METERS
    }
}
