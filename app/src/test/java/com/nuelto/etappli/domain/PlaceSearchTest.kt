package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSearchTest {

    private fun stop(id: String, order: Int, location: LatLng?, state: StopState = StopState.PLANNED) =
        Stop(id = id, tripId = "t1", orderIndex = order, location = location, state = state)

    private val bern = LatLng(46.95, 7.45)
    private val thun = LatLng(46.76, 7.63)
    private val brig = LatLng(46.32, 7.99)

    private val stops = listOf(
        stop("a", 0, bern),
        stop("b", 1, thun),
        stop("c", 2, brig),
    )

    @Test
    fun `the map opens on the located stop right before the one being edited`() {
        assertEquals(thun, nearestLocated(stops, orderIndex = 2))
    }

    @Test
    fun `a stop is not its own fallback`() {
        assertEquals(bern, nearestLocated(stops, orderIndex = 1))
    }

    @Test
    fun `a new stop at the end falls back to the trip's last stop`() {
        assertEquals(brig, nearestLocated(stops, orderIndex = Int.MAX_VALUE))
    }

    @Test
    fun `editing the first stop falls back to the last located stop`() {
        assertEquals(brig, nearestLocated(stops, orderIndex = 0))
    }

    @Test
    fun `skipped stops and stops without a location are never the fallback`() {
        val messy = listOf(
            stop("a", 0, bern),
            stop("b", 1, thun, state = StopState.SKIPPED),
            stop("c", 2, null),
        )
        assertEquals(bern, nearestLocated(messy, orderIndex = Int.MAX_VALUE))
    }

    @Test
    fun `a trip without a single coordinate has no fallback`() {
        assertNull(nearestLocated(listOf(stop("a", 0, null)), orderIndex = 1))
        assertNull(nearestLocated(emptyList(), orderIndex = 1))
    }

    @Test
    fun `suggestions compare by value`() {
        assertEquals(PlaceSuggestion("A", "B", bern), PlaceSuggestion("A", "B", bern))
        assertNotEquals(PlaceSuggestion("A", "B", bern), PlaceSuggestion("A", "B", thun))
        assertNotEquals(PlaceSuggestion("A", "B", bern), PlaceSuggestion("A", "C", bern))
        assertNotEquals(PlaceSuggestion("A", "B", bern), PlaceSuggestion("Z", "B", bern))
    }

    @Test
    fun `what the user is looking for comes first, without repeats`() {
        val camp = PlaceSuggestion("Camping Grimselblick", "", id = "a")
        val pass = PlaceSuggestion("Grimsel Pass", "", id = "b")
        val lake = PlaceSuggestion("Grimselsee", "", id = "c")
        assertEquals(
            listOf(camp, pass, lake),
            mergePreferred(listOf(camp), listOf(pass, camp, lake), limit = 8),
        )
    }

    @Test
    fun `merging cuts to the limit and falls back to names without ids`() {
        val many = (1..10).map { PlaceSuggestion("P$it", "", id = "id$it") }
        assertEquals(5, mergePreferred(emptyList(), many, limit = 5).size)

        val a = PlaceSuggestion("Same", "one")
        val b = PlaceSuggestion("Same", "two")
        assertEquals(listOf(a), mergePreferred(listOf(a), listOf(b), limit = 8))
    }

    @Test
    fun `nothing preferred leaves the order alone`() {
        val hits = listOf(PlaceSuggestion("A", "", id = "a"), PlaceSuggestion("B", "", id = "b"))
        assertEquals(hits, mergePreferred(emptyList(), hits, limit = 8))
        assertTrue(mergePreferred(emptyList(), emptyList(), limit = 8).isEmpty())
    }
}
