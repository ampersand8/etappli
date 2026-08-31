package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopElevation
import com.nuelto.camperexperience.data.model.StopLeg
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * Keeps a trip's road-following legs in step with its stops: drops the ones that no
 * longer describe a drive or have run out of days, and fetches what is missing. Composed
 * over the TripRepository interface, so it works the same against Firestore and the
 * in-memory store — the same shape as [PlaceCacheSweeper], for the same reason.
 *
 * Fail-soft throughout, like the Places path: no key, no signal or a malformed response
 * simply leaves the legs alone, and the map and the estimate fall back to straight lines
 * scaled by the road-distance factor.
 *
 * When anything at all is missing the whole chain is re-fetched rather than the gaps
 * alone. One request covers a normal trip, and a partial fetch would have to reason about
 * which stored leg belongs to which pair after a reorder — which is exactly the bug class
 * [RouteCache] exists to make impossible.
 */
class RouteRefresher(
    private val tripRepository: TripRepository,
    private val route: suspend (points: List<LatLng>) -> List<RoutedLeg>?,
    // Null-returning by default: elevation is a second, optional service, and a leg
    // without a climb figure still draws and still costs.
    private val heights: suspend (points: List<LatLng>) -> List<Double>? = { null },
) {

    suspend fun refresh(tripId: String, today: LocalDate = LocalDate.now()): Result {
        val stops = tripRepository.stops(tripId).first()
        val cleared = clearStale(tripId, stops, today)
        val elevated = fillElevations(tripId, stops)
        if (!RouteCache.needsFetch(stops, today)) {
            return Result(fetched = 0, cleared = cleared, elevated = elevated)
        }

        val routed = RouteCache.routed(stops)
        // Safe: routed() keeps only stops that have a location.
        val legs = fetchAll(routed.map { it.location!! })
            ?: return Result(fetched = 0, cleared = cleared, elevated = elevated)

        var fetched = 0
        routed.zipWithNext().forEachIndexed { index, (from, to) ->
            val climb = climb(legs[index].polyline)
            val leg = StopLeg(
                // The coordinates the route was actually computed between. A stop edited
                // while the request was in flight must invalidate this leg on the next
                // pass, not quietly adopt it.
                from = from.location!!,
                to = to.location!!,
                polyline = legs[index].polyline,
                distanceMeters = legs[index].distanceMeters,
                durationSeconds = legs[index].durationSeconds,
                ascentMeters = climb?.ascentMeters?.roundToInt(),
                descentMeters = climb?.descentMeters?.roundToInt(),
                fetchedAt = today,
            )
            if (write(tripId, to.id) { it.copy(leg = leg.keepingClimbOf(it.leg)) }) fetched++
        }
        return Result(fetched = fetched, cleared = cleared, elevated = elevated)
    }

    /**
     * How high each stop sits, in one call for the whole trip. Independent of routing: a
     * lone stop has no drive but still has a height, and the DEM is free and open, so
     * there is no reason to make it wait for a billed route.
     */
    private suspend fun fillElevations(tripId: String, stops: List<Stop>): Int {
        val due = stops.filter { it.location != null && Elevation.ofStop(it) == null }
        if (due.isEmpty()) return 0
        val points = due.mapNotNull { it.location }.take(Elevation.MAX_SAMPLES)
        val measured = heights(points) ?: return 0
        if (measured.size != points.size) return 0
        var elevated = 0
        due.take(points.size).forEachIndexed { index, stop ->
            val elevation = StopElevation(points[index], measured[index].roundToInt())
            if (write(tripId, stop.id) { it.copy(elevation = elevation) }) elevated++
        }
        return elevated
    }

    /**
     * A failed elevation call must not erase a climb already measured for the same drive.
     * [RouteCache.needsFetch] only asks whether a leg exists, so a leg written with a null
     * climb is never revisited — the figure would be gone until the stop moved again.
     */
    private fun StopLeg.keepingClimbOf(stored: StopLeg?): StopLeg {
        if (ascentMeters != null) return this
        val measured = stored?.takeIf { it.from == from && it.to == to } ?: return this
        return copy(ascentMeters = measured.ascentMeters, descentMeters = measured.descentMeters)
    }

    /** §19.3 deletion plus invalidation: a leg that no longer applies is simply removed. */
    private suspend fun clearStale(tripId: String, stops: List<Stop>, today: LocalDate): Int {
        var cleared = 0
        RouteCache.stale(stops, today).forEach { stale ->
            val dropped = write(tripId, stale.id) { stop ->
                stop.copy(leg = null).takeIf { stop.leg != null }
            }
            if (dropped) cleared++
        }
        return cleared
    }

    /**
     * Re-reads the stop before writing it. Every fetch here is a network round trip, so
     * the stop may have been edited meanwhile — and `upsertStop` replaces the whole
     * document, so writing the stale copy would revert that edit.
     */
    private suspend fun write(tripId: String, stopId: String, edit: (Stop) -> Stop?): Boolean {
        val current = tripRepository.stops(tripId).first().find { it.id == stopId } ?: return false
        val updated = edit(current) ?: return false
        tripRepository.upsertStop(updated)
        return true
    }

    /** Null on the first window that fails, so a half-routed trip is never written. */
    private suspend fun fetchAll(points: List<LatLng>): List<RoutedLeg>? {
        val legs = mutableListOf<RoutedLeg>()
        GoogleRoutes.windows(points).forEach { window ->
            val fetched = route(window) ?: return null
            // One leg per pair, or the response cannot be lined up with the stops.
            if (fetched.size != window.size - 1) return null
            legs += fetched
        }
        return legs
    }

    private suspend fun climb(polyline: String): Climb? {
        val samples = Elevation.sample(Polyline.decode(polyline))
        if (samples.size < 2) return null
        val measured = heights(samples) ?: return null
        return if (measured.size != samples.size) null else Elevation.profile(samples, measured)
    }

    data class Result(val fetched: Int, val cleared: Int, val elevated: Int = 0) {
        val touched: Boolean get() = fetched > 0 || cleared > 0 || elevated > 0
    }
}
