package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopState
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

    @Test
    fun `the home a tour set out from is behind you even before the start date`() {
        // Started the evening before: home is checked in, dated tomorrow, with no nights.
        val home = stop("home", 0, StopState.DONE, arrival = today.plusDays(1), nights = 0)
        val first = stop("first", 1, arrival = today.plusDays(1))
        assertEquals("first", CurrentStop.of(listOf(home, first), today))
    }

    @Test
    fun `next is the first planned stop past the check-in line, whatever the date says`() {
        val here = stop("here", 0, StopState.DONE, arrival = today.minusDays(1), nights = 2)
        assertEquals("next", CurrentStop.next(listOf(stop("later", 2), stop("next", 1), here))!!.id)
        assertEquals("restored", CurrentStop.next(listOf(stop("restored", 0), here.copy(orderIndex = 1)))!!.id)
        assertNull(CurrentStop.next(listOf(here, stop("skipped", 1, StopState.SKIPPED))))
        assertNull(CurrentStop.next(emptyList()))
    }

    @Test
    fun `heading is the next stop unless you are mid-stay`() {
        val stops = listOf(stop("here", 0, StopState.DONE, arrival = today.minusDays(1), nights = 2), stop("next", 1))
        assertNull(CurrentStop.heading(stops, today))
        assertEquals("next", CurrentStop.heading(stops, today.plusDays(1))!!.id)
        // Nothing planned: nowhere to head for, mid-stay or not.
        assertNull(CurrentStop.heading(stops.take(1), today.plusDays(5)))
        // A zero-night check-in never holds the heading.
        val visited = stop("visited", 0, StopState.DONE, nights = 0)
        assertEquals("next", CurrentStop.heading(listOf(visited, stop("next", 1)), today)!!.id)
    }

    @Test
    fun `shortening the stay with the stepper is the check-out`() {
        val here = stop("here", 0, StopState.DONE, arrival = today.minusDays(1), nights = 3)
        assertNull(CurrentStop.heading(listOf(here, stop("next", 1)), today))
        val shortened = listOf(here.copy(nights = 1), stop("next", 1))
        assertEquals("next", CurrentStop.heading(shortened, today)!!.id)
        assertEquals("next", CurrentStop.of(shortened, today))
    }
}
