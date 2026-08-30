package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateCascadeTest {

    private val base = LocalDate.of(2027, 6, 10)

    private fun stop(
        id: String,
        order: Int,
        arrival: LocalDate,
        state: StopState = StopState.PLANNED,
        nights: Int = 1,
    ) = Stop(id = id, orderIndex = order, arrivalDate = arrival, state = state, nights = nights)

    @Test
    fun `planned stops after the pivot shift by the delta`() {
        val stops = listOf(
            stop("before", 0, base),
            stop("pivot", 1, base.plusDays(2)),
            stop("after1", 2, base.plusDays(3)),
            stop("after2", 3, base.plusDays(6)), // deliberate gap — must survive
        )
        val changed = DateCascade.shift(stops, afterOrderIndex = 1, days = 2)
        assertEquals(listOf("after1", "after2"), changed.map { it.id })
        assertEquals(base.plusDays(5), changed[0].arrivalDate)
        assertEquals(base.plusDays(8), changed[1].arrivalDate)
    }

    @Test
    fun `negative deltas pull the schedule forward`() {
        val stops = listOf(stop("a", 0, base), stop("b", 1, base.plusDays(4)))
        val changed = DateCascade.shift(stops, afterOrderIndex = 0, days = -1)
        assertEquals(base.plusDays(3), changed.single().arrivalDate)
    }

    @Test
    fun `done and skipped stops are never rewritten`() {
        val stops = listOf(
            stop("pivot", 0, base),
            stop("done", 1, base.plusDays(1), StopState.DONE),
            stop("skipped", 2, base.plusDays(2), StopState.SKIPPED),
            stop("planned", 3, base.plusDays(3)),
        )
        assertEquals(listOf("planned"), DateCascade.shift(stops, 0, 1).map { it.id })
    }

    @Test
    fun `a zero delta changes nothing`() {
        assertTrue(DateCascade.shift(listOf(stop("a", 1, base)), 0, 0).isEmpty())
    }

    @Test
    fun `stops at or before the pivot index are untouched`() {
        val stops = listOf(stop("at", 1, base), stop("before", 0, base))
        assertTrue(DateCascade.shift(stops, afterOrderIndex = 1, days = 3).isEmpty())
    }

    @Test
    fun `a reordered plan re-dates itself, keeping its start, gaps and length`() {
        val stops = listOf(
            stop("a", 0, base, nights = 2),
            stop("b", 1, base.plusDays(4), nights = 1), // two empty days in front of it
            stop("c", 2, base.plusDays(5), nights = 3),
        )
        val changed = DateCascade.resequence(stops, listOf("c", "a", "b"))
        assertEquals(listOf("c", "a", "b"), changed.map { it.id })
        assertEquals(listOf(0, 1, 2), changed.map { it.orderIndex })
        assertEquals(base, changed[0].arrivalDate) // still starts on day one
        assertEquals(base.plusDays(5), changed[1].arrivalDate) // 3 nights + the kept gap
        assertEquals(base.plusDays(7), changed[2].arrivalDate)
        // ...and still ends when it used to: 6 nights + 2 empty days.
        assertEquals(base.plusDays(8), changed[2].arrivalDate.plusDays(1))
    }

    @Test
    fun `done stops anchor the chain and skipped stops sit outside it`() {
        val stops = listOf(
            stop("done", 0, base, StopState.DONE, nights = 2),
            stop("skipped", 1, base.plusDays(9), StopState.SKIPPED),
            stop("x", 2, base.plusDays(2), nights = 1),
            stop("y", 3, base.plusDays(3), nights = 1),
        )
        val changed = DateCascade.resequence(stops, listOf("done", "skipped", "y", "x"))
        assertEquals(listOf("y", "x"), changed.map { it.id })
        assertEquals(base.plusDays(2), changed[0].arrivalDate) // straight after the done stay
        assertEquals(base.plusDays(3), changed[1].arrivalDate)
    }

    @Test
    fun `an order that changes no arrival, and an empty plan, write nothing`() {
        val stops = listOf(stop("a", 0, base), stop("b", 1, base.plusDays(1)))
        assertTrue(DateCascade.resequence(stops, listOf("a", "b")).isEmpty())
        assertTrue(DateCascade.resequence(listOf(stop("s", 0, base, StopState.SKIPPED)), listOf("s")).isEmpty())
    }

    @Test
    fun `overlapping stays are pulled back into a chain`() {
        val stops = listOf(stop("a", 0, base, nights = 3), stop("b", 1, base.plusDays(1)))
        val changed = DateCascade.resequence(stops, listOf("b", "a"))
        assertEquals(base, changed[0].arrivalDate)
        assertEquals(base.plusDays(1), changed[1].arrivalDate) // no negative gap in front of a
    }
}
