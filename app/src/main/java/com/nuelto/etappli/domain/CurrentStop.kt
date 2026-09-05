package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopState
import java.time.LocalDate

object CurrentStop {

    /**
     * Tonight's stop on an active trip. A checked-in stay stays current until it is over
     * (so the ±nights stepper is at hand mid-stay); otherwise [next].
     */
    fun of(stops: List<Stop>, today: LocalDate): String? =
        staying(stops, today)?.id ?: next(stops)?.id

    /**
     * The stop the drive underway arrives at: the first planned stop past the check-in
     * line, else the first planned stop — a stop restored behind the line only comes up
     * when nothing lies ahead.
     */
    fun next(stops: List<Stop>): Stop? {
        val ordered = ordered(stops)
        val lastDone = ordered.lastOrNull { it.state == StopState.DONE }
        val upcoming = ordered.filter { it.state == StopState.PLANNED }
        return upcoming.firstOrNull { lastDone == null || it.orderIndex > lastDone.orderIndex }
            ?: upcoming.firstOrNull()
    }

    /**
     * The stop you are driving to: [next] unless you are mid-stay. Null with nothing
     * planned. A stay is over the morning after its last night; the Check out button
     * (Stay.checkOut) and the nights stepper are how you leave early.
     */
    fun heading(stops: List<Stop>, today: LocalDate): Stop? =
        if (staying(stops, today) != null) null else next(stops)

    /**
     * The checked-in stay that is not over yet. A checked-in stop with no nights — the
     * home a tour set out from, a visit — is behind you the moment it is done, whatever
     * its date says: a tour started ahead of its start date must head for the first
     * stop, not sit "checked in" at home.
     */
    private fun staying(stops: List<Stop>, today: LocalDate): Stop? =
        ordered(stops).lastOrNull { it.state == StopState.DONE }
            ?.takeIf { it.nights > 0 && today.isBefore(it.arrivalDate.plusDays(it.nights.toLong())) }

    private fun ordered(stops: List<Stop>): List<Stop> =
        stops.sortedBy { it.orderIndex }.filterNot { it.state == StopState.SKIPPED }
}
