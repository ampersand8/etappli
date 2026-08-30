package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.data.model.Stop
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
        val expired = PlaceCache.expired(tripRepository.stops(tripId).first(), today)
        var refreshed = 0
        var cleared = 0
        expired.forEach { stop ->
            val place = stop.placeId?.let { refresh(it) }
            tripRepository.upsertStop(
                if (place == null) {
                    cleared++
                    PlaceCache.forget(stop)
                } else {
                    refreshed++
                    PlaceCache.refreshed(stop, place, today)
                },
            )
        }
        return Result(refreshed = refreshed, cleared = cleared)
    }

    data class Result(val refreshed: Int, val cleared: Int) {
        val touched: Boolean get() = refreshed > 0 || cleared > 0
    }
}

/** True when a stop is showing a place whose coordinate is due for renewal. */
fun Stop.needsPlaceRefresh(today: LocalDate): Boolean = PlaceCache.isExpired(this, today)
