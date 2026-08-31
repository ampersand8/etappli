package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.TripRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * Enforces the Places 30-day retention on stored coordinates: refresh from the place id
 * where possible, delete where not. Composed over the TripRepository interface, so it
 * works the same against Firestore and the in-memory store.
 *
 * Deleting is not optional — §14.3 says "must delete" — so an expired stop with no
 * refresh loses its coordinate and stops appearing on the map until the next online run.
 */
class PlaceCacheSweeper(
    private val tripRepository: TripRepository,
    private val refresh: suspend (placeId: String) -> PlaceSuggestion?,
) {

    /** Returns how many stops were refreshed and how many were cleared. */
    suspend fun sweep(tripId: String, today: LocalDate = LocalDate.now()): Result {
        val due = PlaceCache.expired(tripRepository.stops(tripId).first(), today)
        var refreshed = 0
        var cleared = 0
        due.forEach { stale ->
            val place = stale.placeId?.let { refresh(it) }
            // A refresh is a network round trip, so the stop may have been edited while
            // it was in flight. Re-read before writing, or the edit is silently reverted
            // — upsertStop replaces the whole document.
            val current = tripRepository.stops(tripId).first().find { it.id == stale.id }
                ?: return@forEach
            tripRepository.upsertStop(
                if (place?.location == null) {
                    // Only count, and only write, if there is still something to clear.
                    if (!PlaceCache.isExpired(current, today)) return@forEach
                    cleared++
                    PlaceCache.forget(current)
                } else {
                    refreshed++
                    PlaceCache.refreshed(current, place, today)
                },
            )
        }
        return Result(refreshed = refreshed, cleared = cleared)
    }

    data class Result(val refreshed: Int, val cleared: Int) {
        val touched: Boolean get() = refreshed > 0 || cleared > 0
    }
}
