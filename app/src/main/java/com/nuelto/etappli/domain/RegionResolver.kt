package com.nuelto.etappli.domain

import com.nuelto.etappli.data.TripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopRegion
import kotlinx.coroutines.flow.first

/**
 * Looks up which region each stop lies in, so the tour can be named after where it goes
 * (TripName; the repositories denormalize the result onto Trip.region with the totals).
 * Composed over the TripRepository interface like [RouteRefresher], and fail-soft the
 * same way: a lookup that finds nothing — offline, no geocoder — leaves the stop alone,
 * and the next time the trip is opened tries again.
 */
class RegionResolver(
    private val tripRepository: TripRepository,
    private val lookup: suspend (LatLng) -> StopRegion?,
) {

    /** Returns how many stops were written. */
    suspend fun resolve(tripId: String): Int {
        var resolved = 0
        due(tripRepository.stops(tripId).first()).forEach { stale ->
            val at = stale.location!! // due() keeps only located stops
            val region = lookup(at) ?: return@forEach
            // The lookup is a round trip, so re-read before writing: upsertStop replaces
            // the whole document, and the stop may have been edited meanwhile — or moved,
            // in which case this answer is for somewhere it no longer is.
            val current = tripRepository.stops(tripId).first().find { it.id == stale.id } ?: return@forEach
            if (current.location != at) return@forEach
            tripRepository.upsertStop(current.copy(region = region.copy(at = at)))
            resolved++
        }
        return resolved
    }

    companion object {
        /** Located stops whose region is missing, or was looked up for another spot. */
        fun due(stops: List<Stop>): List<Stop> =
            stops.filter { it.location != null && it.region?.at != it.location }
    }
}
