package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCacheTest {

    private val today = LocalDate.of(2026, 9, 1)
    private val here = LatLng(46.1712, 8.7936)

    private fun stop(cachedAt: LocalDate?, placeId: String? = "ChIJ1") =
        Stop(id = "s1", tripId = "t1", location = here, placeId = placeId, locationCachedAt = cachedAt)

    @Test
    fun `a coordinate that is not from Places never expires`() {
        assertFalse(PlaceCache.isExpired(stop(cachedAt = null, placeId = null), today))
        assertFalse(PlaceCache.isExpired(stop(cachedAt = null), today.plusYears(5)))
    }

    @Test
    fun `a Places coordinate lasts exactly thirty days`() {
        val cached = today.minusDays(29)
        assertFalse(PlaceCache.isExpired(stop(cached), today))
        // Day 30 is the first day it must be gone.
        assertTrue(PlaceCache.isExpired(stop(today.minusDays(30)), today))
        assertTrue(PlaceCache.isExpired(stop(today.minusDays(31)), today))
        assertEquals(30L, PlaceCache.RETENTION_DAYS)
    }

    @Test
    fun `expired picks out only the stops past their retention`() {
        val stops = listOf(
            stop(null).copy(id = "own"),
            stop(today.minusDays(1)).copy(id = "fresh"),
            stop(today.minusDays(40)).copy(id = "stale"),
        )
        assertEquals(listOf("stale"), PlaceCache.expired(stops, today).map { it.id })
        assertTrue(PlaceCache.expired(emptyList(), today).isEmpty())
    }

    @Test
    fun `forgetting drops the coordinate but keeps the place id`() {
        val forgotten = PlaceCache.forget(stop(today.minusDays(40)))
        assertNull(forgotten.location)
        assertNull(forgotten.locationCachedAt)
        assertEquals("ChIJ1", forgotten.placeId)
    }

    @Test
    fun `a refresh restamps the coordinate and the date`() {
        val there = LatLng(47.0, 8.0)
        val refreshed = PlaceCache.refreshed(
            stop(today.minusDays(40)),
            PlaceSuggestion("Camping Delta", "Locarno", there, id = "ChIJ2"),
            today,
        )
        assertEquals(there, refreshed.location)
        assertEquals(today, refreshed.locationCachedAt)
        assertEquals("ChIJ2", refreshed.placeId)
        assertFalse(PlaceCache.isExpired(refreshed, today))
    }

    @Test
    fun `a refresh without an id of its own keeps the one it had`() {
        val refreshed = PlaceCache.refreshed(
            stop(today.minusDays(40)),
            PlaceSuggestion("X", "", here),
            today,
        )
        assertEquals("ChIJ1", refreshed.placeId)
    }
}
