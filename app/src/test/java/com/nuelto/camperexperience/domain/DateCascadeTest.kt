package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateCascadeTest {

    private val start = LocalDate.of(2027, 6, 10)

    private fun stop(
        id: String,
        order: Int,
        nights: Int,
        arrival: LocalDate,
        state: StopState = StopState.PLANNED,
    ) = Stop(id = id, orderIndex = order, nights = nights, arrivalDate = arrival, state = state)

    @Test
    fun `planned stops chain from the trip start`() {
        val stops = listOf(
            stop("a", 0, 2, start),
            stop("b", 1, 0, LocalDate.of(2027, 1, 1)),
            stop("c", 2, 3, LocalDate.of(2027, 1, 1)),
        )
        val changed = DateCascade.apply(start, stops)
        // a already correct; b lands after a's 2 nights; c right after the 0-night visit.
        assertEquals(listOf("b", "c"), changed.map { it.id })
        assertEquals(start.plusDays(2), changed[0].arrivalDate)
        assertEquals(start.plusDays(2), changed[1].arrivalDate)
    }

    @Test
    fun `done stops re-anchor the chain and are never rewritten`() {
        val actualArrival = start.plusDays(5) // arrived later than planned
        val stops = listOf(
            stop("done", 0, 2, actualArrival, StopState.DONE),
            stop("next", 1, 1, start.plusDays(2)),
        )
        val changed = DateCascade.apply(start, stops)
        assertEquals(listOf("next"), changed.map { it.id })
        assertEquals(actualArrival.plusDays(2), changed.single().arrivalDate)
    }

    @Test
    fun `skipped stops keep their date and contribute no nights`() {
        val stops = listOf(
            stop("a", 0, 2, start),
            stop("skipped", 1, 4, start.plusDays(2), StopState.SKIPPED),
            stop("c", 2, 1, LocalDate.of(2027, 1, 1)),
        )
        val changed = DateCascade.apply(start, stops)
        assertEquals(listOf("c"), changed.map { it.id })
        assertEquals(start.plusDays(2), changed.single().arrivalDate)
    }

    @Test
    fun `stops are cascaded in order index order, not list order`() {
        val stops = listOf(
            stop("second", 1, 1, LocalDate.of(2027, 1, 1)),
            stop("first", 0, 2, start),
        )
        val changed = DateCascade.apply(start, stops)
        assertEquals(start.plusDays(2), changed.single { it.id == "second" }.arrivalDate)
    }

    @Test
    fun `nothing changes when dates already line up`() {
        val stops = listOf(
            stop("a", 0, 2, start),
            stop("b", 1, 1, start.plusDays(2)),
        )
        assertTrue(DateCascade.apply(start, stops).isEmpty())
    }
}
