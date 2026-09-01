package com.nuelto.etappli.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Four 100px rows laid out from 0; [onMove] relayouts them like the real list would. */
class ReorderStateTest {

    private val order = mutableListOf("a", "b", "c", "d")
    private val moves = mutableListOf<Pair<String, String>>()

    private fun rows() = order.mapIndexed { i, key -> ItemGeometry(key, i * 100, 100) }

    private val state = ReorderState(
        rows = ::rows,
        onMove = { from, to ->
            moves += from to to
            order.add(order.indexOf(to), order.removeAt(order.indexOf(from)))
            from // stop rows keep their key across a move
        },
    )

    @Test
    fun `crossing a row moves it once, and the card stays under the finger`() {
        state.start("a")
        state.drag(60f) // centre 110, past b's centre
        assertEquals(listOf("b", "a", "c", "d"), order)
        assertEquals(listOf("a" to "b"), moves)
        assertEquals(-40f, state.translationFor("a"), 0.01f)
        assertEquals(0f, state.translationFor("b"), 0.01f)

        state.drag(20f) // centre 130 — a's own slot, nothing to cross
        assertEquals(listOf("a" to "b"), moves)

        state.drag(-50f) // centre 80 — nearer b's centre than its own, so back it goes
        assertEquals(listOf("a", "b", "c", "d"), order)
        assertEquals(30f, state.translationFor("a"), 0.01f)

        state.stop()
        assertNull(state.draggedKey)
        assertEquals(0f, state.translationFor("a"), 0.01f)
    }

    @Test
    fun `a drag fast enough to fly off the list lands on the row it flew past`() {
        state.start("a")
        state.drag(1_000f)
        assertEquals(listOf("a" to "d"), moves)
        assertEquals(listOf("b", "c", "d", "a"), order)
    }

    @Test
    fun `a drag without a lifted row, or of a row that is gone, does nothing`() {
        state.drag(500f)
        assertEquals(emptyList<Pair<String, String>>(), moves)
        assertEquals(0f, state.autoScroll(0, 250), 0.01f)

        state.start("nope")
        assertNull(state.draggedKey)

        state.start("a")
        order.clear()
        state.drag(10f)
        assertEquals(emptyList<Pair<String, String>>(), moves)
        assertEquals(0f, state.translationFor("a"), 0.01f)
    }

    @Test
    fun `a card held against a viewport edge asks for a capped scroll`() {
        state.start("b") // rows 100..200, inside the 50..250 viewport
        assertEquals(0f, state.autoScroll(50, 250), 0.01f)
        state.drag(-60f) // top 40, just above the start
        assertEquals(-10f, state.autoScroll(50, 250), 0.01f)
        state.drag(120f) // bottom 260, just past the end
        assertEquals(10f, state.autoScroll(50, 250), 0.01f)
        state.drag(200f)
        assertEquals(32f, state.autoScroll(50, 250), 0.01f)
        state.drag(-1_000f)
        assertEquals(-32f, state.autoScroll(50, 250), 0.01f)
    }

    @Test
    fun `lifting a second row starts from its own slot`() {
        state.start("a")
        state.drag(60f)
        state.stop()
        state.start("a")
        assertEquals(0f, state.translationFor("a"), 0.01f)
    }
}
