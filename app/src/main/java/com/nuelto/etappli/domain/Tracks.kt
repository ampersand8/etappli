package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopState

/**
 * The way actually driven, as the fixes the phone took along it ([Stop.track]): which
 * fixes are worth keeping, where a drive is really headed, how one drive's fixes read
 * back together, and whose they are once the plan changes under them. [RouteTracker]
 * records them; [MapOverlay] draws them.
 */
object Tracks {

    /**
     * How often the phone takes a fix while a drive is tracked. HIGH_ACCURACY at this
     * cadence is deliberate: a road trace needs GPS, and two minutes keeps it duty-cycled.
     */
    const val FIX_INTERVAL_MS = 120_000L

    /** A fix closer than this to the last one recorded is the same place — parked, at the lights, at lunch. */
    const val MIN_MOVE_METERS = 30.0

    /** A fix less sure than this is a cell tower, not GPS: the service drops it (see CLAUDE.md, arriving). */
    const val MAX_ACCURACY_METERS = 250.0

    fun accepts(track: List<LatLng>, fix: LatLng): Boolean {
        val last = track.lastOrNull() ?: return true
        return GeoUtils.haversineKm(last, fix) * 1000 >= MIN_MOVE_METERS
    }

    /**
     * Where the vehicle is driven to for [stop]: its pin; where it is left for the ride up
     * when no road reaches the stop (ParkAndRide); null when there is no way at all —
     * nothing to route to or arrive at.
     */
    fun target(stops: List<Stop>, stop: Stop): LatLng? {
        val previous = RouteCache.routed(stops).takeWhile { it.id != stop.id }.lastOrNull() ?: return stop.location
        val leg = RouteCache.usable(previous, stop) ?: return stop.location
        if (!leg.hasRoad) return null
        return leg.rideAfter?.parked ?: stop.location
    }

    /**
     * The way actually driven to [to]: its fixes, behind those of every stop with no pin
     * since the previous routed one (the same drive, which the road skips). Empty below
     * two points: one fix is a place, not a way.
     */
    fun drive(stops: List<Stop>, to: Stop): List<LatLng> {
        val ordered = stops.sortedBy { it.orderIndex }
        val before = ordered.take(ordered.indexOfFirst { it.id == to.id }.coerceAtLeast(0))
        val points = before.takeLastWhile { it.state == StopState.SKIPPED || it.location == null }
            .flatMap { it.track } + to.track
        return if (points.size < 2) emptyList() else points
    }

    /**
     * The rest of the planned road from the last fix: joins [road] at its nearest vertex
     * and follows it to the end; straight to [end] with no road. Empty with no fix.
     */
    fun remainder(track: List<LatLng>, road: List<LatLng>, end: LatLng): List<LatLng> {
        val last = track.lastOrNull() ?: return emptyList()
        if (road.isEmpty()) return listOf(last, end)
        val nearest = road.withIndex().minBy { (_, vertex) -> GeoUtils.haversineKm(last, vertex) }.index
        return listOf(last) + road.drop(nearest)
    }

    /**
     * Fixes belong to the drive underway, whichever stop it arrives at now: a stop added
     * or dragged in front of the one being headed for takes them over, a skipped one hands
     * them on, a restored one takes them back. Run by both repositories after every stop
     * write, like DateCascade.settle. Returns the stops to rewrite, none when nothing strayed.
     */
    fun settle(stops: List<Stop>): List<Stop> {
        val heading = CurrentStop.next(stops) ?: return emptyList()
        val strays = stops.sortedBy { it.orderIndex }
            .filter { it.state != StopState.DONE && it.id != heading.id && it.track.isNotEmpty() }
        if (strays.isEmpty()) return emptyList()
        return listOf(heading.copy(track = heading.track + strays.flatMap { it.track })) +
            strays.map { it.copy(track = emptyList()) }
    }
}
