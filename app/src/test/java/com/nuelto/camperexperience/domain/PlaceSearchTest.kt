package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
    fun `the bias is the located stop right before the one being edited`() {
        assertEquals(thun, searchBias(stops, orderIndex = 2))
    }

    @Test
    fun `a stop does not bias itself`() {
        assertEquals(bern, searchBias(stops, orderIndex = 1))
    }

    @Test
    fun `a new stop at the end is biased by the trip's last stop`() {
        assertEquals(brig, searchBias(stops, orderIndex = Int.MAX_VALUE))
    }

    @Test
    fun `editing the first stop falls back to the last located stop`() {
        assertEquals(brig, searchBias(stops, orderIndex = 0))
    }

    @Test
    fun `skipped stops and stops without a location never bias`() {
        val messy = listOf(
            stop("a", 0, bern),
            stop("b", 1, thun, state = StopState.SKIPPED),
            stop("c", 2, null),
        )
        assertEquals(bern, searchBias(messy, orderIndex = Int.MAX_VALUE))
    }

    @Test
    fun `a trip without a single coordinate has no bias`() {
        assertNull(searchBias(listOf(stop("a", 0, null)), orderIndex = 1))
        assertNull(searchBias(emptyList(), orderIndex = 1))
    }

    @Test
    fun `suggestions compare by value`() {
        assertEquals(PlaceSuggestion("A", "B", bern), PlaceSuggestion("A", "B", bern))
        assertNotEquals(PlaceSuggestion("A", "B", bern), PlaceSuggestion("A", "B", thun))
        assertNotEquals(PlaceSuggestion("A", "B", bern), PlaceSuggestion("A", "C", bern))
        assertNotEquals(PlaceSuggestion("A", "B", bern), PlaceSuggestion("Z", "B", bern))
    }
}
