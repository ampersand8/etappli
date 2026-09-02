package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopState
import java.time.LocalDate

object CurrentStop {

    /**
     * Tonight's stop on an active trip. A checked-in stay stays current until it is over
     * (so the ±nights stepper is at hand mid-stay). A checked-in stop with no nights — the
     * home a tour set out from, a visit — is behind you the moment it is done, whatever
     * its date says: a tour started ahead of its start date must head for the first stop,
     * not sit "checked in" at home. Otherwise the first upcoming stop past the check-in
     * line — a stop restored behind it only becomes current when nothing lies ahead.
     */
    fun of(stops: List<Stop>, today: LocalDate): String? {
        val ordered = stops.sortedBy { it.orderIndex }.filterNot { it.state == StopState.SKIPPED }
        val lastDone = ordered.lastOrNull { it.state == StopState.DONE }
        if (lastDone != null && lastDone.nights > 0 &&
            today.isBefore(lastDone.arrivalDate.plusDays(lastDone.nights.toLong()))
        ) {
            return lastDone.id
        }
        val upcoming = ordered.filter { it.state == StopState.PLANNED }
        return upcoming.firstOrNull { lastDone == null || it.orderIndex > lastDone.orderIndex }?.id
            ?: upcoming.firstOrNull()?.id
    }
}
