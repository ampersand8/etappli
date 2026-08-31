package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineTest {

    private val base = LocalDate.of(2027, 9, 1)

    private fun stop(
        id: String,
        order: Int,
        arrival: LocalDate,
        nights: Int = 1,
        state: StopState = StopState.PLANNED,
    ) = Stop(id = id, orderIndex = order, arrivalDate = arrival, nights = nights, state = state)

    private fun keys(rows: List<TimelineRow>) = rows.map { it.key }

    @Test
    fun `unplanned nights between two stops become a row of their own`() {
        val stops = listOf(
            stop("a", 0, base, nights = 2), // leaves the 3rd
            stop("b", 1, base.plusDays(4)), // arrives the 5th — two nights unaccounted for
        )
        val rows = Timeline.rows(stops)
        assertEquals(listOf("a", "gap-b", "b"), keys(rows))
        val gap = rows[1] as GapRow
        assertEquals(2, gap.nights)
        assertEquals(base.plusDays(2), gap.from)
        assertEquals("b", gap.anchor.id)
    }

    @Test
    fun `an insertion takes the place and the first day of the row it lands in front of`() {
        val stops = listOf(
            stop("a", 0, base, nights = 2), // leaves the 3rd
            stop("b", 1, base.plusDays(4)), // arrives the 5th
        )
        val rows = Timeline.rows(stops)
        // In front of the empty nights: the day after A leaves, taking B's index.
        assertEquals(Insertion(1, base.plusDays(2)), Timeline.insertion(rows, "gap-b"))
        // In front of B itself: after those nights have been spent.
        assertEquals(Insertion(1, base.plusDays(4)), Timeline.insertion(rows, "b"))
        assertNull(Timeline.insertion(rows, "gone"))
    }

    @Test
    fun `back-to-back and overlapping stops leave no gap`() {
        val stops = listOf(
            stop("a", 0, base, nights = 2),
            stop("b", 1, base.plusDays(2)),
            stop("c", 2, base.plusDays(2)), // same day again — never a negative gap
        )
        assertEquals(listOf("a", "b", "c"), keys(Timeline.rows(stops)))
    }

    @Test
    fun `skipped stops keep their place but not their nights`() {
        val stops = listOf(
            stop("a", 0, base),
            stop("s", 1, base.plusDays(9), state = StopState.SKIPPED),
            stop("b", 2, base.plusDays(2)),
        )
        assertEquals(listOf("a", "s", "gap-b", "b"), keys(Timeline.rows(stops)))
        assertEquals(1, (Timeline.rows(stops)[2] as GapRow).nights)
    }

    @Test
    fun `a row lands on the drop index, clamped to the ends`() {
        val rows = Timeline.rows(listOf(stop("a", 0, base), stop("b", 1, base.plusDays(1)), stop("c", 2, base.plusDays(2))))
        assertEquals(listOf("b", "c", "a"), keys(Timeline.move(rows, "a", 2)!!))
        assertEquals(listOf("c", "a", "b"), keys(Timeline.move(rows, "c", -4)!!))
        assertNull(Timeline.move(rows, "a", 0)) // already there
        assertNull(Timeline.move(rows, "nope", 1))
    }

    @Test
    fun `done stops anchor the window on both sides`() {
        val rows = Timeline.rows(
            listOf(
                stop("done", 0, base, state = StopState.DONE),
                stop("x", 1, base.plusDays(1)),
                stop("y", 2, base.plusDays(2)),
                stop("done2", 3, base.plusDays(3), state = StopState.DONE),
                stop("z", 4, base.plusDays(4)),
            ),
        )
        assertEquals(listOf("done", "y", "x", "done2", "z"), keys(Timeline.move(rows, "y", 0)!!))
        assertNull(Timeline.move(rows, "y", 4)) // can't pass the second done stop
        assertNull(Timeline.move(rows, "done2", 1)) // history doesn't move
        assertNull(Timeline.move(rows, "z", 0)) // alone below the anchor
    }

    @Test
    fun `a gap only moves between two stops, and is renamed where it lands`() {
        val rows = Timeline.rows(
            listOf(
                stop("a", 0, base, nights = 2),
                stop("b", 1, base.plusDays(4)), // two unplanned nights in front of b
                stop("c", 2, base.plusDays(5)),
            ),
        )
        assertEquals(listOf("a", "gap-b", "b", "c"), keys(rows))

        val moved = Timeline.move(rows, "gap-b", 2)!!
        assertEquals(listOf("a", "b", "gap-b", "c"), keys(moved))
        assertEquals("gap-c", Timeline.keyAt(moved, 2)) // it now sits in front of c
        assertEquals("a", Timeline.keyAt(moved, 0))

        assertNull(Timeline.move(rows, "gap-b", 0)) // never before the first stop
        assertEquals(listOf("a", "b", "gap-b", "c"), keys(Timeline.move(rows, "gap-b", 9)!!)) // nor after the last
    }

    @Test
    fun `a gap with no stop left to sit in front of cannot move`() {
        val stop = stop("a", 0, base)
        val rows = listOf(GapRow(1, base, stop), StopRow(stop))
        assertNull(Timeline.move(rows, GapRow.key("a"), 1))
    }
}
