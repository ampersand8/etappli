package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.isStay
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus

/** The app's colour language, provider-agnostic: blue = planned, green = current, grey = done. */
enum class MapAccent { PLANNED, CURRENT, DONE }

/** Visits and home render hollow — they are route points, not stays. */
data class MapMarker(
    val tripId: String,
    val stopId: String,
    val at: LatLng,
    val accent: MapAccent,
    val hollow: Boolean,
)

data class MapRoute(
    val tripId: String,
    val points: List<LatLng>,
    val accent: MapAccent,
    val dashed: Boolean,
    // A ride to a stop no road reaches, drawn apart from the road: a cable car is not one.
    val ride: Boolean = false,
)

/** How the camera should sit over a set of points. */
sealed interface MapFrame {
    data object None : MapFrame
    data class Point(val at: LatLng) : MapFrame
    data class Bounds(
        val west: Double,
        val south: Double,
        val east: Double,
        val north: Double,
    ) : MapFrame
}

data class TripMapData(
    val trip: Trip,
    val stops: List<Stop>,
    // Tonight's stop on an active trip — rendered green, with a green current leg.
    val currentStopId: String? = null,
)

/**
 * Turns trips into the markers, route legs and camera frame a map should draw, in the
 * lifecycle colour language. Pure, so the rules live here where they can be tested
 * rather than inside a renderer that needs a GL surface to run.
 */
object MapOverlay {

    fun markers(data: List<TripMapData>): List<MapMarker> = data.flatMap { entry ->
        entry.stops.mapNotNull { stop ->
            stop.location?.let {
                MapMarker(
                    tripId = entry.trip.id,
                    stopId = stop.id,
                    at = it,
                    accent = entry.accent(stop),
                    hollow = !stop.kind.isStay,
                )
            }
        }
    }

    /** Every visible marker, including skipped stops — they leave the route, not the map. */
    fun points(data: List<TripMapData>): List<LatLng> = markers(data).map { it.at }

    /**
     * DONE: one grey line. PLANNED: one dashed blue line. ACTIVE: grey behind (done
     * stops), green current leg, dashed blue ahead.
     */
    fun routes(data: List<TripMapData>): List<MapRoute> = data.flatMap { entry ->
        val located = entry.stops
            .sortedBy { it.orderIndex }
            .filterNot { it.state == StopState.SKIPPED }
            .filter { it.location != null }
        if (located.size < 2) return@flatMap emptyList()
        // The road through the stops, then any ride off it, in the same colour.
        val section = { stops: List<Stop>, accent: MapAccent, dashed: Boolean ->
            listOf(MapRoute(entry.trip.id, path(stops), accent, dashed)) +
                rides(stops).map { MapRoute(entry.trip.id, it, accent, dashed = false, ride = true) }
        }

        when (entry.trip.status) {
            TripStatus.DONE -> section(located, MapAccent.DONE, false)
            TripStatus.PLANNED -> section(located, MapAccent.PLANNED, true)
            TripStatus.ACTIVE -> {
                val done = located.filter { it.state == StopState.DONE }
                val upcoming = located.filter { it.state == StopState.PLANNED }
                buildList {
                    if (done.size >= 2) addAll(section(done, MapAccent.DONE, false))
                    if (done.isNotEmpty() && upcoming.isNotEmpty()) {
                        addAll(section(listOf(done.last(), upcoming.first()), MapAccent.CURRENT, false))
                    }
                    if (upcoming.size >= 2) addAll(section(upcoming, MapAccent.PLANNED, true))
                }
            }
        }
    }

    /**
     * The rides either side of each drive whose stop no road reaches, as lines of their
     * own: the road [path] ends where the vehicle is left, and the ride carries on to the pin.
     */
    private fun rides(stops: List<Stop>): List<List<LatLng>> = stops.zipWithNext().flatMap { (from, to) ->
        val leg = RouteCache.usable(from, to) ?: return@flatMap emptyList()
        listOfNotNull(leg.rideBefore, leg.rideAfter)
            .map { Polyline.decode(it.polyline) }
            .filter { it.size >= 2 }
    }

    /**
     * The line through [stops]: the road Google routed wherever there is a leg for it,
     * and a straight hop between the two pins wherever there is not — no key, no signal,
     * or a stop moved since the route was fetched.
     */
    private fun path(stops: List<Stop>): List<LatLng> {
        val points = mutableListOf<LatLng>()
        stops.zipWithNext().forEach { (from, to) ->
            val segment = segment(from, to)
            // Consecutive segments share the stop between them; a routed one starts at
            // the road, not the pin, so the join is only dropped when it really repeats.
            points += if (points.lastOrNull() == segment.firstOrNull()) segment.drop(1) else segment
        }
        return points
    }

    private fun segment(from: Stop, to: Stop): List<LatLng> {
        val road = RouteCache.usable(from, to)?.let { Polyline.decode(it.polyline) }
        return if (road != null && road.size >= 2) road else listOfNotNull(from.location, to.location)
    }

    /** Identical coordinates collapse, so a degenerate bounding box never reaches the map. */
    fun frame(points: List<LatLng>): MapFrame {
        val distinct = points.distinct()
        return when {
            distinct.isEmpty() -> MapFrame.None
            distinct.size == 1 -> MapFrame.Point(distinct.first())
            else -> MapFrame.Bounds(
                west = distinct.minOf { it.longitude },
                south = distinct.minOf { it.latitude },
                east = distinct.maxOf { it.longitude },
                north = distinct.maxOf { it.latitude },
            )
        }
    }

    private fun TripMapData.accent(stop: Stop): MapAccent = when (trip.status) {
        TripStatus.PLANNED -> MapAccent.PLANNED
        TripStatus.DONE -> MapAccent.DONE
        TripStatus.ACTIVE -> when {
            stop.id == currentStopId -> MapAccent.CURRENT
            stop.state == StopState.PLANNED -> MapAccent.PLANNED
            else -> MapAccent.DONE
        }
    }
}
