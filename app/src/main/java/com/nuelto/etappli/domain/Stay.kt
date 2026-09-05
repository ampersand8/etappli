package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopState
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Checking in and out of a stop, and where a stay stands: pure, so the tracker can check you
 * in from the background exactly as the ✓ Arrived button does. [checkIn], [undoCheckIn] and
 * [checkOut] return the stops to write as one [com.nuelto.etappli.data.TripRepository.upsertStops]
 * — the stop and the shift it causes in the plan behind it — and nothing when there is
 * nothing to do.
 */
object Stay {

    /** Standing this close to the stop you are heading for... */
    const val CHECK_IN_WITHIN_METERS = 500.0

    /** ...for this long is arriving, and the tracker checks you in (RouteTracker). */
    val CHECK_IN_AFTER: Duration = Duration.ofMinutes(10)

    /**
     * Only a fix this far away has left the stop. In between — a pitch on the far edge of
     * the site, GPS wandering — a fix neither adds to the stay nor breaks it.
     */
    const val LEFT_BEYOND_METERS = 1_000.0

    /**
     * Fixes at the stop further apart than this are two visits, not one stay: a couple of
     * missed fixes are fine, a drive-by and a drive-back are not.
     */
    val FIX_GAP: Duration = Duration.ofMillis(3 * Tracks.FIX_INTERVAL_MS)

    /** The fixes at one stop so far: the first and the latest. Zoned, so a night the clocks change counts real minutes. */
    data class Dwell(val stopId: String, val since: ZonedDateTime, val last: ZonedDateTime) {
        /** [CHECK_IN_AFTER] of them: you have arrived. */
        val checksIn: Boolean get() = Duration.between(since, last) >= CHECK_IN_AFTER
    }

    /** One more fix at [stopId]: onto [previous] when that is the same stop within [FIX_GAP], else a fresh one. */
    fun dwell(previous: Dwell?, stopId: String, at: ZonedDateTime): Dwell =
        if (previous != null && previous.stopId == stopId && Duration.between(previous.last, at) <= FIX_GAP) {
            previous.copy(last = at)
        } else {
            Dwell(stopId, at, at)
        }

    /**
     * Check-in at [at]: the stop is reached, its arrival is that day and time, and a late
     * (or early) arrival shifts the plan behind it by the days it was off. Only a PLANNED
     * stop: a check-in is not repeated, so a tap and the tracker racing each other leave
     * the first stamp standing.
     */
    fun checkIn(stops: List<Stop>, stopId: String, at: LocalDateTime): List<Stop> {
        val stop = stops.find { it.id == stopId }?.takeIf { it.state == StopState.PLANNED } ?: return emptyList()
        val day = at.toLocalDate()
        return listOf(stop.copy(state = StopState.DONE, arrivalDate = day, arrivalTime = at.toLocalTime().truncatedTo(ChronoUnit.MINUTES))) +
            DateCascade.shift(stops, stop.orderIndex, ChronoUnit.DAYS.between(stop.arrivalDate, day))
    }

    /**
     * Takes a check-in back — the tracker got it wrong, or a thumb did — leaving the stop
     * planned for the day it was stamped with, so the plan behind it stays where it is.
     */
    fun undoCheckIn(stops: List<Stop>, stopId: String): List<Stop> =
        stops.find { it.id == stopId }?.takeIf { it.state == StopState.DONE }
            ?.let { listOf(it.copy(state = StopState.PLANNED, arrivalTime = null)) }
            .orEmpty()

    /**
     * Check-out on [today]: the stay is as many nights as were slept, and the plan behind it
     * moves up by the nights it lost — the stepper's −1, as many times as it takes. Never
     * lengthens a stay: on or after the planned departure there is nothing to do.
     */
    fun checkOut(stops: List<Stop>, stopId: String, today: LocalDate): List<Stop> {
        val stop = stops.find { it.id == stopId }?.takeIf { it.state == StopState.DONE } ?: return emptyList()
        val nights = ChronoUnit.DAYS.between(stop.arrivalDate, today).coerceIn(0, stop.nights.toLong()).toInt()
        if (nights == stop.nights) return emptyList()
        return listOf(stop.copy(nights = nights)) + DateCascade.shift(stops, stop.orderIndex, (nights - stop.nights).toLong())
    }

    /**
     * Whether a fix is at [stop] as far as arriving goes: within [CHECK_IN_WITHIN_METERS] of
     * its own pin — not the parking spot of a ride, the phone rides up with you — and on or
     * after the day it is planned for. A day early you may just be passing; a day late is
     * still arriving.
     */
    fun near(point: LatLng, stop: Stop, today: LocalDate): Boolean {
        val location = stop.location ?: return false
        return !today.isBefore(stop.arrivalDate) && GeoUtils.haversineKm(point, location) * 1000 < CHECK_IN_WITHIN_METERS
    }

    /** Whether a fix has left [stop] for good — [LEFT_BEYOND_METERS] from its pin, or it has none. */
    fun left(point: LatLng, stop: Stop): Boolean {
        val location = stop.location ?: return true
        return GeoUtils.haversineKm(point, location) * 1000 >= LEFT_BEYOND_METERS
    }

    /** Where a stay stands on a day: nights slept, nights still to sleep, and the morning it ends. */
    data class Progress(val stayed: Int, val ahead: Int, val departure: LocalDate)

    fun progress(stop: Stop, today: LocalDate): Progress {
        val stayed = ChronoUnit.DAYS.between(stop.arrivalDate, today).coerceIn(0, stop.nights.toLong()).toInt()
        return Progress(stayed, stop.nights - stayed, stop.arrivalDate.plusDays(stop.nights.toLong()))
    }
}
