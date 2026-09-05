package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopState
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StayTest {

    private val today = LocalDate.of(2026, 9, 4)
    private val pin = LatLng(46.1591, 8.7853)

    private fun stop(
        id: String,
        order: Int,
        arrival: LocalDate = today,
        nights: Int = 3,
        state: StopState = StopState.PLANNED,
        location: LatLng? = pin,
        time: LocalTime? = null,
    ) = Stop(id = id, orderIndex = order, arrivalDate = arrival, nights = nights, state = state, location = location, arrivalTime = time)

    /** [meters] north of [from], close enough that a degree of latitude is a straight ruler. */
    private fun north(from: LatLng, meters: Double) = LatLng(from.latitude + meters / 111_195.0, from.longitude)

    // --- checkIn -------------------------------------------------------------

    @Test
    fun `check-in stamps the day and the minute, and leaves an on-time plan alone`() {
        val stops = listOf(stop("a", 0), stop("b", 1, arrival = today.plusDays(3)))
        val written = Stay.checkIn(stops, "a", today.atTime(16, 42, 37))
        val a = written.single()
        assertEquals(StopState.DONE, a.state)
        assertEquals(today, a.arrivalDate)
        assertEquals(LocalTime.of(16, 42), a.arrivalTime)
    }

    @Test
    fun `a late check-in moves the plan behind it back, an early one forward`() {
        val stops = listOf(stop("a", 0), stop("b", 1, arrival = today.plusDays(3)), stop("c", 2, arrival = today.plusDays(5), state = StopState.SKIPPED))
        val late = Stay.checkIn(stops, "a", today.plusDays(1).atTime(9, 0))
        assertEquals(listOf("a", "b"), late.map { it.id })
        assertEquals(today.plusDays(1), late[0].arrivalDate)
        assertEquals(today.plusDays(4), late[1].arrivalDate)
        val early = Stay.checkIn(stops, "a", today.minusDays(1).atTime(9, 0))
        assertEquals(today.minusDays(1), early[0].arrivalDate)
        assertEquals(today.plusDays(2), early[1].arrivalDate)
    }

    @Test
    fun `only a planned stop can be checked in, and only once`() {
        val done = stop("a", 0, state = StopState.DONE, time = LocalTime.of(16, 0))
        assertTrue(Stay.checkIn(listOf(done), "a", today.atTime(17, 0)).isEmpty())
        assertTrue(Stay.checkIn(listOf(stop("a", 0, state = StopState.SKIPPED)), "a", today.atTime(17, 0)).isEmpty())
        assertTrue(Stay.checkIn(listOf(stop("a", 0)), "nope", today.atTime(17, 0)).isEmpty())
    }

    // --- undoCheckIn ---------------------------------------------------------

    @Test
    fun `undoing a check-in plans the stop again for the day it was stamped with`() {
        val done = stop("a", 0, arrival = today.plusDays(1), state = StopState.DONE, time = LocalTime.of(16, 0))
        val undone = Stay.undoCheckIn(listOf(done, stop("b", 1, arrival = today.plusDays(4))), "a").single()
        assertEquals(StopState.PLANNED, undone.state)
        assertNull(undone.arrivalTime)
        assertEquals(today.plusDays(1), undone.arrivalDate)
        assertEquals(3, undone.nights)
        assertTrue(Stay.undoCheckIn(listOf(stop("a", 0)), "a").isEmpty())
        assertTrue(Stay.undoCheckIn(listOf(done), "nope").isEmpty())
    }

    // --- checkOut ------------------------------------------------------------

    @Test
    fun `check-out keeps the nights slept and pulls the plan behind it forward`() {
        val stops = listOf(
            stop("a", 0, arrival = today.minusDays(1), state = StopState.DONE),
            stop("b", 1, arrival = today.plusDays(2)),
            stop("c", 2, arrival = today.plusDays(4)),
        )
        val written = Stay.checkOut(stops, "a", today)
        assertEquals(listOf("a", "b", "c"), written.map { it.id })
        assertEquals(1, written[0].nights)
        assertEquals(today, written[1].arrivalDate)
        assertEquals(today.plusDays(2), written[2].arrivalDate)
    }

    @Test
    fun `checking out on the day you arrived leaves no nights`() {
        val written = Stay.checkOut(listOf(stop("a", 0, state = StopState.DONE), stop("b", 1, arrival = today.plusDays(3))), "a", today)
        assertEquals(0, written[0].nights)
        assertEquals(today, written[1].arrivalDate)
    }

    @Test
    fun `a check-out never lengthens a stay, and needs a checked-in stop`() {
        val done = stop("a", 0, arrival = today.minusDays(3), state = StopState.DONE)
        assertTrue(Stay.checkOut(listOf(done), "a", today).isEmpty())
        assertTrue(Stay.checkOut(listOf(done), "a", today.plusDays(2)).isEmpty())
        assertTrue(Stay.checkOut(listOf(stop("a", 0, arrival = today.minusDays(1))), "a", today).isEmpty())
        assertTrue(Stay.checkOut(listOf(done), "nope", today).isEmpty())
    }

    // --- near ----------------------------------------------------------------

    @Test
    fun `near is inside five hundred metres of the pin, on or after the day`() {
        val a = stop("a", 0)
        assertTrue(Stay.near(north(pin, 499.0), a, today))
        assertFalse(Stay.near(north(pin, 501.0), a, today))
        assertTrue(Stay.near(pin, a, today.plusDays(1)))
        assertFalse(Stay.near(pin, a, today.minusDays(1)))
        assertFalse(Stay.near(pin, stop("a", 0, location = null), today))
    }

    // --- dwell ---------------------------------------------------------------

    private val zone = ZoneId.of("Europe/Zurich")
    private val at = today.atTime(16, 0).atZone(zone)

    @Test
    fun `a dwell grows with fixes at the same stop, and checks in after ten minutes`() {
        val first = Stay.dwell(null, "a", at)
        assertEquals(Stay.Dwell("a", at, at), first)
        val later = Stay.dwell(first, "a", at.plusMinutes(5))
        assertEquals(Stay.Dwell("a", at, at.plusMinutes(5)), later)
        assertFalse(Stay.dwell(later, "a", at.plusMinutes(9)).checksIn)
        assertTrue(Stay.dwell(later, "a", at.plusMinutes(10)).checksIn)
    }

    @Test
    fun `another stop, or a gap longer than three fixes, is a fresh dwell`() {
        val first = Stay.dwell(null, "a", at)
        assertEquals(Stay.Dwell("b", at.plusMinutes(1), at.plusMinutes(1)), Stay.dwell(first, "b", at.plusMinutes(1)))
        assertEquals(at, Stay.dwell(first, "a", at.plusMinutes(6)).since)
        assertEquals(at.plusMinutes(7), Stay.dwell(first, "a", at.plusMinutes(7)).since)
    }

    @Test
    fun `left is a kilometre from the pin, or no pin at all`() {
        val a = stop("a", 0)
        assertFalse(Stay.left(north(pin, 999.0), a))
        assertTrue(Stay.left(north(pin, 1_001.0), a))
        assertTrue(Stay.left(pin, stop("a", 0, location = null)))
    }

    @Test
    fun `the minutes of a dwell are real minutes when the clocks go back`() {
        val before = LocalDateTime.of(2026, 10, 25, 2, 55).atZone(zone)
        val after = before.plus(Duration.ofMinutes(10))
        assertEquals(LocalTime.of(2, 5), after.toLocalTime())
        val halfway = Stay.dwell(Stay.dwell(null, "a", before), "a", before.plus(Duration.ofMinutes(5)))
        assertTrue(Stay.dwell(halfway, "a", after).checksIn)
    }

    // --- progress ------------------------------------------------------------

    @Test
    fun `progress counts the nights slept and the nights ahead`() {
        val a = stop("a", 0, arrival = today.minusDays(1), state = StopState.DONE)
        assertEquals(Stay.Progress(0, 3, today.plusDays(2)), Stay.progress(a, today.minusDays(1)))
        assertEquals(Stay.Progress(1, 2, today.plusDays(2)), Stay.progress(a, today))
        assertEquals(Stay.Progress(3, 0, today.plusDays(2)), Stay.progress(a, today.plusDays(2)))
        // Never past the stay, never before it.
        assertEquals(Stay.Progress(3, 0, today.plusDays(2)), Stay.progress(a, today.plusDays(9)))
        assertEquals(Stay.Progress(0, 3, today.plusDays(2)), Stay.progress(a, today.minusDays(9)))
    }
}
