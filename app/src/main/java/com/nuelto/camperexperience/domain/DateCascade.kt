package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DateCascade {

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
     * Re-dates a plan after a reorder — [orderedIds] is the new order, [stops] the
     * stops as they stood. Every stop keeps its nights and every position keeps the
     * empty days in front of it, so the trip still starts and ends on the same days;
     * only who you visit when changes. DONE stops anchor the chain and are never
     * rewritten, SKIPPED stops sit outside it. Returns the stops whose arrival moved,
     * carrying their new orderIndex; the caller upserts exactly those.
     */
    fun resequence(stops: List<Stop>, orderedIds: List<String>): List<Stop> {
        val before = stops.sortedBy { it.orderIndex }.filterNot { it.state == StopState.SKIPPED }
        if (before.isEmpty()) return emptyList()
        val gaps = before.mapIndexed { i, stop ->
            if (i == 0) 0L else ChronoUnit.DAYS.between(departure(before[i - 1]), stop.arrivalDate).coerceAtLeast(0)
        }
        val byId = stops.associateBy { it.id }
        var cursor = before.first().arrivalDate
        return orderedIds.mapNotNull(byId::get).filterNot { it.state == StopState.SKIPPED }
            .mapIndexedNotNull { i, stop ->
                if (stop.state == StopState.DONE) {
                    cursor = departure(stop)
                    return@mapIndexedNotNull null
                }
                val arrival = cursor.plusDays(gaps[i])
                cursor = arrival.plusDays(stop.nights.toLong())
                if (arrival == stop.arrivalDate) {
                    null
                } else {
                    stop.copy(arrivalDate = arrival, orderIndex = orderedIds.indexOf(stop.id))
                }
            }
    }

    private fun departure(stop: Stop): LocalDate = stop.arrivalDate.plusDays(stop.nights.toLong())
}
