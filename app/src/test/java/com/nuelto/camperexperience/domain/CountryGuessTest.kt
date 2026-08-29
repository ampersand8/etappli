package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import org.junit.Assert.assertEquals
import org.junit.Test

class CountryGuessTest {

    private val luzern = Stop(id = "lu", location = LatLng(47.05, 8.31), nights = 2)
    private val innsbruck = Stop(id = "ibk", location = LatLng(47.26, 11.39), nights = 3)
    private val ljubljana = Stop(id = "lj", location = LatLng(46.06, 14.51), nights = 1)

    @Test
    fun `countries are detected from stop coordinates in table order`() {
        assertEquals(listOf("CH"), CountryGuess.vignetteCountries(listOf(luzern)))
        assertEquals(
            listOf("CH", "AT", "SI"),
            CountryGuess.vignetteCountries(listOf(ljubljana, innsbruck, luzern)),
        )
    }

    @Test
    fun `stops without location or outside all boxes suggest nothing`() {
        val nowhere = Stop(id = "x", location = LatLng(60.0, 25.0))
        assertEquals(emptyList<String>(), CountryGuess.vignetteCountries(listOf(nowhere, Stop(id = "y"))))
    }

    @Test
    fun `skipped stops do not suggest countries`() {
        val skipped = innsbruck.copy(state = StopState.SKIPPED)
        assertEquals(listOf("CH"), CountryGuess.vignetteCountries(listOf(luzern, skipped)))
    }

    @Test
    fun `days in a country sum its stop nights`() {
        val zellAmSee = Stop(id = "zs", location = LatLng(47.32, 12.79), nights = 2)
        assertEquals(5, CountryGuess.daysIn("AT", listOf(innsbruck, zellAmSee, luzern)))
    }

    @Test
    fun `a zero-night visit still counts as one transit day`() {
        val viewpoint = innsbruck.copy(nights = 0)
        assertEquals(1, CountryGuess.daysIn("AT", listOf(viewpoint)))
    }

    @Test
    fun `overlapping boxes near borders over-suggest, which is fine - confirm-only`() {
        // Basel-ish: inside the CH box only, despite being near two borders.
        val basel = Stop(id = "bs", location = LatLng(47.56, 7.59), nights = 1)
        assertEquals(listOf("CH"), CountryGuess.vignetteCountries(listOf(basel)))
    }
}
