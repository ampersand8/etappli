package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopLeg
import com.nuelto.etappli.data.model.StopState
import java.time.LocalDate

/**
 * When a stored route still describes the drive it was fetched for, and when it has to
 * go. Two independent reasons to drop one:
 *
 * - **It no longer matches.** A leg records the two coordinates it was computed between,
 *   so a reorder, a moved pin or a skipped stop invalidates it by comparison — nothing
 *   has to remember to clear it.
 * - **It expired.** The Routes API terms allow keeping a routed coordinate for 30 days
 *   (SST §19.3), the same window as the Places rule in [PlaceCache]. A polyline is a
 *   sequence of coordinates, so it lives under the same clock.
 *
 * Expiry is [RouteRefresher]'s business, exactly as [PlaceCacheSweeper] owns §14.3: the
 * map and the estimator ask only whether a leg still matches, and the refresher is what
 * deletes one that has run out of days.
 */
object RouteCache {

    const val RETENTION_DAYS = 30L

    /** The stops a route runs through, in order. Skipped stops leave the route. */
    fun routed(stops: List<Stop>): List<Stop> = stops
        .sortedBy { it.orderIndex }
        .filterNot { it.state == StopState.SKIPPED }
        .filter { it.location != null }

    /**
     * Consecutive drives of the trip, each as the stop it leaves and the one it reaches.
     * Two stops at the same spot — a fresh plan that leaves home and comes straight back —
     * are not a drive: nothing to route, draw or cost.
     */
    fun drives(stops: List<Stop>): List<Pair<Stop, Stop>> =
        routed(stops).zipWithNext().filter { (from, to) -> from.location != to.location }

    /** The stored leg for the drive [previous] → [stop], if it still describes it. */
    fun usable(previous: Stop, stop: Stop): StopLeg? =
        stop.leg?.takeIf { it.from == previous.location && it.to == stop.location }

    /**
     * The drive arriving at each stop, keyed by stop id — only where the stored leg still
     * describes it, so the timeline never shows a distance left over from before an edit.
     */
    fun byStop(stops: List<Stop>): Map<String, StopLeg> = drives(stops)
        .mapNotNull { (from, to) -> usable(from, to)?.let { to.id to it } }
        .toMap()

    fun isExpired(leg: StopLeg, today: LocalDate): Boolean =
        !today.isBefore(leg.fetchedAt.plusDays(RETENTION_DAYS))

    /**
     * Stops holding a leg that must be dropped — expired, or describing a drive that is
     * no longer the one arriving there (including a stop that no longer has a drive at
     * all, because it was skipped, unpinned or dragged to the front).
     */
    fun stale(stops: List<Stop>, today: LocalDate): List<Stop> {
        val previous = drives(stops).associate { (from, to) -> to.id to from }
        return stops.filter { stop ->
            val leg = stop.leg ?: return@filter false
            val from = previous[stop.id]
            from == null || usable(from, stop) == null || isExpired(leg, today)
        }
    }

    /** True when any drive of the trip has no leg to draw or cost with. */
    fun needsFetch(stops: List<Stop>, today: LocalDate): Boolean =
        drives(stops).any { (from, to) ->
            val leg = usable(from, to)
            leg == null || isExpired(leg, today)
        }
}
