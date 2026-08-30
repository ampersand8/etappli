package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopKind
import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus

/** The app's colour language, provider-agnostic: blue = planned, green = current, grey = done. */
enum class MapAccent { PLANNED, CURRENT, DONE }

/** Visits render hollow — they are route points, not stays. */
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
 * lifecycle colour language. Pure — MapLibre and Google Maps both render the same specs,
 * so the rules live here where they can be tested rather than in either renderer.
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
                    hollow = stop.kind == StopKind.VISIT,
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
        val leg = { stops: List<Stop>, accent: MapAccent, dashed: Boolean ->
            MapRoute(entry.trip.id, stops.mapNotNull { it.location }, accent, dashed)
        }

        when (entry.trip.status) {
            TripStatus.DONE -> listOf(leg(located, MapAccent.DONE, false))
            TripStatus.PLANNED -> listOf(leg(located, MapAccent.PLANNED, true))
            TripStatus.ACTIVE -> {
                val done = located.filter { it.state == StopState.DONE }
                val upcoming = located.filter { it.state == StopState.PLANNED }
                buildList {
                    if (done.size >= 2) add(leg(done, MapAccent.DONE, false))
                    if (done.isNotEmpty() && upcoming.isNotEmpty()) {
                        add(leg(listOf(done.last(), upcoming.first()), MapAccent.CURRENT, false))
                    }
                    if (upcoming.size >= 2) add(leg(upcoming, MapAccent.PLANNED, true))
                }
            }
        }
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
