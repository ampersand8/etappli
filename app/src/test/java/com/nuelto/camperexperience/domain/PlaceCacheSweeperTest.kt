package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import java.time.LocalDate
import kotlinx.coroutines.flow.first
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
    fun `a stop knows for itself when it is due`() {
        val stale = Stop(id = "s", tripId = "t1", location = here, locationCachedAt = today.minusDays(40))
        assertTrue(stale.needsPlaceRefresh(today))
        assertFalse(stale.copy(locationCachedAt = null).needsPlaceRefresh(today))
    }
}
