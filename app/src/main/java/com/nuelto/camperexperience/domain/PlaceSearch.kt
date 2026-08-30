package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState

/** A search hit: [name] fills the stop name, [label] is the address line under it. */
data class PlaceSuggestion(
    val name: String,
    val label: String,
    val location: LatLng,
)

/** Search feedback; IDLE also covers "results are showing" and "nothing typed yet". */
enum class PlaceSearchStatus { IDLE, SEARCHING, EMPTY, UNAVAILABLE }

/** Swappable geocoder seam — Photon today, another endpoint if it degrades. */
interface PlaceSearch {
    /** null = the lookup failed (offline, timeout, junk response); empty = no matches. */
    suspend fun search(query: String, near: LatLng?): List<PlaceSuggestion>?
}

/** Where to open the map for the stop at [orderIndex] that has no location of its own:
 *  the located stop before it, falling back to the trip's last located stop. */
fun nearestLocated(stops: List<Stop>, orderIndex: Int): LatLng? {
    val located = stops.filter { it.state != StopState.SKIPPED && it.location != null }
    val before = located.filter { it.orderIndex < orderIndex }
    return (before.maxByOrNull { it.orderIndex } ?: located.maxByOrNull { it.orderIndex })?.location
}
