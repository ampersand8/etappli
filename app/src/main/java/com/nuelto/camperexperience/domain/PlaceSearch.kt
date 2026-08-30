package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopKind
import com.nuelto.camperexperience.data.model.StopState

/**
 * A search hit: [name] fills the stop name, [label] is the address line under it.
 * [id] is the provider's stable id — a Google Place ID, or blank for OSM results.
 */
data class PlaceSuggestion(
    val name: String,
    val label: String,
    // Null while a hit is only a prediction: some providers only name a place, and hand
    // over its coordinate when you actually choose it. See [PlaceSearch.resolve].
    val location: LatLng? = null,
    val id: String = "",
)

/** Search feedback; IDLE also covers "results are showing" and "nothing typed yet". */
enum class PlaceSearchStatus { IDLE, SEARCHING, EMPTY, UNAVAILABLE }

/** Swappable geocoder seam — Google Places today, another endpoint if it degrades. */
interface PlaceSearch {
    /**
     * [prefer] is the kind of stop being added, so searching "grimsel" while adding a
     * campsite puts campsites first. null = no preference. Returns null when the lookup
     * failed (offline, timeout, junk response); empty = no matches.
     */
    suspend fun search(query: String, near: LatLng?, prefer: StopKind?): List<PlaceSuggestion>?

    /**
     * Fills in the coordinate of a hit that came back without one. A provider whose
     * search already returns coordinates just hands the hit straight back.
     */
    suspend fun resolve(suggestion: PlaceSuggestion): PlaceSuggestion?
}

/**
 * Hits of the kind being looked for first, then everything else, without repeats. Two
 * hits are the same when their ids match, or their names when they have no id.
 */
fun mergePreferred(
    preferred: List<PlaceSuggestion>,
    rest: List<PlaceSuggestion>,
    limit: Int,
): List<PlaceSuggestion> = (preferred + rest)
    .distinctBy { it.id.ifBlank { it.name } }
    .take(limit)

/** Where to open the map for the stop at [orderIndex] that has no location of its own:
 *  the located stop before it, falling back to the trip's last located stop. */
fun nearestLocated(stops: List<Stop>, orderIndex: Int): LatLng? {
    val located = stops.filter { it.state != StopState.SKIPPED && it.location != null }
    val before = located.filter { it.orderIndex < orderIndex }
    return (before.maxByOrNull { it.orderIndex } ?: located.maxByOrNull { it.orderIndex })?.location
}
