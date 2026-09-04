package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopLeg
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.TransitRide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TracksTest {

    private val a = LatLng(46.0, 7.0)
    private val b = LatLng(47.0, 8.0)
    private val c = LatLng(48.0, 9.0)
    private val t1 = listOf(LatLng(46.1, 7.1), LatLng(46.2, 7.2))
    private val t2 = listOf(LatLng(46.3, 7.3), LatLng(46.4, 7.4))

    /** [meters] north of [from], close enough that a degree of latitude is a straight ruler. */
    private fun north(from: LatLng, meters: Double) =
        LatLng(from.latitude + meters / 111_195.0, from.longitude)

    private fun stop(
        id: String,
        order: Int,
        location: LatLng? = b,
        state: StopState = StopState.PLANNED,
        track: List<LatLng> = emptyList(),
        leg: StopLeg? = null,
    ) = Stop(id = id, orderIndex = order, location = location, state = state, track = track, leg = leg)

    // --- accepts -------------------------------------------------------------

    @Test
    fun `the first fix is always kept`() {
        assertTrue(Tracks.accepts(emptyList(), a))
    }

    @Test
    fun `a fix short of the minimum move is the same place, one past it is not`() {
        assertFalse(Tracks.accepts(listOf(a, b), north(b, 29.0)))
        assertTrue(Tracks.accepts(listOf(a, b), north(b, 31.0)))
        // Measured from the last fix, not the first.
        assertTrue(Tracks.accepts(listOf(b, a), north(b, 10.0)))
        assertEquals(30.0, Tracks.MIN_MOVE_METERS, 1e-9)
        assertEquals(120_000L, Tracks.FIX_INTERVAL_MS)
    }

    // --- target --------------------------------------------------------------

    @Test
    fun `the target is the pin without a leg, for the first stop, and for a leg with a road`() {
        val first = stop("a", 0, a)
        val second = stop("b", 1, b)
        assertEquals(a, Tracks.target(listOf(first, second), first))
        assertEquals(b, Tracks.target(listOf(first, second), second))
        val routed = stop("b", 1, b, leg = StopLeg(from = a, to = b, distanceMeters = 5))
        assertEquals(b, Tracks.target(listOf(first, routed), routed))
        // A leg that no longer describes the drive says nothing about it.
        val stale = stop("b", 1, b, leg = StopLeg(from = c, to = b, distanceMeters = 0))
        assertEquals(b, Tracks.target(listOf(first, stale), stale))
    }

    @Test
    fun `with a ride up the target is where the vehicle is left`() {
        val parked = LatLng(46.5, 7.5)
        val first = stop("a", 0, a)
        val second = stop("b", 1, b, leg = StopLeg(from = a, to = b, distanceMeters = 1, rideAfter = TransitRide(parked = parked)))
        assertEquals(parked, Tracks.target(listOf(first, second), second))
    }

    @Test
    fun `a leg with no road has no target, judged against the previous routed stop`() {
        val first = stop("a", 0, a)
        val skipped = stop("s", 1, c, StopState.SKIPPED)
        val second = stop("b", 2, b, leg = StopLeg(from = a, to = b, distanceMeters = 0))
        assertNull(Tracks.target(listOf(first, skipped, second), second))
    }

    // --- drive ---------------------------------------------------------------

    @Test
    fun `a drive is the stop's own track`() {
        val to = stop("b", 1, b, track = t1)
        assertEquals(t1, Tracks.drive(listOf(stop("a", 0, a), to), to))
    }

    @Test
    fun `an unlocated done stop's fixes come in front, a skipped stop in between hands nothing on`() {
        val first = stop("a", 0, a, StopState.DONE)
        val nowhere = stop("x", 1, null, StopState.DONE, track = t1)
        val skipped = stop("s", 2, c, StopState.SKIPPED)
        val to = stop("b", 3, b, track = t2)
        assertEquals(t1 + t2, Tracks.drive(listOf(first, nowhere, skipped, to), to))
        assertEquals(t1 + t2, Tracks.drive(listOf(to, skipped, nowhere, first), to))
    }

    @Test
    fun `the walk back ends at the previous routed stop`() {
        val first = stop("a", 0, a, StopState.DONE, track = listOf(a, a))
        val nowhere = stop("x", 1, null, StopState.DONE, track = t1)
        val middle = stop("b", 2, b, StopState.DONE, track = t2)
        val to = stop("c", 3, c, track = listOf(c, c))
        val stops = listOf(first, nowhere, middle, to)
        assertEquals(listOf(c, c), Tracks.drive(stops, to))
        assertEquals(t1 + t2, Tracks.drive(stops, middle))
    }

    @Test
    fun `a stop not in the list gives its own track, and one point is no way`() {
        val alone = stop("z", 9, c, track = t1)
        assertEquals(t1, Tracks.drive(emptyList(), alone))
        assertTrue(Tracks.drive(emptyList(), alone.copy(track = t1.take(1))).isEmpty())
        assertTrue(Tracks.drive(listOf(stop("a", 0, a), alone), alone.copy(track = emptyList())).isEmpty())
        // Two single fixes on one drive are a way.
        val nowhere = stop("x", 0, null, track = t1.take(1))
        val to = stop("b", 1, b, track = t2.take(1))
        assertEquals(t1.take(1) + t2.take(1), Tracks.drive(listOf(nowhere, to), to))
    }

    // --- remainder -----------------------------------------------------------

    @Test
    fun `no fix means no remainder, no road means straight to the end`() {
        assertTrue(Tracks.remainder(emptyList(), listOf(a, b), b).isEmpty())
        assertEquals(listOf(t1.last(), b), Tracks.remainder(t1, emptyList(), b))
    }

    @Test
    fun `the remainder joins the road at its nearest vertex and follows it to the end`() {
        val road = listOf(a, LatLng(46.5, 7.5), LatLng(46.8, 7.8), b)
        val last = LatLng(46.6, 7.6)
        assertEquals(listOf(last, road[1], road[2], b), Tracks.remainder(listOf(a, last), road, b))
        // Nearest the last vertex: just the hop onto it.
        val nearly = LatLng(46.95, 7.95)
        assertEquals(listOf(nearly, b), Tracks.remainder(listOf(nearly), road, b))
    }

    // --- settle --------------------------------------------------------------

    @Test
    fun `nothing strayed, nothing to rewrite`() {
        val stops = listOf(stop("a", 0, a, StopState.DONE), stop("b", 1, b, track = t1), stop("c", 2, c))
        assertTrue(Tracks.settle(stops).isEmpty())
    }

    @Test
    fun `a stop inserted in front of the heading takes its fixes over`() {
        val inserted = stop("n", 1, c)
        val heading = stop("b", 2, b, track = t1)
        assertEquals(
            listOf(inserted.copy(track = t1), heading.copy(track = emptyList())),
            Tracks.settle(listOf(stop("a", 0, a, StopState.DONE), inserted, heading)),
        )
    }

    @Test
    fun `a skipped stop hands its fixes on`() {
        val skipped = stop("b", 1, b, StopState.SKIPPED, track = t1)
        val next = stop("c", 2, c)
        assertEquals(
            listOf(next.copy(track = t1), skipped.copy(track = emptyList())),
            Tracks.settle(listOf(stop("a", 0, a, StopState.DONE), skipped, next)),
        )
    }

    @Test
    fun `a restored stop takes its fixes back, its own first and the strays in order`() {
        val restored = stop("b", 1, b, track = t1)
        val c1 = stop("c", 2, c, track = t2)
        val d1 = stop("d", 3, c, track = listOf(c, c))
        assertEquals(
            listOf(restored.copy(track = t1 + t2 + listOf(c, c)), c1.copy(track = emptyList()), d1.copy(track = emptyList())),
            Tracks.settle(listOf(d1, stop("a", 0, a, StopState.DONE), c1, restored)),
        )
    }

    @Test
    fun `done stops keep their tracks and with no heading nothing moves`() {
        val done = listOf(stop("a", 0, a, StopState.DONE, track = t1), stop("b", 1, b, StopState.DONE, track = t2))
        assertTrue(Tracks.settle(done + stop("c", 2, c, track = listOf(c, c)) + stop("d", 3, c)).isEmpty())
        assertTrue(Tracks.settle(done + stop("s", 2, c, StopState.SKIPPED, track = t1)).isEmpty())
        assertTrue(Tracks.settle(emptyList()).isEmpty())
    }
}
