package com.nuelto.camperexperience.ui

import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FormatTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }


    @Test
    fun `formats amount with requested currency`() {
        assertEquals("$1,234.50", formatCurrency(1234.5, "USD"))
        assertEquals("CHF10.00", formatCurrency(10.0, "CHF"))
    }

    @Test
    fun `invalid currency code falls back to locale currency`() {
        assertEquals("$10.00", formatCurrency(10.0, "NOPE"))
    }

    @Test
    fun `date format contains day month and year`() {
        val formatted = formatDate(LocalDate.of(2026, 9, 12))
        assertTrue(formatted, formatted.contains("12"))
        assertTrue(formatted, formatted.contains("2026"))
    }

    @Test
    fun `date range joins start and end`() {
        val start = LocalDate.of(2026, 9, 12)
        val end = LocalDate.of(2026, 9, 21)
        assertEquals("${formatDate(start)} – ${formatDate(end)}", formatDateRange(start, end))
    }

    @Test
    fun `open ended range says ongoing`() {
        val start = LocalDate.of(2026, 9, 12)
        assertEquals("${formatDate(start)} – ongoing", formatDateRange(start, null))
    }

    @Test
    fun `an open-ended plan reads from its start, not ongoing`() {
        val start = LocalDate.of(2027, 6, 10)
        val plan = Trip(startDate = start, status = TripStatus.PLANNED)
        assertEquals("from ${formatDate(start)}", formatTripDates(plan))
    }

    @Test
    fun `active and done trips keep the date range`() {
        val start = LocalDate.of(2026, 9, 12)
        val end = LocalDate.of(2026, 9, 21)
        assertEquals(
            "${formatDate(start)} – ongoing",
            formatTripDates(Trip(startDate = start, status = TripStatus.ACTIVE)),
        )
        assertEquals(
            "${formatDate(start)} – ${formatDate(end)}",
            formatTripDates(Trip(startDate = start, endDate = end, status = TripStatus.DONE)),
        )
        // A plan with a chosen end date shows the range too.
        assertEquals(
            "${formatDate(start)} – ${formatDate(end)}",
            formatTripDates(Trip(startDate = start, endDate = end, status = TripStatus.PLANNED)),
        )
    }
}
