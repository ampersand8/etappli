package com.nuelto.etappli.ui

import com.nuelto.etappli.data.model.StopLeg
import com.nuelto.etappli.data.model.TransitRide
import com.nuelto.etappli.data.model.TravelMode
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Currency
import kotlin.math.roundToInt

fun formatCurrency(amount: Double, currencyCode: String): String {
    val format = NumberFormat.getCurrencyInstance()
    runCatching { format.currency = Currency.getInstance(currencyCode) }
    return format.format(amount)
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatDate(date: LocalDate): String = date.format(dateFormatter)

fun formatDateRange(start: LocalDate, end: LocalDate?): String =
    if (end == null) "${formatDate(start)} – ongoing" else "${formatDate(start)} – ${formatDate(end)}"

/** A plan without an end isn't "ongoing" — it just has a tentative start. */
fun formatTripDates(trip: Trip): String =
    if (trip.status == TripStatus.PLANNED && trip.endDate == null) {
        "from ${formatDate(trip.startDate)}"
    } else {
        formatDateRange(trip.startDate, trip.endDate)
    }

/** Driving distance at the precision a road sign uses — nobody plans in metres. */
fun formatDistance(meters: Int): String =
    if (meters < 1_000) "$meters m" else "${(meters / 1000.0).roundToInt()} km"

fun formatDuration(seconds: Int): String {
    val minutes = (seconds / 60.0).roundToInt()
    return when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}

/** One piece of the line under a stop: what you are on, and how far or how long. */
data class DrivePart(val modes: List<TravelMode>, val text: String)

/**
 * The drive that arrives at a stop, piece by piece: the car with "124 km · 1 h 45 min",
 * and a ride either side of the road with its minutes. Deliberately not the leg's climb —
 * cumulative ascent next to a place name reads as the height of the place, which is a
 * different number and the one people actually want. When there is no way at all it says
 * so: the map draws a straight hop there, and this is the only place that explains why.
 */
fun driveParts(leg: StopLeg): List<DrivePart> {
    val car = listOf(TravelMode.CAR)
    if (!leg.hasRoad) return listOf(DrivePart(car, "No drivable route"))
    return listOfNotNull(
        leg.rideBefore?.let(::ridePart),
        DrivePart(car, "${formatDistance(leg.distanceMeters)} · ${formatDuration(leg.durationSeconds)}"),
        leg.rideAfter?.let(::ridePart),
    )
}

/** A ride that does not say what it is on still gets an icon: the generic one. */
private fun ridePart(ride: TransitRide) =
    DrivePart(ride.modes.ifEmpty { listOf(TravelMode.TRANSIT) }, formatDuration(ride.durationSeconds))

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/**
 * When you get there if you set off now. Names the day too once the drive crosses
 * midnight — "arrive 04:12" on its own would read as four hours ago.
 */
fun formatArrival(durationSeconds: Int, now: LocalDateTime): String {
    // Rounded to the minute exactly as formatDuration rounds, or the same line would say
    // "2 min" and then an arrival one minute earlier than that.
    val arrival = now.plusMinutes((durationSeconds / 60.0).roundToInt().toLong())
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), arrival.toLocalDate())
    val day = when (days) {
        0L -> ""
        1L -> " tomorrow"
        else -> " on ${formatDate(arrival.toLocalDate())}"
    }
    return "arrive ${arrival.format(timeFormatter)}$day"
}

/**
 * The live drive to the stop you are heading for, and when it gets you there:
 * "47 km · 55 min from here · arrive 16:42".
 */
fun formatDriveFromHere(distanceMeters: Int, durationSeconds: Int, now: LocalDateTime): String =
    "${formatDistance(distanceMeters)} · ${formatDuration(durationSeconds)} from here · " +
        formatArrival(durationSeconds, now)
