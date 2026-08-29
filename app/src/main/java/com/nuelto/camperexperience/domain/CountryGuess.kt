package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState

/**
 * Coarse bounding boxes mapping stop coordinates to vignette countries — offline,
 * confirm-only suggestions (over-suggesting near borders is harmless; transit-only
 * countries with no stop are missed, hence the manual picker).
 */
object CountryGuess {

    private data class Box(
        val code: String,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
    ) {
        fun contains(lat: Double, lon: Double): Boolean =
            lat in minLat..maxLat && lon in minLon..maxLon
    }

    private val boxes = listOf(
        Box("CH", 45.8, 47.9, 5.9, 10.6),
        Box("AT", 46.3, 49.1, 9.5, 17.2),
        Box("SI", 45.4, 46.9, 13.3, 16.6),
        Box("CZ", 48.5, 51.1, 12.0, 18.9),
    )

    private fun Stop.isIn(box: Box): Boolean =
        state != StopState.SKIPPED && location?.let { box.contains(it.latitude, it.longitude) } == true

    /** Vignette countries any non-skipped stop falls into, in table order. */
    fun vignetteCountries(stops: List<Stop>): List<String> =
        boxes.filter { box -> stops.any { it.isIn(box) } }.map { it.code }

    /** Days spent in the country: nights of its stops, at least 1 (a visit is a transit day). */
    fun daysIn(countryCode: String, stops: List<Stop>): Int {
        val box = boxes.first { it.code == countryCode }
        return stops.filter { it.isIn(box) }.sumOf { it.nights }.coerceAtLeast(1)
    }
}
