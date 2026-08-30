package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import java.time.LocalDate

object CurrentStop {

    /**
     * Tonight's stop on an active trip. A checked-in stop stays current until its
     * stay is over (so the ±nights stepper is at hand mid-stay); afterwards the
     * first upcoming stop past it — a stop restored behind the check-in line only
     * becomes current when nothing lies ahead.
     */
    fun of(stops: List<Stop>, today: LocalDate): String? {
        val ordered = stops.sortedBy { it.orderIndex }.filterNot { it.state == StopState.SKIPPED }
        val lastDone = ordered.lastOrNull { it.state == StopState.DONE }
        if (lastDone != null && today.isBefore(lastDone.arrivalDate.plusDays(lastDone.nights.toLong()))) {
            return lastDone.id
        }
        val upcoming = ordered.filter { it.state == StopState.PLANNED }
        return upcoming.firstOrNull { lastDone == null || it.orderIndex > lastDone.orderIndex }?.id
            ?: upcoming.firstOrNull()?.id
    }
}
