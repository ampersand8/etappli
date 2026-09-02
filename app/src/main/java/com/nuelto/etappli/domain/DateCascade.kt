package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopState
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DateCascade {

    /** The day a schedule starts: the arrival of its first stop that is not skipped. */
    fun start(stops: List<Stop>): LocalDate? =
        stops.filterNot { it.state == StopState.SKIPPED }.minByOrNull { it.orderIndex }?.arrivalDate

    /**
     * Shifts the arrival dates of PLANNED stops ordered after [afterOrderIndex] by
     * [days] — used when a stay is extended/shortened or an arrival lands late, so
     * the downstream schedule follows. DONE and SKIPPED stops are never rewritten,
     * and deliberate gaps between stops survive (this shifts, it doesn't normalize).
     * Returns only the changed stops; the caller upserts exactly those.
     */
    fun shift(stops: List<Stop>, afterOrderIndex: Int, days: Long): List<Stop> =
        if (days == 0L) {
            emptyList()
        } else {
            stops.filter { it.orderIndex > afterOrderIndex && it.state == StopState.PLANNED }
                .map { it.copy(arrivalDate = it.arrivalDate.plusDays(days)) }
        }

    /**
     * Re-dates a plan from the timeline [rows] in their new order, starting the day
     * the plan starts ([start]). Gap rows spend their nights, every stop keeps its
     * own, and consecutive gaps merge — so a reorder that only shuffles rows leaves
     * the trip as long as it was. DONE stops anchor the chain and are never rewritten,
     * SKIPPED stops sit outside it. Returns the stops whose arrival moved, carrying
     * their new orderIndex; the caller upserts exactly those.
     */
    fun resequence(rows: List<TimelineRow>, start: LocalDate): List<Stop> {
        val changed = mutableListOf<Stop>()
        var cursor = start
        var gap = 0L
        var order = 0
        for (row in rows) {
            if (row is GapRow) {
                gap += row.nights
                continue
            }
            val stop = (row as StopRow).stop
            val index = order++
            when (stop.state) {
                StopState.SKIPPED -> continue
                StopState.DONE -> cursor = departure(stop)
                StopState.PLANNED -> {
                    val arrival = cursor.plusDays(gap)
                    cursor = arrival.plusDays(stop.nights.toLong())
                    if (arrival != stop.arrivalDate) {
                        changed += stop.copy(arrivalDate = arrival, orderIndex = index)
                    }
                }
            }
            gap = 0
        }
        return changed
    }

    private fun departure(stop: Stop): LocalDate = stop.arrivalDate.plusDays(stop.nights.toLong())
}
