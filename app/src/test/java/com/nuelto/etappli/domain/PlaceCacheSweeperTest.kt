package com.nuelto.etappli.domain

import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
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

class PlaceCacheSweeperTest {

    private val today = LocalDate.of(2026, 9, 1)
    private val here = LatLng(46.1712, 8.7936)
    private val there = LatLng(47.0, 8.0)
    private val repository = InMemoryTripRepository(seed = false)
    private val asked = mutableListOf<String>()

    private fun sweeper(refresh: suspend (String) -> PlaceSuggestion? = { null }) =
        PlaceCacheSweeper(repository) { id -> asked += id; refresh(id) }

    private suspend fun addStop(
        id: String,
        cachedAt: LocalDate?,
        placeId: String? = "ChIJ$id",
        location: LatLng? = here,
    ) = repository.upsertStop(
        Stop(id = id, tripId = "t1", name = id, location = location, placeId = placeId, locationCachedAt = cachedAt),
    )

    private suspend fun stop(id: String) = repository.stops("t1").first().single { it.id == id }

    @Test
    fun `a live coordinate is left alone and nothing is fetched`() = runTest {
        addStop("fresh", today.minusDays(29))
        addStop("ours", null, placeId = null)
        val result = sweeper().sweep("t1", today)
        assertFalse(result.touched)
        assertEquals(0, result.refreshed)
        assertEquals(0, result.cleared)
        assertTrue(asked.isEmpty())
        assertEquals(here, stop("fresh").location)
        assertEquals(here, stop("ours").location)
    }

    @Test
    fun `an expired coordinate is renewed from its place id`() = runTest {
        addStop("stale", today.minusDays(40))
        val result = sweeper { PlaceSuggestion("Camping Delta", "Locarno", there, id = it) }
            .sweep("t1", today)
        assertEquals(1, result.refreshed)
        assertEquals(0, result.cleared)
        assertTrue(result.touched)
        assertEquals(listOf("ChIJstale"), asked)
        assertEquals(there, stop("stale").location)
        assertEquals(today, stop("stale").locationCachedAt)
    }

    @Test
    fun `an expired coordinate that cannot be renewed is deleted, id kept`() = runTest {
        addStop("stale", today.minusDays(40))
        val result = sweeper().sweep("t1", today)
        assertEquals(0, result.refreshed)
        assertEquals(1, result.cleared)
        assertNull(stop("stale").location)
        assertNull(stop("stale").locationCachedAt)
        assertEquals("ChIJstale", stop("stale").placeId)
    }

    @Test
    fun `an expired stop with no place id is deleted without asking`() = runTest {
        addStop("orphan", today.minusDays(40), placeId = null)
        assertEquals(1, sweeper().sweep("t1", today).cleared)
        assertTrue(asked.isEmpty())
        assertNull(stop("orphan").location)
    }

    @Test
    fun `a sweep handles renewals and deletions in the same trip`() = runTest {
        addStop("keep", today.minusDays(40))
        addStop("drop", today.minusDays(40), placeId = "gone")
        val result = sweeper { id -> if (id == "ChIJkeep") PlaceSuggestion("K", "", there, id) else null }
            .sweep("t1", today)
        assertEquals(1, result.refreshed)
        assertEquals(1, result.cleared)
        assertEquals(there, stop("keep").location)
        assertNull(stop("drop").location)
    }

    @Test
    fun `a trip with no stops sweeps to nothing`() = runTest {
        assertFalse(sweeper().sweep("t1", today).touched)
    }

    @Test
    fun `sweeping defaults to today`() = runTest {
        addStop("fresh", LocalDate.now())
        addStop("stale", LocalDate.now().minusDays(31))
        val result = sweeper().sweep("t1")
        assertEquals(1, result.cleared)
        assertEquals(here, stop("fresh").location)
        assertNull(stop("stale").location)
    }

    @Test
    fun `a coordinate lost to a failed refresh is retried on the next sweep`() = runTest {
        addStop("stale", today.minusDays(40))
        // Offline: the coordinate must go, per the terms.
        assertEquals(1, sweeper().sweep("t1", today).cleared)
        assertNull(stop("stale").location)

        // Back online a day later, the stop is picked up again and comes back.
        asked.clear()
        val result = sweeper { PlaceSuggestion("K", "", there, id = it) }
            .sweep("t1", today.plusDays(1))
        assertEquals(1, result.refreshed)
        assertEquals(listOf("ChIJstale"), asked)
        assertEquals(there, stop("stale").location)
        assertEquals(today.plusDays(1), stop("stale").locationCachedAt)
    }

    @Test
    fun `a stop emptied with no place id is not swept forever`() = runTest {
        addStop("own", null, placeId = null, location = null)
        assertFalse(sweeper().sweep("t1", today).touched)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun `an edit made while a refresh is in flight is not reverted`() = runTest {
        addStop("stale", today.minusDays(40))
        val gate = CompletableDeferred<Unit>()
        val sweeping = launch {
            PlaceCacheSweeper(repository) { gate.await(); PlaceSuggestion("K", "", there, id = it) }
                .sweep("t1", today)
        }
        runCurrent()

        // The user renames the stop and adds a night while the lookup is out.
        repository.upsertStop(stop("stale").copy(name = "Renamed", nights = 4))
        gate.complete(Unit)
        sweeping.join()

        val swept = stop("stale")
        assertEquals("Renamed", swept.name)
        assertEquals(4, swept.nights)
        // and the refresh still landed
        assertEquals(there, swept.location)
    }

    @Test
    fun `a stop the user relocated mid-sweep is left alone`() = runTest {
        addStop("stale", today.minusDays(40))
        val gate = CompletableDeferred<Unit>()
        val sweeping = launch {
            PlaceCacheSweeper(repository) { gate.await(); null }.sweep("t1", today)
        }
        runCurrent()

        // Picked somewhere fresh while the (doomed) lookup was out: nothing to clear.
        repository.upsertStop(stop("stale").copy(location = there, locationCachedAt = today))
        gate.complete(Unit)
        sweeping.join()

        assertEquals(there, stop("stale").location)
        assertEquals(today, stop("stale").locationCachedAt)
    }

    @Test
    fun `a stop deleted mid-sweep is simply skipped`() = runTest {
        addStop("stale", today.minusDays(40))
        val gate = CompletableDeferred<Unit>()
        val sweeping = launch {
            PlaceCacheSweeper(repository) { gate.await(); PlaceSuggestion("K", "", there, id = it) }
                .sweep("t1", today)
        }
        runCurrent()

        repository.deleteStop("t1", "stale")
        gate.complete(Unit)
        sweeping.join()
        assertTrue(repository.stops("t1").first().isEmpty())
    }

    @Test
    fun `a still-unreachable stop is left as it is, not cleared twice`() = runTest {
        addStop("stale", today.minusDays(40))
        assertEquals(1, sweeper().sweep("t1", today).cleared)

        // Offline again: it is already empty, so there is nothing to do.
        val second = sweeper().sweep("t1", today.plusDays(1))
        assertEquals(0, second.cleared)
        assertEquals(0, second.refreshed)
        assertNull(stop("stale").location)
        assertEquals("ChIJstale", stop("stale").placeId)
    }
}
