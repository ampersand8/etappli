package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopKind
import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapOverlayTest {

    private fun trip(status: TripStatus) = Trip(id = "t1", name = "T", status = status)

    private fun stop(
        id: String,
        order: Int,
        location: LatLng? = LatLng(46.0 + order, 7.0 + order),
        state: StopState = StopState.PLANNED,
        kind: StopKind = StopKind.CAMPSITE,
    ) = Stop(id = id, tripId = "t1", orderIndex = order, location = location, state = state, kind = kind)

    private fun data(status: TripStatus, stops: List<Stop>, currentStopId: String? = null) =
        listOf(TripMapData(trip(status), stops, currentStopId))

    // --- markers -------------------------------------------------------------

    @Test
    fun `a planned trip's markers are all blue, a done trip's all grey`() {
        val stops = listOf(stop("a", 0), stop("b", 1))
        assertEquals(
            listOf(MapAccent.PLANNED, MapAccent.PLANNED),
            MapOverlay.markers(data(TripStatus.PLANNED, stops)).map { it.accent },
        )
        assertEquals(
            listOf(MapAccent.DONE, MapAccent.DONE),
            MapOverlay.markers(data(TripStatus.DONE, stops)).map { it.accent },
        )
    }

    @Test
    fun `on an active trip the current stop is green, checked-in grey, ahead blue`() {
        val stops = listOf(
            stop("done", 0, state = StopState.DONE),
            stop("now", 1),
            stop("later", 2),
            stop("skipped", 3, state = StopState.SKIPPED),
        )
        val accents = MapOverlay.markers(data(TripStatus.ACTIVE, stops, currentStopId = "now"))
            .associate { it.stopId to it.accent }
        assertEquals(MapAccent.DONE, accents["done"])
        assertEquals(MapAccent.CURRENT, accents["now"])
        assertEquals(MapAccent.PLANNED, accents["later"])
        // A skipped stop keeps its marker, greyed out — it left the route, not the map.
        assertEquals(MapAccent.DONE, accents["skipped"])
    }

    @Test
    fun `visits render hollow and stops without a location get no marker`() {
        val stops = listOf(
            stop("visit", 0, kind = StopKind.VISIT),
            stop("camp", 1),
            stop("nowhere", 2, location = null),
        )
        val markers = MapOverlay.markers(data(TripStatus.PLANNED, stops))
        assertEquals(listOf("visit", "camp"), markers.map { it.stopId })
        assertEquals(listOf(true, false), markers.map { it.hollow })
        assertEquals("t1", markers.first().tripId)
        assertEquals(LatLng(46.0, 7.0), markers.first().at)
    }

    @Test
    fun `points are every marker across every trip`() {
        val two = listOf(
            TripMapData(trip(TripStatus.PLANNED), listOf(stop("a", 0))),
            TripMapData(Trip(id = "t2", status = TripStatus.DONE), listOf(stop("b", 1))),
        )
        assertEquals(listOf(LatLng(46.0, 7.0), LatLng(47.0, 8.0)), MapOverlay.points(two))
    }

    // --- routes --------------------------------------------------------------

    @Test
    fun `a done trip is one solid grey line, a planned trip one dashed blue`() {
        val stops = listOf(stop("a", 0), stop("b", 1))
        val done = MapOverlay.routes(data(TripStatus.DONE, stops)).single()
        assertEquals(MapAccent.DONE, done.accent)
        assertEquals(false, done.dashed)
        assertEquals(2, done.points.size)
        assertEquals("t1", done.tripId)

        val planned = MapOverlay.routes(data(TripStatus.PLANNED, stops)).single()
        assertEquals(MapAccent.PLANNED, planned.accent)
        assertEquals(true, planned.dashed)
    }

    @Test
    fun `an active trip splits into travelled, current leg and the road ahead`() {
        val stops = listOf(
            stop("d1", 0, state = StopState.DONE),
            stop("d2", 1, state = StopState.DONE),
            stop("u1", 2),
            stop("u2", 3),
        )
        val routes = MapOverlay.routes(data(TripStatus.ACTIVE, stops))
        assertEquals(
            listOf(MapAccent.DONE, MapAccent.CURRENT, MapAccent.PLANNED),
            routes.map { it.accent },
        )
        assertEquals(listOf(false, false, true), routes.map { it.dashed })
        // The current leg joins the last done stop to the next one.
        assertEquals(listOf(LatLng(47.0, 8.0), LatLng(48.0, 9.0)), routes[1].points)
    }

    @Test
    fun `an active trip with one done and one upcoming stop is just the current leg`() {
        val stops = listOf(stop("d", 0, state = StopState.DONE), stop("u", 1))
        assertEquals(listOf(MapAccent.CURRENT), MapOverlay.routes(data(TripStatus.ACTIVE, stops)).map { it.accent })
    }

    @Test
    fun `an active trip that has not started yet is only the road ahead`() {
        val stops = listOf(stop("u1", 0), stop("u2", 1))
        assertEquals(listOf(MapAccent.PLANNED), MapOverlay.routes(data(TripStatus.ACTIVE, stops)).map { it.accent })
    }

    @Test
    fun `skipped and unlocated stops leave the route, and one point draws no line`() {
        val stops = listOf(
            stop("a", 0),
            stop("skip", 1, state = StopState.SKIPPED),
            stop("nowhere", 2, location = null),
            stop("b", 3),
        )
        assertEquals(
            listOf(LatLng(46.0, 7.0), LatLng(49.0, 10.0)),
            MapOverlay.routes(data(TripStatus.PLANNED, stops)).single().points,
        )
        assertTrue(MapOverlay.routes(data(TripStatus.PLANNED, listOf(stop("a", 0)))).isEmpty())
        assertTrue(MapOverlay.routes(emptyList()).isEmpty())
    }

    @Test
    fun `the route follows order index, not list order`() {
        val stops = listOf(stop("b", 1), stop("a", 0))
        assertEquals(
            listOf(LatLng(46.0, 7.0), LatLng(47.0, 8.0)),
            MapOverlay.routes(data(TripStatus.DONE, stops)).single().points,
        )
    }

    // --- frame ---------------------------------------------------------------

    @Test
    fun `no points means no framing, one point means a point`() {
        assertEquals(MapFrame.None, MapOverlay.frame(emptyList()))
        assertEquals(MapFrame.Point(LatLng(46.0, 7.0)), MapOverlay.frame(listOf(LatLng(46.0, 7.0))))
    }

    @Test
    fun `repeated coordinates collapse instead of making a degenerate box`() {
        val same = LatLng(46.0, 7.0)
        assertEquals(MapFrame.Point(same), MapOverlay.frame(listOf(same, same, same)))
    }

    @Test
    fun `several points give the box that holds them all`() {
        assertEquals(
            MapFrame.Bounds(west = 7.0, south = 45.0, east = 9.0, north = 46.0),
            MapOverlay.frame(listOf(LatLng(46.0, 7.0), LatLng(45.0, 9.0), LatLng(45.5, 8.0))),
        )
    }
}
