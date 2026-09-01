package com.nuelto.etappli.domain

import kotlin.math.ceil

/**
 * Bundled vignette prices for the camper-relevant countries. Facts, refreshed once a
 * year alongside the appVersionBase bump — no toll API exists free. Amounts convert
 * to the app currency at bundled rough rates; everything downstream is ≈-displayed.
 */
object VignetteTable {

    const val YEAR = 2026

    data class Vignette(
        val countryCode: String,
        val label: String,
        val days: Int,
        val price: Double,
        val currency: String,
    )

    /** How many of this vignette cover [days], and what that costs. */
    data class Choice(val vignette: Vignette, val count: Int) {
        val total: Double get() = vignette.price * count
        // Country code stays the prefix — expense labels are matched on it for dedup.
        val label: String get() = if (count == 1) vignette.label else "${vignette.label} ×$count"
    }

    val vignettes: List<Vignette> = listOf(
        Vignette("CH", "CH annual vignette", 365, 40.0, "CHF"),
        Vignette("AT", "AT 1-day vignette", 1, 9.60, "EUR"),
        Vignette("AT", "AT 10-day vignette", 10, 12.80, "EUR"),
        Vignette("AT", "AT annual vignette", 365, 106.80, "EUR"),
        Vignette("SI", "SI 1-day vignette", 1, 8.10, "EUR"),
        Vignette("SI", "SI 10-day vignette", 10, 10.80, "EUR"),
        Vignette("CZ", "CZ 1-day vignette", 1, 200.0, "CZK"),
        Vignette("CZ", "CZ 10-day vignette", 10, 270.0, "CZK"),
        Vignette("CZ", "CZ 30-day vignette", 30, 430.0, "CZK"),
    )

    val countryCodes: List<String> = vignettes.map { it.countryCode }.distinct()

    fun cheapestCovering(countryCode: String, days: Int): Choice? {
        val coveredDays = days.coerceAtLeast(1)
        return vignettes.filter { it.countryCode == countryCode }
            .map { Choice(it, ceil(coveredDays / it.days.toDouble()).toInt()) }
            .minByOrNull { it.total }
    }

    // Rough bundled rates (2026) for converting vignette prices into the app currency.
    private val chfPer = mapOf("CHF" to 1.0, "EUR" to 0.94, "CZK" to 0.038)

    fun convert(amount: Double, from: String, to: String): Double {
        val fromRate = chfPer[from] ?: 1.0
        val toRate = chfPer[to] ?: 1.0
        return amount * fromRate / toRate
    }
}
