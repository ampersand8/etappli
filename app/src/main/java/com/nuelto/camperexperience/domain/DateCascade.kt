package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import java.time.LocalDate

object DateCascade {

    /**
     * Derives arrival dates for PLANNED stops from the trip start plus cumulative
     * nights, re-anchoring at every DONE stop's actual dates. DONE and SKIPPED stops
     * are never rewritten; SKIPPED stops contribute no nights. Returns only the stops
     * whose arrivalDate changed — the caller upserts exactly those.
     */
    fun apply(startDate: LocalDate, stops: List<Stop>): List<Stop> {
        var cursor = startDate
        val changed = mutableListOf<Stop>()
        for (stop in stops.sortedBy { it.orderIndex }) {
            when (stop.state) {
                StopState.DONE -> cursor = stop.arrivalDate.plusDays(stop.nights.toLong())
                StopState.SKIPPED -> {}
                StopState.PLANNED -> {
                    if (stop.arrivalDate != cursor) changed += stop.copy(arrivalDate = cursor)
                    cursor = cursor.plusDays(stop.nights.toLong())
                }
            }
        }
        return changed
    }
}
