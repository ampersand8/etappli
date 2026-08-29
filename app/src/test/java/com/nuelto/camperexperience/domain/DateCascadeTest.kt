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
    ) = Stop(id = id, orderIndex = order, arrivalDate = arrival, state = state)

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
}
