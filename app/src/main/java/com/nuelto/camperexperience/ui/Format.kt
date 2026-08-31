package com.nuelto.camperexperience.ui

import com.nuelto.camperexperience.data.model.StopLeg
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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

/**
 * The drive that arrives at a stop: "124 km · 1 h 45 min". Deliberately not the leg's
 * climb — cumulative ascent next to a place name reads as the height of the place, which
 * is a different number and the one people actually want.
 */
fun formatDrive(leg: StopLeg): String =
    "${formatDistance(leg.distanceMeters)} · ${formatDuration(leg.durationSeconds)}"

/** The live drive to the stop you are heading for: "47 km · 55 min from here". */
fun formatDriveFromHere(distanceMeters: Int, durationSeconds: Int): String =
    "${formatDistance(distanceMeters)} · ${formatDuration(durationSeconds)} from here"
