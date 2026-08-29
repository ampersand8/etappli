package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState

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
}
