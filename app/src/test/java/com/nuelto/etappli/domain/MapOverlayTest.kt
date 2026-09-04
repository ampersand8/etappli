package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopLeg
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.TransitRide
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // --- routes: road geometry -----------------------------------------------

    /** (46,7) (46.4,7.3) (46.7,7.6) (47,8) — the drive from stop a to stop b. */
    private val roadAB = "_kwwG_evi@_cmA_ry@_ry@_ry@_ry@_cmA"

    /** (47,8) (47.5,8.4) (48,9) — on from b to c, starting where [roadAB] ends. */
    private val roadBC = "_uz}G_oyo@_t`B_cmA_t`B_etB"

    /** (46,7) (46.5,7.5) (46.99,7.99) — a road that ends just short of b's pin. */
    private val roadShortOfB = "_kwwG_evi@_t`B_t`Bou~Aou~A"

    /** (46.5,7.5) — a single point, too little to draw a line with. */
    private val onePoint = "_`yzG_zwl@"

    private fun Stop.road(from: Stop, polyline: String) =
        copy(leg = StopLeg(from = from.location!!, to = location!!, polyline = polyline))

    @Test
    fun `a leg's road replaces the straight hop between the two pins`() {
        val a = stop("a", 0)
        val b = stop("b", 1).road(a, roadAB)
        val points = MapOverlay.routes(data(TripStatus.DONE, listOf(a, b))).single().points
        assertTrue(points.size > 2)
        assertEquals(Polyline.decode(roadAB), points)
    }

    @Test
    fun `a leg whose endpoints no longer match the stops falls back to the pins`() {
        val a = stop("a", 0)
        val moved = stop("b", 1).road(a, roadAB).copy(location = LatLng(47.5, 8.5))
        assertEquals(
            listOf(LatLng(46.0, 7.0), LatLng(47.5, 8.5)),
            MapOverlay.routes(data(TripStatus.DONE, listOf(a, moved))).single().points,
        )
    }

    @Test
    fun `a polyline with fewer than two points falls back to the pins`() {
        val a = stop("a", 0)
        assertEquals(1, Polyline.decode(onePoint).size)
        listOf("", onePoint).forEach { polyline ->
            val b = stop("b", 1).road(a, polyline)
            assertEquals(
                listOf(LatLng(46.0, 7.0), LatLng(47.0, 8.0)),
                MapOverlay.routes(data(TripStatus.DONE, listOf(a, b))).single().points,
            )
        }
    }

    @Test
    fun `a ride to a stop no road reaches is a dotted line of its own, from where the vehicle is left`() {
        val a = stop("a", 0)
        val parked = LatLng(46.5, 7.5)
        val pin = LatLng(47.0, 8.0)
        val up = TransitRide(parked = parked, polyline = Polyline.encode(listOf(parked, pin)))
        // The road runs a → parked; the ride carries on to b's pin. A ride with no line to draw is left out.
        val b = stop("b", 1).copy(
            leg = StopLeg(
                from = a.location!!, to = pin, distanceMeters = 1,
                polyline = Polyline.encode(listOf(a.location!!, parked)),
                rideBefore = TransitRide(polyline = ""), rideAfter = up,
            ),
        )
        val routes = MapOverlay.routes(data(TripStatus.PLANNED, listOf(a, b)))
        assertEquals(listOf(false, true), routes.map { it.ride })
        assertEquals(listOf(LatLng(46.0, 7.0), parked), routes[0].points)
        assertEquals(listOf(parked, pin), routes[1].points)
        assertEquals(MapAccent.PLANNED, routes[1].accent)
        assertFalse(routes[1].dashed)
    }

    @Test
    fun `on an active trip a ride takes the colour of its section`() {
        val a = stop("a", 0, state = StopState.DONE)
        val parked = LatLng(46.5, 7.5)
        val pin = LatLng(47.0, 8.0)
        val b = stop("b", 1).copy(
            leg = StopLeg(
                from = a.location!!, to = pin, distanceMeters = 1,
                polyline = Polyline.encode(listOf(a.location!!, parked)),
                rideAfter = TransitRide(parked = parked, polyline = Polyline.encode(listOf(parked, pin))),
            ),
        )
        val routes = MapOverlay.routes(data(TripStatus.ACTIVE, listOf(a, b), currentStopId = "b"))
        assertEquals(listOf(MapAccent.CURRENT, MapAccent.CURRENT), routes.map { it.accent })
        assertEquals(listOf(false, true), routes.map { it.ride })
    }

    @Test
    fun `two roads meeting at a stop share that vertex once`() {
        val a = stop("a", 0)
        val b = stop("b", 1).road(a, roadAB)
        val c = stop("c", 2).road(b, roadBC)
        val points = MapOverlay.routes(data(TripStatus.DONE, listOf(a, b, c))).single().points
        assertEquals(Polyline.decode(roadAB) + Polyline.decode(roadBC).drop(1), points)
        assertEquals(6, points.size)
        assertEquals(1, points.count { it == LatLng(47.0, 8.0) })
    }

    @Test
    fun `a road ending short of the pin keeps the pin the next hop starts from`() {
        val a = stop("a", 0)
        val b = stop("b", 1).road(a, roadShortOfB)
        val points = MapOverlay.routes(data(TripStatus.DONE, listOf(a, b, stop("c", 2)))).single().points
        assertEquals(
            Polyline.decode(roadShortOfB) + listOf(LatLng(47.0, 8.0), LatLng(48.0, 9.0)),
            points,
        )
        assertEquals(5, points.size)
    }

    @Test
    fun `an active trip splits into three roads where it has them`() {
        // (46,7) (46.5,7.5) (47,8)
        val travelled = "_kwwG_evi@_t`B_t`B_t`B_t`B"
        // (47,8) (47.5,8.6) (48,9)
        val current = "_uz}G_oyo@_t`B_etB_t`B_cmA"
        // (48,9) (48.5,9.5) (49,10)
        val ahead = "__~cH_y|u@_t`B_t`B_t`B_t`B"
        val d1 = stop("d1", 0, state = StopState.DONE)
        val d2 = stop("d2", 1, state = StopState.DONE).road(d1, travelled)
        val u1 = stop("u1", 2).road(d2, current)
        val u2 = stop("u2", 3).road(u1, ahead)
        val routes = MapOverlay.routes(data(TripStatus.ACTIVE, listOf(d1, d2, u1, u2)))
        assertEquals(
            listOf(MapAccent.DONE, MapAccent.CURRENT, MapAccent.PLANNED),
            routes.map { it.accent },
        )
        assertEquals(Polyline.decode(travelled), routes[0].points)
        assertEquals(Polyline.decode(current), routes[1].points)
        assertEquals(Polyline.decode(ahead), routes[2].points)
    }

    // --- routes: the way actually driven --------------------------------------

    private val fixes = listOf(LatLng(46.3, 7.2), LatLng(46.6, 7.7))

    @Test
    fun `a tracked drive is drawn from its fixes with the pins at both ends`() {
        val a = stop("a", 0, state = StopState.DONE)
        val b = stop("b", 1, state = StopState.DONE).road(a, roadAB).copy(track = fixes)
        val points = MapOverlay.routes(data(TripStatus.DONE, listOf(a, b))).single().points
        assertEquals(listOf(a.location!!) + fixes + b.location!!, points)
    }

    @Test
    fun `one fix keeps the road`() {
        val a = stop("a", 0)
        val b = stop("b", 1).road(a, roadAB).copy(track = fixes.take(1))
        assertEquals(Polyline.decode(roadAB), MapOverlay.routes(data(TripStatus.DONE, listOf(a, b))).single().points)
    }

    @Test
    fun `the drive to the first stop leads in from its first fix`() {
        val first = stop("a", 0).copy(track = fixes)
        val next = stop("b", 1)
        // Underway: the fixes solid green, then dashed green straight on to the pin.
        val routes = MapOverlay.routes(data(TripStatus.ACTIVE, listOf(first, next)))
        assertEquals(listOf(MapAccent.CURRENT, MapAccent.CURRENT, MapAccent.PLANNED), routes.map { it.accent })
        assertEquals(listOf(false, true, true), routes.map { it.dashed })
        assertEquals(fixes, routes[0].points)
        assertEquals(listOf(fixes.last(), first.location), routes[1].points)
        // Drawn even while it is the only stop on the map.
        assertEquals(routes.take(2), MapOverlay.routes(data(TripStatus.ACTIVE, listOf(first))))
        // Once checked in it is grey, to the pin.
        val arrived = first.copy(state = StopState.DONE)
        val checkedIn = MapOverlay.routes(data(TripStatus.ACTIVE, listOf(arrived, next)))
        assertEquals(listOf(MapAccent.DONE, MapAccent.CURRENT), checkedIn.map { it.accent })
        assertEquals(fixes + arrived.location!!, checkedIn[0].points)
        assertFalse(checkedIn[0].dashed)
        val finished = MapOverlay.routes(data(TripStatus.DONE, listOf(arrived, next.copy(state = StopState.DONE))))
        assertEquals(fixes + arrived.location!!, finished[0].points)
        assertEquals(MapAccent.DONE, finished[0].accent)
        // A lone fix leads nowhere.
        assertTrue(MapOverlay.routes(data(TripStatus.ACTIVE, listOf(first.copy(track = fixes.take(1))))).isEmpty())
    }

    @Test
    fun `an unlocated stop's fixes fold into the next drive`() {
        val a = stop("a", 0, state = StopState.DONE)
        val nowhere = stop("x", 1, location = null, state = StopState.DONE).copy(track = fixes.take(1))
        val b = stop("b", 2, state = StopState.DONE).copy(track = fixes.drop(1))
        val points = MapOverlay.routes(data(TripStatus.DONE, listOf(a, nowhere, b))).single().points
        assertEquals(listOf(a.location!!) + fixes + b.location!!, points)
    }

    @Test
    fun `the drive underway is the track so far and the rest of the road, dashed`() {
        val a = stop("a", 0, state = StopState.DONE)
        val track = listOf(LatLng(46.2, 7.1), LatLng(46.45, 7.35))
        val b = stop("b", 1).road(a, roadAB).copy(track = track)
        val routes = MapOverlay.routes(data(TripStatus.ACTIVE, listOf(a, b), currentStopId = "b"))
        assertEquals(listOf(MapAccent.CURRENT, MapAccent.CURRENT), routes.map { it.accent })
        assertEquals(listOf(false, true), routes.map { it.dashed })
        assertEquals(listOf(a.location!!) + track, routes[0].points)
        // Joins the road at (46.4, 7.3), its nearest vertex, and follows it to the end.
        assertEquals(listOf(track.last()) + Polyline.decode(roadAB).drop(1), routes[1].points)

        // With no road the rest is a straight line to the pin; a ride still rides along.
        val parked = LatLng(46.9, 7.9)
        val ride = TransitRide(parked = parked, polyline = Polyline.encode(listOf(parked, b.location!!)))
        val noRoad = b.copy(leg = StopLeg(from = a.location!!, to = b.location!!, distanceMeters = 1, rideAfter = ride))
        val straight = MapOverlay.routes(data(TripStatus.ACTIVE, listOf(a, noRoad), currentStopId = "b"))
        assertEquals(listOf(false, false, true), straight.map { it.ride })
        assertEquals(listOf(track.last(), b.location), straight[1].points)
        assertEquals(listOf(parked, b.location), straight[2].points)

        // Without a track the current leg is the road it always was.
        val untracked = MapOverlay.routes(data(TripStatus.ACTIVE, listOf(a, b.copy(track = emptyList())), currentStopId = "b"))
        assertEquals(Polyline.decode(roadAB), untracked.single().points)
        assertFalse(untracked.single().dashed)
    }
}
