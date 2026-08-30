package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState

/**
 * Order changes coming out of a drag. DONE stops are anchors — history can't be
 * corrupted by a drag — so a stop only moves inside the window between the nearest
 * DONE stop above and below it.
 */
object StopReorder {

    /** The new id order after dropping [stopId] at [toIndex], or null if nothing moves. */
    fun move(stops: List<Stop>, stopId: String, toIndex: Int): List<String>? {
        val ordered = stops.sortedBy { it.orderIndex }
        val from = ordered.indexOfFirst { it.id == stopId }
        if (from < 0 || ordered[from].state == StopState.DONE) return null
        val to = toIndex.coerceIn(window(ordered, from))
        if (to == from) return null
        val ids = ordered.mapTo(mutableListOf()) { it.id }
        ids.add(to, ids.removeAt(from))
        return ids
    }

    /** Positions the stop at [from] may be dropped on. */
    private fun window(ordered: List<Stop>, from: Int): IntRange {
        val done = { i: Int -> ordered[i].state == StopState.DONE }
        val lower = (from - 1 downTo 0).firstOrNull(done)?.plus(1) ?: 0
        val upper = (from + 1..ordered.lastIndex).firstOrNull(done)?.minus(1) ?: ordered.lastIndex
        return lower..upper
    }
}
