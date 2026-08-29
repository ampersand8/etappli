package com.nuelto.camperexperience.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VignetteTableTest {

    @Test
    fun `switzerland only has the annual vignette`() {
        val choice = VignetteTable.cheapestCovering("CH", 5)!!
        assertEquals("CH annual vignette", choice.label)
        assertEquals(40.0, choice.total, 1e-9)
        assertEquals("CHF", choice.vignette.currency)
    }

    @Test
    fun `austria picks the cheapest bucket covering the days`() {
        assertEquals(9.60, VignetteTable.cheapestCovering("AT", 1)!!.total, 1e-9)
        // 12 days: 2× 10-day (25.60) beats 12× 1-day and the annual.
        val twelve = VignetteTable.cheapestCovering("AT", 12)!!
        assertEquals(2, twelve.count)
        assertEquals(25.60, twelve.total, 1e-9)
        assertEquals("2× AT 10-day vignette", twelve.label)
        // A long stay falls back to the annual.
        assertEquals(106.80, VignetteTable.cheapestCovering("AT", 120)!!.total, 1e-9)
    }

    @Test
    fun `day counts are floored at one`() {
        assertEquals(9.60, VignetteTable.cheapestCovering("AT", 0)!!.total, 1e-9)
    }

    @Test
    fun `unknown countries have no vignette`() {
        assertNull(VignetteTable.cheapestCovering("DE", 5))
    }

    @Test
    fun `conversion uses bundled rates and is identity for same currency`() {
        assertEquals(12.80 * 0.94, VignetteTable.convert(12.80, "EUR", "CHF"), 1e-9)
        assertEquals(270.0 * 0.038, VignetteTable.convert(270.0, "CZK", "CHF"), 1e-9)
        assertEquals(40.0, VignetteTable.convert(40.0, "CHF", "CHF"), 1e-9)
        // Unknown currencies pass through at 1:1 rather than failing.
        assertEquals(0.94, VignetteTable.convert(1.0, "EUR", "XXX"), 1e-9)
    }

    @Test
    fun `table vintage and country list are exposed for the UI`() {
        assertEquals(2026, VignetteTable.YEAR)
        assertEquals(listOf("CH", "AT", "SI", "CZ"), VignetteTable.countryCodes)
    }
}
