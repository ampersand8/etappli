package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StopReorderTest {

    private fun stop(id: String, order: Int, state: StopState = StopState.PLANNED) =
        Stop(id = id, orderIndex = order, state = state)

    private val planned = listOf(stop("c", 2), stop("a", 0), stop("b", 1), stop("d", 3))

    @Test
    fun `a stop lands on the drop index, order taken from orderIndex`() {
        assertEquals(listOf("b", "c", "a", "d"), StopReorder.move(planned, "a", 2))
        assertEquals(listOf("d", "a", "b", "c"), StopReorder.move(planned, "d", 0))
    }

    @Test
    fun `a drop past the ends clamps instead of dropping the stop`() {
        assertEquals(listOf("b", "c", "d", "a"), StopReorder.move(planned, "a", 9))
        assertEquals(listOf("d", "a", "b", "c"), StopReorder.move(planned, "d", -3))
    }

    @Test
    fun `dropping on the same slot, or on an unknown stop, changes nothing`() {
        assertNull(StopReorder.move(planned, "b", 1))
        assertNull(StopReorder.move(planned, "nope", 0))
    }

    @Test
    fun `done stops anchor the window on both sides`() {
        val stops = listOf(
            stop("done1", 0, StopState.DONE),
            stop("x", 1),
            stop("y", 2),
            stop("done2", 3, StopState.DONE),
            stop("z", 4),
        )
        // y can reach x, never the done row above it.
        assertEquals(listOf("done1", "y", "x", "done2", "z"), StopReorder.move(stops, "y", 0))
        // ...nor the one below it.
        assertNull(StopReorder.move(stops, "y", 4))
        // A done stop is not draggable at all.
        assertNull(StopReorder.move(stops, "done2", 1))
        // Below the second anchor, z is alone.
        assertNull(StopReorder.move(stops, "z", 0))
    }

    @Test
    fun `skipped stops move like planned ones`() {
        val stops = listOf(stop("a", 0), stop("s", 1, StopState.SKIPPED))
        assertEquals(listOf("s", "a"), StopReorder.move(stops, "s", 0))
    }
}
