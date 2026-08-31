package com.nuelto.camperexperience.ui

import com.nuelto.camperexperience.data.model.StopLeg
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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

    @Test
    fun `distance stays in metres below a kilometre and flips at 1000`() {
        assertEquals("0 m", formatDistance(0))
        assertEquals("999 m", formatDistance(999))
        assertEquals("1 km", formatDistance(1000))
    }

    @Test
    fun `kilometres round to the nearest whole one`() {
        assertEquals("1 km", formatDistance(1499))
        assertEquals("2 km", formatDistance(1500))
        assertEquals("124 km", formatDistance(124_400))
    }

    @Test
    fun `a duration under an hour is minutes only`() {
        assertEquals("0 min", formatDuration(0))
        assertEquals("45 min", formatDuration(2700))
        assertEquals("59 min", formatDuration(3540))
    }

    @Test
    fun `whole hours drop the minutes`() {
        assertEquals("1 h", formatDuration(3600))
        assertEquals("2 h", formatDuration(7200))
    }

    @Test
    fun `hours carry their leftover minutes`() {
        assertEquals("1 h 1 min", formatDuration(3660))
        assertEquals("1 h 45 min", formatDuration(6300))
    }

    @Test
    fun `seconds round to the nearest minute`() {
        assertEquals("0 min", formatDuration(29))
        assertEquals("1 min", formatDuration(30))
        assertEquals("1 min", formatDuration(89))
        assertEquals("2 min", formatDuration(90))
    }

    private fun leg(meters: Int, seconds: Int, ascent: Int? = null) =
        StopLeg(distanceMeters = meters, durationSeconds = seconds, ascentMeters = ascent)

    @Test
    fun `a drive joins distance and duration`() {
        assertEquals("124 km · 1 h 45 min", formatDrive(leg(124_400, 6300)))
        assertEquals("800 m · 12 min", formatDrive(leg(800, 700)))
    }

    @Test
    fun `the drive is distance and time only, never the climb`() {
        // Cumulative ascent beside a place name reads as the height of the place.
        assertEquals("10 km · 12 min", formatDrive(leg(10_000, 700, 1450)))
        assertEquals("10 km · 12 min", formatDrive(leg(10_000, 700, null)))
    }
    private val midday = LocalDateTime.of(2026, 9, 1, 15, 47)
    private val evening = LocalDateTime.of(2026, 9, 1, 22, 30)

    /**
     * The clock time as the platform writes it. Spelling it out here would pin CLDR's
     * spacing (it puts U+202F before "PM"), not our arithmetic — which is the part worth
     * asserting: that the arrival really is now plus the drive.
     */
    private fun at(hour: Int, minute: Int): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(LocalTime.of(hour, minute))

    @Test
    fun `the live drive says how far, how long, and when you get there`() {
        assertEquals(
            "47 km · 55 min from here · arrive ${at(16, 42)}",
            formatDriveFromHere(47_000, 3_300, midday),
        )
        assertEquals(
            "800 m · 2 min from here · arrive ${at(15, 49)}",
            formatDriveFromHere(800, 100, midday),
        )
    }

    @Test
    fun `arrival is now plus the drive`() {
        assertEquals("arrive ${at(16, 42)}", formatArrival(3_300, midday))
        assertEquals("arrive ${at(15, 47)}", formatArrival(0, midday))
    }

    @Test
    fun `an arrival past midnight names the day`() {
        // Four hours on: the same clock time tomorrow would otherwise read as this morning.
        assertEquals("arrive ${at(2, 30)} tomorrow", formatArrival(4 * 3_600, evening))
        // Just before midnight is still today.
        assertEquals("arrive ${at(23, 59)}", formatArrival(89 * 60, evening))
    }

    @Test
    fun `an arrival further out names the date`() {
        assertEquals(
            "arrive ${at(22, 30)} on ${formatDate(LocalDate.of(2026, 9, 3))}",
            formatArrival(2 * 24 * 3_600, evening),
        )
    }

}
