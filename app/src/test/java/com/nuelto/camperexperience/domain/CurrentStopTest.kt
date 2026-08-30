package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentStopTest {

    private val today = LocalDate.of(2026, 8, 30)

    private fun stop(
        id: String,
        order: Int,
        state: StopState = StopState.PLANNED,
        arrival: LocalDate = today,
        nights: Int = 1,
    ) = Stop(id = id, orderIndex = order, state = state, arrivalDate = arrival, nights = nights)

    @Test
    fun `a checked-in stop stays current until its stay is over`() {
        val stops = listOf(
            stop("here", 0, StopState.DONE, arrival = today.minusDays(1), nights = 2),
            stop("next", 1),
        )
        assertEquals("here", CurrentStop.of(stops, today))
        // Stay over on departure day -> the next stop takes over.
        assertEquals("next", CurrentStop.of(stops, today.plusDays(1)))
    }

    @Test
    fun `without check-ins the first planned stop is current`() {
        assertEquals("a", CurrentStop.of(listOf(stop("b", 1), stop("a", 0)), today))
    }

    @Test
    fun `skipped stops are never current`() {
        val stops = listOf(stop("skipped", 0, StopState.SKIPPED), stop("b", 1))
        assertEquals("b", CurrentStop.of(stops, today))
    }

    @Test
    fun `a stop restored behind the check-in line only becomes current when nothing lies ahead`() {
        val restored = stop("restored", 0, arrival = today.minusDays(3))
        val done = stop("done", 1, StopState.DONE, arrival = today.minusDays(2), nights = 1)
        assertEquals("ahead", CurrentStop.of(listOf(restored, done, stop("ahead", 2)), today))
        assertEquals("restored", CurrentStop.of(listOf(restored, done), today))
    }

    @Test
    fun `a zero-night check-in hands over immediately and no stops means no current`() {
        val visited = stop("visited", 0, StopState.DONE, nights = 0)
        assertEquals("next", CurrentStop.of(listOf(visited, stop("next", 1)), today))
        assertNull(CurrentStop.of(listOf(visited), today))
        assertNull(CurrentStop.of(emptyList(), today))
    }
}
