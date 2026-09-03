package com.nuelto.etappli.domain

import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopElevation
import com.nuelto.etappli.data.model.StopLeg
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
    private val transitAsks = mutableListOf<Pair<LatLng, LatLng>>()

    private fun refresher(
        heights: suspend (List<LatLng>) -> List<Double>? = { null },
        transit: suspend (LatLng, LatLng) -> List<TransitStep>? = { _, _ -> emptyList() },
        route: suspend (List<LatLng>) -> List<RoutedLeg>?,
    ) = RouteRefresher(
        repository,
        { points -> windows += points; route(points) },
        { points -> sampled += points; heights(points) },
        { from, to -> transitAsks += from to to; transit(from, to) },
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
    fun `a response with the wrong number of legs writes nothing, and is not retried drive by drive`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)
        addStop("c", elsewhere, index = 2)

        val result = refresher { listOf(RoutedLeg(geometry, 1, 1)) }.refresh("t1", today)

        assertEquals(0, result.fetched)
        assertFalse(result.touched)
        assertEquals(listOf(listOf(here, there, elsewhere)), windows)
        assertTrue(repository.stops("t1").first().all { it.leg == null })
    }

    @Test
    fun `a stop with no road to it gets a leg saying so, and the rest is routed drive by drive`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)
        addStop("c", elsewhere, index = 2)

        val result = refresher { window ->
            // Nothing drives through `there`, so only the drive that avoids it has a road.
            if (window == listOf(there, elsewhere)) listOf(RoutedLeg(geometry, 9_000, 600)) else emptyList()
        }.refresh("t1", today)

        assertEquals(2, result.fetched)
        assertEquals(
            listOf(listOf(here, there, elsewhere), listOf(here, there), listOf(there, elsewhere)),
            windows,
        )
        val none = stop("b").leg!!
        assertFalse(none.hasRoad)
        assertEquals(here, none.from)
        assertEquals(there, none.to)
        assertEquals(today, none.fetchedAt)
        assertEquals(9_000, stop("c").leg!!.distanceMeters)
    }

    @Test
    fun `a drive with no road is not asked about again`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1, leg = StopLeg(from = here, to = there, fetchedAt = today))

        val result = refresher { error("asked again") }.refresh("t1", today)

        assertFalse(result.touched)
        assertTrue(windows.isEmpty())
        assertFalse(stop("b").leg!!.hasRoad)
    }

    @Test
    fun `a two-stop trip with no road is asked only once`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)

        val result = refresher { emptyList() }.refresh("t1", today)

        assertEquals(1, result.fetched)
        assertEquals(listOf(listOf(here, there)), windows)
        assertFalse(stop("b").leg!!.hasRoad)
    }

    @Test
    fun `a drive that fails while asked on its own discards the whole chain`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)
        addStop("c", elsewhere, index = 2)

        val result = refresher { window ->
            if (window == listOf(here, there)) null else emptyList()
        }.refresh("t1", today)

        assertEquals(0, result.fetched)
        assertEquals(listOf(listOf(here, there, elsewhere), listOf(here, there)), windows)
        assertTrue(repository.stops("t1").first().all { it.leg == null })
    }

    @Test
    fun `a drive answered with more than one leg on its own writes nothing`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)
        addStop("c", elsewhere, index = 2)

        val result = refresher { window ->
            if (window.size == 3) emptyList() else listOf(RoutedLeg(geometry, 1, 1), RoutedLeg(geometry, 2, 2))
        }.refresh("t1", today)

        assertEquals(0, result.fetched)
        assertTrue(repository.stops("t1").first().all { it.leg == null })
    }

    // --- park and ride --------------------------------------------------------

    private val valley = LatLng(46.3566, 8.046)
    private val town = LatLng(46.32, 7.99)

    /** Home to Riederalp by public transport: a train from town, then the cable car from the valley. */
    private val toRiederalp = listOf(
        TransitStep("", 100, 80),
        TransitStep(geometry, 100_000, 3_600, "LONG_DISTANCE_TRAIN", town),
        TransitStep("", 50, 40),
        TransitStep(geometry, 2_572, 720, "GONDOLA_LIFT", valley),
        TransitStep(geometry, 300, 240),
    )

    /** Drives as Google answers them: one leg for each listed window, no road for anything else. */
    private fun roads(vararg legs: Pair<List<LatLng>, Int>): suspend (List<LatLng>) -> List<RoutedLeg>? =
        { window -> legs.firstOrNull { it.first == window }?.let { listOf(RoutedLeg(geometry, it.second, 600)) } ?: emptyList() }

    @Test
    fun `a stop no road reaches is driven to as far as the road goes, then ridden to`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)
        addStop("c", elsewhere, index = 2)

        val result = refresher(
            transit = { _, _ -> toRiederalp },
            route = roads(listOf(here, valley) to 120_000, listOf(valley, elsewhere) to 136_000),
        ).refresh("t1", today)

        assertEquals(2, result.fetched)
        assertEquals(listOf(here to there), transitAsks)
        assertEquals(
            listOf(listOf(here, there, elsewhere), listOf(here, there), listOf(here, valley), listOf(valley, elsewhere)),
            windows,
        )
        val up = stop("b").leg!!
        assertTrue(up.hasRoad)
        assertEquals(here, up.from)
        assertEquals(there, up.to)
        assertEquals(120_000, up.distanceMeters)
        assertNull(up.rideBefore)
        val ride = up.rideAfter!!
        assertEquals(valley, ride.parked)
        assertEquals("cable car", ride.mode)
        assertEquals(960, ride.durationSeconds)
        assertEquals(2_872, ride.distanceMeters)
        // The next drive sets out from where the vehicle was left, after the ride back down.
        val down = stop("c").leg!!
        assertEquals(there, down.from)
        assertEquals(136_000, down.distanceMeters)
        assertEquals(ride, down.rideBefore)
        assertNull(down.rideAfter)
    }

    @Test
    fun `when no road reaches the last ride's boarding point either, the vehicle is left one ride earlier`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)

        refresher(transit = { _, _ -> toRiederalp }, route = roads(listOf(here, town) to 100_000)).refresh("t1", today)

        val ride = stop("b").leg!!.rideAfter!!
        assertEquals(town, ride.parked)
        assertEquals("train + cable car", ride.mode)
        assertEquals(listOf(listOf(here, there), listOf(here, valley), listOf(here, town)), windows)
    }

    @Test
    fun `a stop no ride reaches either is out of reach, after three tries at most`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)
        val rides = List(5) { TransitStep(geometry, 1_000, 600, "BUS", LatLng(47.0 + it, 9.0)) }

        val result = refresher(transit = { _, _ -> rides }) { emptyList() }.refresh("t1", today)

        assertEquals(1, result.fetched)
        assertFalse(stop("b").leg!!.hasRoad)
        assertNull(stop("b").leg!!.rideAfter)
        // The stop itself, then the three boarding points nearest it.
        assertEquals(listOf(there, LatLng(51.0, 9.0), LatLng(50.0, 9.0), LatLng(49.0, 9.0)), windows.map { it.last() })
    }

    @Test
    fun `a transit lookup that fails discards the whole chain`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)
        addStop("c", elsewhere, index = 2)

        val result = refresher(transit = { _, _ -> null }, route = roads(listOf(there, elsewhere) to 9_000))
            .refresh("t1", today)

        assertEquals(0, result.fetched)
        assertTrue(repository.stops("t1").first().all { it.leg == null })
    }

    @Test
    fun `a drive to a parking spot that fails discards the whole chain`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)

        val result = refresher(transit = { _, _ -> toRiederalp }) { window ->
            if (window == listOf(here, valley)) null else emptyList()
        }.refresh("t1", today)

        assertEquals(0, result.fetched)
        assertNull(stop("b").leg)
    }

    @Test
    fun `a window after a stop no road reaches is not asked for whole, and sets out from the vehicle`() = runTest {
        // Twelve stops fill the first window; the twelfth has no road, so the second would start there.
        repeat(13) { addStop("s$it", LatLng(46.0 + it, 8.0), index = it) }
        val offRoad = LatLng(57.0, 8.0)
        val parked = LatLng(56.9, 8.0)
        val steps = listOf(TransitStep(geometry, 1_000, 600, "FUNICULAR", parked))

        val result = refresher(transit = { _, _ -> steps }) { window ->
            if (offRoad in window) emptyList() else window.zipWithNext().map { RoutedLeg(geometry, 1, 1) }
        }.refresh("t1", today)

        assertEquals(12, result.fetched)
        assertEquals(listOf(LatLng(56.0, 8.0) to offRoad), transitAsks)
        assertFalse(listOf(offRoad, LatLng(58.0, 8.0)) in windows)
        assertTrue(listOf(parked, LatLng(58.0, 8.0)) in windows)
        assertEquals(parked, stop("s11").leg!!.rideAfter!!.parked)
        assertEquals(stop("s11").leg!!.rideAfter, stop("s12").leg!!.rideBefore)
    }

    @Test
    fun `a drive that has lost its road does not keep the climb it had`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1, leg = leg(here, there, today).copy(ascentMeters = 300, descentMeters = 100))
        // The new stop is what triggers the re-route; the answer says the old drive has no road now.
        addStop("c", elsewhere, index = 2)

        refresher { emptyList() }.refresh("t1", today)

        val none = stop("b").leg!!
        assertFalse(none.hasRoad)
        assertNull(none.ascentMeters)
        assertNull(none.descentMeters)
    }

    @Test
    fun `a height profile lands on the leg, rounded`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)

        val result = refresher(heights = { listOf(100.0, 250.6, 200.2) }) {
            listOf(RoutedLeg(geometry, 42_000, 3_600))
        }.refresh("t1", today)

        assertEquals(1, result.fetched)
        // Both stops' own heights, then the leg's sampled geometry.
        assertEquals(listOf(listOf(here, there), Polyline.decode(geometry)), sampled)
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
        // Only the two stops' own heights — nothing to sample along an empty polyline.
        assertEquals(listOf(listOf(here, there)), sampled)
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
    fun `a stop at the same spot as the one before is left out of the chain`() = runTest {
        addStop("out", here, index = 0)
        addStop("back", here, index = 1)
        // Leaving home and coming straight back is not a drive: nothing is asked for.
        assertFalse(refresher { listOf(RoutedLeg(geometry, 1, 1)) }.refresh("t1", today).touched)
        assertTrue(windows.isEmpty())

        addStop("twin", here, index = 1)
        addStop("far", there, index = 2)
        addStop("back", here, index = 3)
        val result = refresher { window -> window.zipWithNext().map { RoutedLeg(geometry, 42_000, 3_600) } }
            .refresh("t1", today)

        // One request over the distinct points; the leg lands on the stops that are driven to.
        assertEquals(listOf(listOf(here, there, here)), windows)
        assertEquals(2, result.fetched)
        assertNull(stop("twin").leg)
        assertEquals(here, stop("far").leg!!.from)
        assertEquals(there, stop("back").leg!!.from)
        assertEquals(here, stop("back").leg!!.to)
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

    @Test
    fun `every located stop gets its own height, in one call`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)
        addStop("c", location = null, index = 2)

        val result = refresher(heights = { listOf(1469.4, 492.6) }) {
            listOf(RoutedLeg(geometry, 1_000, 60))
        }.refresh("t1", today)

        assertEquals(2, result.elevated)
        assertEquals(1469, Elevation.ofStop(stop("a")))
        assertEquals(493, Elevation.ofStop(stop("b")))
        // No pin, no height to ask for.
        assertNull(stop("c").elevation)
        // One call for both stops, not one each.
        assertEquals(listOf(here, there), sampled.first())
    }

    @Test
    fun `a height already measured where the stop sits is not fetched again`() = runTest {
        addStop("a", here, index = 0)
        repository.upsertStop(stop("a").copy(elevation = StopElevation(here, 1200)))

        val result = refresher(heights = { listOf(999.0) }) { null }.refresh("t1", today)

        assertEquals(0, result.elevated)
        assertEquals(1200, Elevation.ofStop(stop("a")))
        assertTrue(sampled.isEmpty())
    }

    @Test
    fun `moving the pin re-measures the height`() = runTest {
        addStop("a", there, index = 0)
        repository.upsertStop(stop("a").copy(elevation = StopElevation(here, 1200)))

        refresher(heights = { listOf(640.0) }) { null }.refresh("t1", today)

        assertEquals(640, Elevation.ofStop(stop("a")))
    }

    @Test
    fun `an unreachable elevation service leaves the heights alone`() = runTest {
        addStop("a", here, index = 0)
        addStop("b", there, index = 1)

        val result = refresher(heights = { null }) {
            listOf(RoutedLeg(geometry, 1_000, 60))
        }.refresh("t1", today)

        assertEquals(0, result.elevated)
        assertNull(stop("a").elevation)
        // The route still landed: a missing height is not a failed refresh.
        assertEquals(1, result.fetched)
    }

}
