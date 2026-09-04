package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopState

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
    // Filled in by [PlaceSearch.resolve] — what the place is actually like.
    val details: PlaceDetails? = null,
)

/**
 * What a chosen place looks like: enough to decide whether to stay there without
 * leaving the app. [photo] is a provider-side handle, fetched separately.
 */
data class PlaceDetails(
    val kind: String = "",
    val rating: Double? = null,
    val ratingCount: Int = 0,
    val summary: String = "",
    val review: String = "",
    val photo: String = "",
) {
    val isEmpty: Boolean
        get() = kind.isBlank() && rating == null && summary.isBlank() &&
            review.isBlank() && photo.isBlank()
}

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
     * A search as submitted rather than typed: everything matching [query], each with a
     * coordinate, so the hits can be pins. A kind of place ("camping") gives many around
     * [near]; a name gives the one. Null when the lookup failed; empty = no matches.
     */
    suspend fun find(query: String, near: LatLng?): List<PlaceSuggestion>?

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
