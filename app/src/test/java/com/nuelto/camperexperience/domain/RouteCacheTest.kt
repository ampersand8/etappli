package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopLeg
import com.nuelto.camperexperience.data.model.StopState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteCacheTest {

    private val today = LocalDate.of(2026, 9, 1)
    private val a = LatLng(46.1712, 8.7936)
    private val b = LatLng(46.5610, 8.3390)
    private val c = LatLng(47.0502, 8.3093)

    private fun stop(
        id: String,
        order: Int,
        location: LatLng? = a,
        state: StopState = StopState.PLANNED,
        leg: StopLeg? = null,
    ) = Stop(id = id, tripId = "t1", name = id, location = location, orderIndex = order, state = state, leg = leg)

    private fun leg(from: LatLng, to: LatLng, meters: Int = 42_000, fetchedAt: LocalDate = today) =
        StopLeg(from = from, to = to, polyline = "abc", distanceMeters = meters, fetchedAt = fetchedAt)

    @Test
    fun `routed orders the stops and drops what the route cannot run through`() {
        val stops = listOf(
            stop("third", 2, c),
            stop("skipped", 1, b, state = StopState.SKIPPED),
            stop("unpinned", 1, location = null),
            stop("first", 0, a),
        )
        assertEquals(listOf("first", "third"), RouteCache.routed(stops).map { it.id })
        assertTrue(RouteCache.routed(emptyList()).isEmpty())
    }

    @Test
    fun `drives are the consecutive pairs of routed stops`() {
        val stops = listOf(stop("s1", 0, a), stop("s2", 1, b), stop("s3", 2, c))
        assertEquals(
            listOf("s1" to "s2", "s2" to "s3"),
            RouteCache.drives(stops).map { (from, to) -> from.id to to.id },
        )
    }

    @Test
    fun `fewer than two routable stops is no drive at all`() {
        assertTrue(RouteCache.drives(emptyList()).isEmpty())
        assertTrue(RouteCache.drives(listOf(stop("only", 0, a))).isEmpty())
        assertTrue(
            RouteCache.drives(listOf(stop("s1", 0, a), stop("s2", 1, b, state = StopState.SKIPPED))).isEmpty(),
        )
        assertTrue(RouteCache.drives(listOf(stop("s1", 0, a), stop("s2", 1, location = null))).isEmpty())
    }

    @Test
    fun `a leg is usable while it still describes the drive`() {
        val from = stop("s1", 0, a)
        val stored = leg(a, b)
        assertEquals(stored, RouteCache.usable(from, stop("s2", 1, b, leg = stored)))
    }

    @Test
    fun `a leg with no coordinates in common with the drive is not usable`() {
        val from = stop("s1", 0, a)
        assertNull(RouteCache.usable(from, stop("s2", 1, b, leg = null)))
        assertNull(RouteCache.usable(stop("s1", 0, c), stop("s2", 1, b, leg = leg(a, b))))
        assertNull(RouteCache.usable(from, stop("s2", 1, c, leg = leg(a, b))))
    }

    @Test
    fun `byStop keys each drive by the stop it arrives at`() {
        val stops = listOf(
            stop("s1", 0, a),
            stop("s2", 1, b, leg = leg(a, b, meters = 11_000)),
            stop("s3", 2, c, leg = leg(b, c, meters = 22_000)),
        )
        val byStop = RouteCache.byStop(stops)
        assertEquals(setOf("s2", "s3"), byStop.keys)
        assertEquals(11_000, byStop.getValue("s2").distanceMeters)
        assertEquals(22_000, byStop.getValue("s3").distanceMeters)
    }

    @Test
    fun `byStop leaves out a leg that no longer matches its drive`() {
        val stops = listOf(
            stop("s1", 0, a),
            stop("s2", 1, b, leg = leg(a, b)),
            stop("s3", 2, c, leg = leg(a, c)),
        )
        assertEquals(setOf("s2"), RouteCache.byStop(stops).keys)
    }

    @Test
    fun `a routed leg lasts exactly thirty days`() {
        assertFalse(RouteCache.isExpired(leg(a, b, fetchedAt = today.minusDays(29)), today))
        // Day 30 is the first day it must be gone.
        assertTrue(RouteCache.isExpired(leg(a, b, fetchedAt = today.minusDays(30)), today))
        assertTrue(RouteCache.isExpired(leg(a, b, fetchedAt = today.minusDays(31)), today))
        assertEquals(30L, RouteCache.RETENTION_DAYS)
    }

    @Test
    fun `stale picks out the legs that no longer describe an arrival here`() {
        val stops = listOf(
            stop("first", 0, a, leg = leg(a, b)),
            stop("second", 1, b, leg = leg(a, b)),
            stop("third", 2, c, leg = leg(a, c)),
            stop("fourth", 3, a, leg = leg(c, a, fetchedAt = today.minusDays(30))),
            stop("fifth", 4, b),
        )
        assertEquals(listOf("first", "third", "fourth"), RouteCache.stale(stops, today).map { it.id })
    }

    @Test
    fun `a leg on a stop that dropped out of the route goes with it`() {
        val stops = listOf(
            stop("s1", 0, a),
            stop("skipped", 1, b, state = StopState.SKIPPED, leg = leg(a, b)),
            stop("unpinned", 2, location = null, leg = leg(b, c)),
            stop("s4", 3, c, leg = leg(a, c)),
        )
        assertEquals(listOf("skipped", "unpinned"), RouteCache.stale(stops, today).map { it.id })
        assertTrue(RouteCache.stale(emptyList(), today).isEmpty())
    }

    @Test
    fun `nothing needs fetching while every drive has a fresh matching leg`() {
        val stops = listOf(
            stop("s1", 0, a),
            stop("s2", 1, b, leg = leg(a, b, fetchedAt = today.minusDays(29))),
            stop("s3", 2, c, leg = leg(b, c)),
        )
        assertFalse(RouteCache.needsFetch(stops, today))
        assertFalse(RouteCache.needsFetch(listOf(stop("only", 0, a)), today))
    }

    @Test
    fun `a missing or expired leg is a drive that must be fetched`() {
        val s1 = stop("s1", 0, a)
        val s3 = stop("s3", 2, c, leg = leg(b, c))
        assertTrue(RouteCache.needsFetch(listOf(s1, stop("s2", 1, b), s3), today))
        assertTrue(
            RouteCache.needsFetch(
                listOf(s1, stop("s2", 1, b, leg = leg(a, b, fetchedAt = today.minusDays(30))), s3),
                today,
            ),
        )
    }
}
