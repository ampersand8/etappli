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
    fun `a reordered plan re-dates itself, keeping its start and its length`() {
        val stops = listOf(
            stop("a", 0, base, nights = 2),
            stop("b", 1, base.plusDays(4), nights = 1), // two unplanned nights in front of it
            stop("c", 2, base.plusDays(5), nights = 3),
        )
        val rows = Timeline.move(Timeline.rows(stops), "c", 0)!!
        val changed = DateCascade.resequence(rows, base)
        assertEquals(listOf("c", "a", "b"), changed.map { it.id })
        assertEquals(listOf(0, 1, 2), changed.map { it.orderIndex })
        assertEquals(base, changed[0].arrivalDate) // still starts on day one
        assertEquals(base.plusDays(3), changed[1].arrivalDate)
        assertEquals(base.plusDays(7), changed[2].arrivalDate) // 2 nights + the kept gap
        // ...and still ends when it used to: 6 nights plus 2 empty ones.
        assertEquals(base.plusDays(8), changed[2].arrivalDate.plusDays(1))
    }

    @Test
    fun `dropping the gap row shortens the plan, and gaps in a row merge`() {
        val stops = listOf(stop("a", 0, base), stop("b", 1, base.plusDays(3), nights = 2))
        val rows = Timeline.rows(stops)
        assertEquals(base.plusDays(1), DateCascade.resequence(rows.filterNot { it is GapRow }, base).single().arrivalDate)

        val doubled = rows.flatMap { if (it is GapRow) listOf(it, it) else listOf(it) }
        assertEquals(base.plusDays(5), DateCascade.resequence(doubled, base).single().arrivalDate)
    }

    @Test
    fun `done stops anchor the chain and skipped stops sit outside it`() {
        val stops = listOf(
            stop("done", 0, base, StopState.DONE, nights = 2),
            stop("skipped", 1, base.plusDays(9), StopState.SKIPPED),
            stop("x", 2, base.plusDays(3), nights = 1),
            stop("y", 3, base.plusDays(4), nights = 1),
        )
        val rows = Timeline.move(Timeline.rows(stops), "y", 3)!! // in front of x
        val changed = DateCascade.resequence(rows, base)
        assertEquals(listOf("y", "x"), changed.map { it.id })
        assertEquals(base.plusDays(3), changed[0].arrivalDate) // after the done stay and its gap
        assertEquals(base.plusDays(4), changed[1].arrivalDate)
        assertEquals(listOf(2, 3), changed.map { it.orderIndex }) // the skipped stop keeps its slot
    }

    @Test
    fun `an order that changes no arrival writes nothing`() {
        val stops = listOf(stop("a", 0, base), stop("b", 1, base.plusDays(1)))
        assertTrue(DateCascade.resequence(Timeline.rows(stops), base).isEmpty())
    }
}
