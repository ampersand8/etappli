package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopLeg
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRefresherTest {

    private val today = LocalDate.of(2026, 9, 1)
    private val here = LatLng(46.1712, 8.7936)
    private val there = LatLng(46.9, 8.4)
    private val elsewhere = LatLng(47.5, 7.6)

    /** Google's own example string: three points, enough for the elevation path. */
    private val geometry = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    private val repository = InMemoryTripRepository(seed = false)
    private val windows = mutableListOf<List<LatLng>>()
    private val sampled = mutableListOf<List<LatLng>>()

    private fun refresher(
        heights: suspend (List<LatLng>) -> List<Double>? = { null },
        route: suspend (List<LatLng>) -> List<RoutedLeg>?,
    ) = RouteRefresher(
        repository,
        { points -> windows += points; route(points) },
        { points -> sampled += points; heights(points) },
    )

    private suspend fun addStop(
        id: String,
        location: LatLng? = here,
        index: Int = 0,
        leg: StopLeg? = null,
    ) = repository.upsertStop(
        Stop(id = id, tripId = "t1", name = id, location = location, orderIndex = index, leg = leg),
    )

    private fun leg(from: LatLng, to: LatLng, fetchedAt: LocalDate, distance: Int = 1_000) = StopLeg(
        from = from,
        to = to,
        polyline = geometry,
        distanceMeters = distance,
        durationSeconds = 600,
        fetchedAt = fetchedAt,
    )

    private suspend fun stop(id: String) = repository.stops("t1").first().single { it.id == id }

    @Test
    fun `a missing leg is fetched onto the stop it arrives at`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)

        val result = refresher { listOf(RoutedLeg(geometry, 42_000, 3_600)) }.refresh("t1", today)

        assertEquals(1, result.fetched)
        assertEquals(0, result.cleared)
        assertTrue(result.touched)
        assertEquals(listOf(listOf(here, there)), windows)
        assertNull(stop("a").leg)
        val drive = stop("b").leg!!
        assertEquals(here, drive.from)
        assertEquals(there, drive.to)
        assertEquals(geometry, drive.polyline)
        assertEquals(42_000, drive.distanceMeters)
        assertEquals(3_600, drive.durationSeconds)
        assertEquals(today, drive.fetchedAt)
        assertNull(drive.ascentMeters)
        assertNull(drive.descentMeters)
    }

    @Test
    fun `a trip whose legs all still describe it is never routed`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1, leg = leg(here, there, today.minusDays(29), distance = 7_000))

        val result = refresher { listOf(RoutedLeg(geometry, 1, 1)) }.refresh("t1", today)

        assertFalse(result.touched)
        assertEquals(0, result.fetched)
        assertEquals(0, result.cleared)
        assertTrue(windows.isEmpty())
        assertEquals(7_000, stop("b").leg!!.distanceMeters)
    }

    @Test
    fun `a leg whose endpoints moved is cleared even with no network`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1, leg = leg(elsewhere, there, today))

        val result = refresher { null }.refresh("t1", today)

        assertEquals(1, result.cleared)
        assertEquals(0, result.fetched)
        assertTrue(result.touched)
        assertNull(stop("b").leg)
        assertEquals(listOf(listOf(here, there)), windows)
    }

    @Test
    fun `a leg past the thirty-day window is cleared`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1, leg = leg(here, there, today.minusDays(30)))

        val result = refresher { null }.refresh("t1", today)

        assertEquals(1, result.cleared)
        assertEquals(0, result.fetched)
        assertNull(stop("b").leg)
    }

    @Test
    fun `an expired leg is cleared and refetched in the same pass, dated today`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1, leg = leg(here, there, LocalDate.now().minusDays(30)))

        val result = refresher { listOf(RoutedLeg(geometry, 9_000, 900)) }.refresh("t1")

        assertEquals(1, result.cleared)
        assertEquals(1, result.fetched)
        val drive = stop("b").leg!!
        assertEquals(9_000, drive.distanceMeters)
        assertEquals(LocalDate.now(), drive.fetchedAt)
    }

    @Test
    fun `a failed fetch leaves the legs that still hold`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1, leg = leg(here, there, today, distance = 7_000))
        addStop("c", elsewhere, index = 2)

        val result = refresher { null }.refresh("t1", today)

        assertEquals(0, result.fetched)
        assertEquals(0, result.cleared)
        assertFalse(result.touched)
        assertEquals(7_000, stop("b").leg!!.distanceMeters)
        assertNull(stop("c").leg)
    }

    @Test
    fun `a response with the wrong number of legs writes nothing`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)

        val result = refresher {
            listOf(RoutedLeg(geometry, 1, 1), RoutedLeg(geometry, 2, 2))
        }.refresh("t1", today)

        assertEquals(0, result.fetched)
        assertFalse(result.touched)
        assertNull(stop("b").leg)
    }

    @Test
    fun `a height profile lands on the leg, rounded`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)

        val result = refresher(heights = { listOf(100.0, 250.6, 200.2) }) {
            listOf(RoutedLeg(geometry, 42_000, 3_600))
        }.refresh("t1", today)

        assertEquals(1, result.fetched)
        assertEquals(listOf(Polyline.decode(geometry)), sampled)
        val drive = stop("b").leg!!
        assertEquals(151, drive.ascentMeters)
        assertEquals(50, drive.descentMeters)
    }

    @Test
    fun `heights that do not line up with the samples are discarded`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)

        val result = refresher(heights = { listOf(100.0, 250.0) }) {
            listOf(RoutedLeg(geometry, 42_000, 3_600))
        }.refresh("t1", today)

        assertEquals(1, result.fetched)
        val drive = stop("b").leg!!
        assertEquals(42_000, drive.distanceMeters)
        assertNull(drive.ascentMeters)
        assertNull(drive.descentMeters)
    }

    @Test
    fun `a leg with no geometry is written without asking for heights`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)

        val result = refresher(heights = { listOf(100.0, 250.0) }) {
            listOf(RoutedLeg("", 500, 60))
        }.refresh("t1", today)

        assertEquals(1, result.fetched)
        assertTrue(sampled.isEmpty())
        val drive = stop("b").leg!!
        assertEquals(500, drive.distanceMeters)
        assertNull(drive.ascentMeters)
    }

    @Test
    fun `a long trip is routed in overlapping windows and every leg is written`() = runTest {
        repeat(13) { addStop("s$it", LatLng(46.0 + it, 8.0), index = it) }

        val result = refresher { window ->
            window.zipWithNext().map { (_, to) -> RoutedLeg(geometry, to.latitude.toInt(), 60) }
        }.refresh("t1", today)

        assertEquals(12, result.fetched)
        assertEquals(listOf(12, 2), windows.map { it.size })
        assertNull(stop("s0").leg)
        (1..12).forEach { index ->
            val drive = stop("s$index").leg!!
            assertEquals(46 + index, drive.distanceMeters)
            assertEquals(LatLng(45.0 + index, 8.0), drive.from)
            assertEquals(LatLng(46.0 + index, 8.0), drive.to)
        }
    }

    @Test
    fun `a window that fails discards the whole chain`() = runTest {
        repeat(13) { addStop("s$it", LatLng(46.0 + it, 8.0), index = it) }

        val result = refresher { window ->
            if (window.size == 2) null else window.zipWithNext().map { RoutedLeg(geometry, 1, 1) }
        }.refresh("t1", today)

        assertEquals(0, result.fetched)
        assertEquals(listOf(12, 2), windows.map { it.size })
        assertTrue(repository.stops("t1").first().all { it.leg == null })
    }

    @Test
    fun `a trip with nothing to drive between is never routed`() = runTest {
        addStop("lonely", here, index = 0)

        val result = refresher { listOf(RoutedLeg(geometry, 1, 1)) }.refresh("t1", today)

        assertFalse(result.touched)
        assertTrue(windows.isEmpty())
        assertNull(stop("lonely").leg)
    }

    @Test
    fun `an edit made while the route was in flight is not reverted`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)
        val gate = CompletableDeferred<Unit>()
        val running = launch {
            RouteRefresher(repository, route = { gate.await(); listOf(RoutedLeg(geometry, 12_000, 600)) })
                .refresh("t1", today)
        }
        runCurrent()

        repository.upsertStop(stop("b").copy(name = "Gotthard", nights = 3))
        gate.complete(Unit)
        running.join()

        val arrived = stop("b")
        assertEquals("Gotthard", arrived.name)
        assertEquals(3, arrived.nights)
        assertEquals(12_000, arrived.leg!!.distanceMeters)
    }

    @Test
    fun `a stop deleted while the route was in flight is skipped`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)
        val gate = CompletableDeferred<Unit>()
        var result: RouteRefresher.Result? = null
        val running = launch {
            result = RouteRefresher(repository, route = { gate.await(); listOf(RoutedLeg(geometry, 1, 1)) })
                .refresh("t1", today)
        }
        runCurrent()

        repository.deleteStop("t1", "b")
        gate.complete(Unit)
        running.join()

        assertEquals(0, result!!.fetched)
        assertFalse(result!!.touched)
        assertNull(stop("a").leg)
    }
    @Test
    fun `a failed elevation call keeps the climb already measured for that drive`() = runTest {
        val measured = leg(here, there, LocalDate.now()).copy(ascentMeters = 900, descentMeters = 300)
        addStop("a", here, index = 0)
        addStop("b", there, index = 1, leg = measured)
        addStop("c", elsewhere, index = 2)

        // The new stop makes the whole chain re-route; the DEM service is down meanwhile.
        val result = refresher(heights = { null }) { points ->
            points.zipWithNext().map { RoutedLeg(geometry, 5_000, 1_200) }
        }.refresh("t1")

        assertEquals(2, result.fetched)
        val kept = stop("b").leg!!
        assertEquals(900, kept.ascentMeters)
        assertEquals(300, kept.descentMeters)
        // The rest of the leg is the fresh one, not the old one.
        assertEquals(5_000, kept.distanceMeters)
        // Nothing to keep for the drive that never had a climb.
        assertEquals(null, stop("c").leg!!.ascentMeters)
    }

    @Test
    fun `a climb is not carried over to a different drive`() = runTest {
        val elsewhereLeg = leg(here, elsewhere, LocalDate.now()).copy(ascentMeters = 900)
        addStop("a", here, index = 0)
        // The stored leg describes a drive from somewhere this stop no longer follows.
        addStop("b", there, index = 1, leg = elsewhereLeg)

        refresher(heights = { null }) { listOf(RoutedLeg(geometry, 5_000, 1_200)) }.refresh("t1")

        assertEquals(null, stop("b").leg!!.ascentMeters)
    }

    @Test
    fun `a fresh climb wins over the stored one`() = runTest {
        val measured = leg(here, there, LocalDate.now()).copy(ascentMeters = 900, descentMeters = 300)
        addStop("a", here, index = 0)
        addStop("b", there, index = 1, leg = measured)
        addStop("c", elsewhere, index = 2)

        refresher(heights = { listOf(100.0, 400.0, 250.0) }) { points ->
            points.zipWithNext().map { RoutedLeg(geometry, 5_000, 1_200) }
        }.refresh("t1")

        assertEquals(300, stop("b").leg!!.ascentMeters)
        assertEquals(150, stop("b").leg!!.descentMeters)
    }

}
