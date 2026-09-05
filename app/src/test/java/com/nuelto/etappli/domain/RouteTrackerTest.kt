package com.nuelto.etappli.domain

import app.cash.turbine.test
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopLeg
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.TransitRide
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTrackerTest {

    private val today = LocalDate.of(2026, 9, 4)
    private val zone = ZoneId.of("Europe/Zurich")
    private var now = today.atTime(16, 0).atZone(zone)
    private val here = LatLng(46.8709, 8.2492)
    private val there = LatLng(46.1591, 8.7853)
    private val repository = InMemoryTripRepository(seed = false)
    private val asked = mutableListOf<Pair<LatLng, LatLng>>()
    private var answer: RoutedLeg? = RoutedLeg("", 90_000, 5_400)

    private fun tracker() = RouteTracker(repository, { from, to -> asked += from to to; answer }, { now })

    /** A fix [minutes] after the last one. */
    private suspend fun RouteTracker.fixAfter(minutes: Long, point: LatLng, tripId: String? = null) {
        now += Duration.ofMinutes(minutes)
        fix(point, tripId)
    }

    /** [meters] north of [from], close enough that a degree of latitude is a straight ruler. */
    private fun north(from: LatLng, meters: Double) =
        LatLng(from.latitude + meters / 111_195.0, from.longitude)

    private suspend fun trip(id: String, status: TripStatus = TripStatus.ACTIVE, start: LocalDate = today) =
        repository.upsertTrip(Trip(id = id, name = id, startDate = start, status = status))

    private suspend fun stop(
        tripId: String,
        id: String,
        order: Int,
        location: LatLng? = there,
        state: StopState = StopState.PLANNED,
        nights: Int = 1,
        leg: StopLeg? = null,
        arrival: LocalDate = today,
    ) = repository.upsertStop(
        Stop(
            id = id, tripId = tripId, name = id, orderIndex = order, location = location,
            state = state, nights = nights, arrivalDate = arrival, leg = leg,
        ),
    )

    private suspend fun stop(tripId: String, id: String) = repository.stops(tripId).first().single { it.id == id }

    private suspend fun track(tripId: String, id: String) = stop(tripId, id).track

    // --- heading -------------------------------------------------------------

    @Test
    fun `no active trip, no heading`() = runTest {
        tracker().heading().test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        trip("p", TripStatus.PLANNED)
        stop("p", "s1", 0)
        assertNull(tracker().heading().first())
    }

    @Test
    fun `an active trip heads for its first planned stop`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        stop("t", "s2", 1)
        val heading = tracker().heading().first()!!
        assertEquals("t", heading.tripId)
        assertEquals("s1", heading.stop.id)
        assertEquals(there, heading.target)
        assertTrue(heading.underway)
    }

    @Test
    fun `mid-stay there is no heading`() = runTest {
        trip("t")
        stop("t", "s1", 0, state = StopState.DONE, nights = 2)
        stop("t", "s2", 1)
        assertNull(tracker().heading().first())
    }

    @Test
    fun `of two active trips the one started last is followed`() = runTest {
        trip("old", start = today.minusDays(3))
        stop("old", "o1", 0)
        trip("new", start = today.minusDays(1))
        stop("new", "n1", 0)
        val heading = tracker().heading().first()!!
        assertEquals("new", heading.tripId)
        assertTrue(heading.underway)
    }

    @Test
    fun `before the start date the drive is not underway`() = runTest {
        trip("t", start = today.plusDays(1))
        stop("t", "s1", 0)
        val heading = tracker().heading().first()!!
        assertEquals("s1", heading.stop.id)
        assertFalse(heading.underway)
    }

    @Test
    fun `the target is where the vehicle is left for the ride up`() = runTest {
        trip("t")
        stop("t", "s1", 0, location = here, state = StopState.DONE, nights = 0)
        val parked = LatLng(46.5, 8.5)
        stop(
            "t", "s2", 1,
            leg = StopLeg(from = here, to = there, distanceMeters = 1, rideAfter = TransitRide(parked = parked)),
        )
        assertEquals(parked, tracker().heading().first()!!.target)
    }

    @Test
    fun `the heading re-emits on check-in`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        tracker().heading().test {
            assertEquals("s1", awaitItem()!!.stop.id)
            repository.upsertStop(stop("t", "s1").copy(state = StopState.DONE))
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- fix -----------------------------------------------------------------

    @Test
    fun `a fix goes onto the heading stop's track and buys a route`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(here)
        assertEquals(listOf(here), track("t", "s1"))
        assertEquals(listOf(here to there), asked)
        assertEquals(DriveFromHere(here, there, 90_000, 5_400), tracker.state.value.drive)
        assertEquals(here, tracker.state.value.lastFix)
    }

    @Test
    fun `a fix within thirty metres of the last is not recorded but is the last fix`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(here)
        tracker.fix(north(here, 20.0))
        assertEquals(listOf(here), track("t", "s1"))
        assertEquals(north(here, 20.0), tracker.state.value.lastFix)
        tracker.fix(north(here, 40.0))
        assertEquals(listOf(here, north(here, 40.0)), track("t", "s1"))
    }

    @Test
    fun `an arrival fix is never recorded and clears the distance`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(here)
        assertNotNull(tracker.state.value.drive)
        val arrival = north(there, 50.0)
        tracker.fix(arrival)
        assertEquals(listOf(here), track("t", "s1"))
        assertNull(tracker.state.value.drive)
        assertEquals(arrival, tracker.state.value.lastFix)
        assertEquals(1, asked.size)
    }

    @Test
    fun `nothing after the arrival is recorded until the heading changes`() = runTest {
        trip("t")
        stop("t", "s1", 0, nights = 0)
        stop("t", "s2", 1, location = here)
        val tracker = tracker()
        tracker.fix(here)
        tracker.fix(north(there, 50.0))
        // The run to the shop from the campsite, and the way back: not the drive.
        tracker.fix(north(there, 900.0))
        tracker.fix(north(there, 60.0))
        assertEquals(listOf(here), track("t", "s1"))
        assertEquals(1, asked.size)
        // Checked in and heading on: recording resumes for the next stop.
        repository.upsertStop(stop("t", "s1").copy(state = StopState.DONE))
        val onwards = north(there, 400.0)
        tracker.fix(onwards)
        assertEquals(listOf(onwards), track("t", "s2"))
        assertEquals(2, asked.size)
    }

    @Test
    fun `the heading follows a tour being started and re-dated, not a note being edited`() = runTest {
        repository.upsertTrip(Trip(id = "t", name = "t", startDate = today, status = TripStatus.PLANNED))
        stop("t", "s1", 0)
        tracker().heading().test {
            assertNull(awaitItem())
            val trip = repository.trip("t").first()!!
            repository.upsertTrip(trip.copy(status = TripStatus.ACTIVE))
            assertTrue(awaitItem()!!.underway)
            repository.upsertTrip(trip.copy(status = TripStatus.ACTIVE, startDate = today.plusDays(1)))
            assertFalse(awaitItem()!!.underway)
            repository.upsertTrip(trip.copy(status = TripStatus.ACTIVE, startDate = today.plusDays(1), notes = "packed"))
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `before the start date a fix is routed but not recorded`() = runTest {
        trip("t", start = today.plusDays(1))
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(here)
        assertTrue(track("t", "s1").isEmpty())
        assertEquals(listOf(here to there), asked)
        assertEquals(90_000, tracker.state.value.drive!!.distanceMeters)
    }

    @Test
    fun `a fix for another trip records nothing`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(here, tripId = "other")
        assertTrue(track("t", "s1").isEmpty())
        assertTrue(asked.isEmpty())
        assertNull(tracker.state.value.lastFix)
        tracker.fix(here, tripId = "t")
        assertEquals(listOf(here), track("t", "s1"))
        assertEquals(1, asked.size)
    }

    @Test
    fun `no route is bought again within two kilometres`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(here)
        tracker.fix(north(here, 1_999.0))
        assertEquals(1, asked.size)
        assertEquals(here, tracker.state.value.drive!!.from)
        tracker.fix(north(here, 2_001.0))
        assertEquals(2, asked.size)
        assertEquals(north(here, 2_001.0), tracker.state.value.drive!!.from)
    }

    @Test
    fun `a failed ask is not repeated until two kilometres on, and a due one that fails clears the answer`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        answer = null
        val tracker = tracker()
        tracker.fix(here)
        tracker.fix(north(here, 1_000.0))
        assertEquals(1, asked.size)
        assertNull(tracker.state.value.drive)
        answer = RoutedLeg("", 80_000, 5_000)
        tracker.fix(north(here, 2_500.0))
        assertEquals(2, asked.size)
        assertEquals(80_000, tracker.state.value.drive!!.distanceMeters)
        answer = null
        tracker.fix(north(here, 5_000.0))
        assertEquals(3, asked.size)
        assertNull(tracker.state.value.drive)
    }

    @Test
    fun `a stop no road reaches is recorded but never routed`() = runTest {
        trip("t")
        stop("t", "s1", 0, location = here, state = StopState.DONE, nights = 0)
        stop("t", "s2", 1, leg = StopLeg(from = here, to = there, distanceMeters = 0))
        val tracker = tracker()
        val fix = north(here, 500.0)
        tracker.fix(fix)
        assertEquals(listOf(fix), track("t", "s2"))
        assertTrue(asked.isEmpty())
        assertNull(tracker.state.value.drive)
        assertEquals(fix, tracker.state.value.lastFix)
    }

    @Test
    fun `with no heading a fix clears the distance and records nothing`() = runTest {
        trip("t")
        stop("t", "s1", 0, nights = 2)
        val tracker = tracker()
        tracker.fix(here)
        assertNotNull(tracker.state.value.drive)
        repository.upsertStop(stop("t", "s1").copy(state = StopState.DONE))
        val later = north(here, 3_000.0)
        tracker.fix(later)
        assertNull(tracker.state.value.drive)
        assertEquals(later, tracker.state.value.lastFix)
        assertEquals(listOf(here), track("t", "s1"))
        assertEquals(1, asked.size)
    }

    // --- check-in ------------------------------------------------------------

    @Test
    fun `ten minutes at the stop check you in, stamped with the first fix there`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        stop("t", "s2", 1, arrival = today.plusDays(1))
        val tracker = tracker()
        tracker.fix(here)
        // Inside the 150 m that already count as arrived: the dwell must run before that return.
        val atStop = north(there, 50.0)
        tracker.fixAfter(5, atStop)
        tracker.fixAfter(5, atStop)
        tracker.fixAfter(4, atStop)
        assertEquals(StopState.PLANNED, stop("t", "s1").state)
        tracker.fixAfter(1, atStop)
        val s1 = stop("t", "s1")
        assertEquals(StopState.DONE, s1.state)
        assertEquals(today, s1.arrivalDate)
        assertEquals(LocalTime.of(16, 5), s1.arrivalTime)
        assertEquals(today.plusDays(1), stop("t", "s2").arrivalDate)
        assertNull(tracker.state.value.drive)
        assertEquals(listOf(here), track("t", "s1"))
        assertEquals(1, asked.size)
    }

    @Test
    fun `a fix away from the stop starts the dwell over, one for another trip is not a move`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        tracker.fixAfter(5, north(there, 1_200.0))
        tracker.fixAfter(5, north(there, 100.0))
        tracker.fixAfter(4, north(there, 400.0))
        tracker.fixAfter(5, north(there, 400.0))
        assertEquals(StopState.PLANNED, stop("t", "s1").state)
        tracker.fixAfter(1, north(there, 400.0))
        assertEquals(LocalTime.of(16, 10), stop("t", "s1").arrivalTime)

        repository.upsertStops(Stay.undoCheckIn(repository.stops("t").first(), "s1"))
        tracker.fixAfter(1, north(there, 1_200.0))
        tracker.fixAfter(1, north(there, 100.0))
        tracker.fixAfter(2, north(there, 100.0))
        tracker.fixAfter(1, north(there, 100.0), tripId = "other")
        tracker.fixAfter(1, north(there, 100.0))
        assertEquals(StopState.PLANNED, stop("t", "s1").state)
        tracker.fixAfter(4, north(there, 100.0))
        tracker.fixAfter(2, north(there, 100.0))
        assertEquals(LocalTime.of(16, 22), stop("t", "s1").arrivalTime)
    }

    @Test
    fun `a fix between near and left neither adds to the stay nor breaks it`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        tracker.fixAfter(2, north(there, 700.0))
        tracker.fixAfter(2, north(there, 100.0))
        tracker.fixAfter(2, north(there, 700.0))
        tracker.fixAfter(2, north(there, 100.0))
        // Ten minutes on, but this one is not at the stop.
        tracker.fixAfter(2, north(there, 700.0))
        assertEquals(StopState.PLANNED, stop("t", "s1").state)
        tracker.fixAfter(1, north(there, 100.0))
        assertEquals(LocalTime.of(16, 0), stop("t", "s1").arrivalTime)
    }

    @Test
    fun `too long on the edge of the site is a gap like any other`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        tracker.fixAfter(2, north(there, 700.0))
        tracker.fixAfter(2, north(there, 700.0))
        tracker.fixAfter(3, north(there, 700.0))
        tracker.fixAfter(1, north(there, 100.0))
        tracker.fixAfter(2, north(there, 100.0))
        assertEquals(StopState.PLANNED, stop("t", "s1").state)
        tracker.fixAfter(4, north(there, 100.0))
        tracker.fixAfter(4, north(there, 100.0))
        assertEquals(LocalTime.of(16, 8), stop("t", "s1").arrivalTime)
    }

    @Test
    fun `fixes at the stop further apart than the fix gap are two visits, not a stay`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        tracker.fixAfter(2, north(there, 100.0))
        tracker.fixAfter(7, north(there, 100.0))
        tracker.fixAfter(3, north(there, 100.0))
        assertEquals(StopState.PLANNED, stop("t", "s1").state)
        tracker.fixAfter(4, north(there, 100.0))
        tracker.fixAfter(3, north(there, 100.0))
        assertEquals(LocalTime.of(16, 9), stop("t", "s1").arrivalTime)
    }

    @Test
    fun `the ten minutes are real minutes on the night the clocks go back`() = runTest {
        val night = LocalDate.of(2026, 10, 25)
        now = LocalDateTime.of(2026, 10, 25, 2, 55).atZone(zone)
        trip("t", start = night)
        stop("t", "s1", 0, arrival = night)
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        tracker.fixAfter(4, north(there, 100.0))
        tracker.fixAfter(4, north(there, 100.0))
        assertEquals(StopState.PLANNED, stop("t", "s1").state)
        tracker.fixAfter(2, north(there, 100.0))
        assertEquals(LocalTime.of(2, 55), stop("t", "s1").arrivalTime)
        assertEquals("02:05", now.toLocalTime().toString())
    }

    @Test
    fun `a late arrival is still an arrival, and the plan behind it moves`() = runTest {
        trip("t", start = today.minusDays(1))
        stop("t", "s1", 0, arrival = today.minusDays(1))
        stop("t", "s2", 1, arrival = today)
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        val s1 = stop("t", "s1")
        assertEquals(StopState.DONE, s1.state)
        assertEquals(today, s1.arrivalDate)
        assertEquals(today.plusDays(1), stop("t", "s2").arrivalDate)
    }

    @Test
    fun `a day early, before the tour, or at a stop with no pin, standing there is not arriving`() = runTest {
        trip("early")
        stop("early", "e", 0, arrival = today.plusDays(1))
        val early = tracker()
        early.fix(north(there, 100.0))
        early.fixAfter(5, north(there, 100.0))
        early.fixAfter(5, north(there, 100.0))
        assertEquals(StopState.PLANNED, stop("early", "e").state)
        repository.deleteTrip("early")

        trip("ahead", start = today.plusDays(1))
        stop("ahead", "a", 0)
        val ahead = tracker()
        ahead.fix(north(there, 100.0))
        ahead.fixAfter(5, north(there, 100.0))
        ahead.fixAfter(5, north(there, 100.0))
        assertEquals(StopState.PLANNED, stop("ahead", "a").state)
        repository.deleteTrip("ahead")

        trip("pinless")
        stop("pinless", "p", 0, location = null)
        val pinless = tracker()
        pinless.fix(north(there, 100.0))
        pinless.fixAfter(5, north(there, 100.0))
        pinless.fixAfter(5, north(there, 100.0))
        assertEquals(StopState.PLANNED, stop("pinless", "p").state)
    }

    @Test
    fun `the first fix at the stop is the arrival even across midnight`() = runTest {
        now = today.atTime(23, 55).atZone(zone)
        trip("t")
        stop("t", "s1", 0)
        stop("t", "s2", 1, arrival = today.plusDays(1))
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        val s1 = stop("t", "s1")
        assertEquals(today, s1.arrivalDate)
        assertEquals(LocalTime.of(23, 55), s1.arrivalTime)
        assertEquals(today.plusDays(1), stop("t", "s2").arrivalDate)
    }

    @Test
    fun `a visit checked in hands the heading on, and the next stop needs its own ten minutes`() = runTest {
        trip("t")
        stop("t", "s1", 0, nights = 0)
        stop("t", "s2", 1, location = north(there, 200.0))
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        assertEquals(StopState.DONE, stop("t", "s1").state)
        assertEquals(StopState.PLANNED, stop("t", "s2").state)
        assertEquals("s2", tracker.heading().first()!!.stop.id)
        tracker.fixAfter(2, north(there, 150.0))
        tracker.fixAfter(4, north(there, 150.0))
        tracker.fixAfter(4, north(there, 150.0))
        assertEquals(StopState.PLANNED, stop("t", "s2").state)
        tracker.fixAfter(2, north(there, 150.0))
        assertEquals(LocalTime.of(16, 12), stop("t", "s2").arrivalTime)
    }

    @Test
    fun `a check-in taken back is not made again until a fix has left the stop`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        assertEquals(StopState.DONE, stop("t", "s1").state)
        // Taken back on the timeline: the tracker holds itself off after its own check-in.
        repository.upsertStops(Stay.undoCheckIn(repository.stops("t").first(), "s1"))
        tracker.fixAfter(2, north(there, 100.0))
        // A wander to the far edge of the site is not leaving.
        tracker.fixAfter(2, north(there, 700.0))
        tracker.fixAfter(2, north(there, 100.0))
        tracker.fixAfter(4, north(there, 100.0))
        tracker.fixAfter(4, north(there, 100.0))
        tracker.fixAfter(2, north(there, 100.0))
        assertEquals(StopState.PLANNED, stop("t", "s1").state)
        // Gone and back: the stop counts again.
        tracker.fixAfter(3, north(there, 1_200.0))
        tracker.fixAfter(2, north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        assertEquals(LocalTime.of(16, 31), stop("t", "s1").arrivalTime)
    }

    @Test
    fun `held off, a stop checked in by hand and taken back is left alone while you stay`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        repository.upsertStops(Stay.checkIn(repository.stops("t").first(), "s1", now.toLocalDateTime()))
        repository.upsertStops(Stay.undoCheckIn(repository.stops("t").first(), "s1"))
        tracker.holdOff("s1")
        tracker.fixAfter(4, north(there, 100.0))
        tracker.fixAfter(4, north(there, 100.0))
        tracker.fixAfter(4, north(there, 100.0))
        assertEquals(StopState.PLANNED, stop("t", "s1").state)
        tracker.fixAfter(1, north(there, 1_200.0))
        tracker.fixAfter(1, north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        assertEquals(StopState.DONE, stop("t", "s1").state)
    }

    @Test
    fun `taking a check-in back puts the drive to the stop on again`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        assertTrue(asked.isEmpty())
        assertTrue(track("t", "s1").isEmpty())
        tracker.holdOff("s1")
        // The pin was wrong: what follows is a drive, routed and recorded.
        repository.upsertStop(stop("t", "s1").copy(location = here))
        val fix = north(there, 100.0)
        tracker.fixAfter(1, fix)
        assertEquals(listOf(fix), track("t", "s1"))
        assertEquals(listOf(fix to here), asked)
    }

    @Test
    fun `a dwell interrupted by a check-in and its undo starts over`() = runTest {
        trip("t")
        stop("t", "s1", 0)
        val tracker = tracker()
        tracker.fix(north(there, 100.0))
        repository.upsertStops(Stay.checkIn(repository.stops("t").first(), "s1", now.toLocalDateTime()))
        tracker.fixAfter(5, north(there, 100.0))
        assertNull(tracker.state.value.drive)
        repository.upsertStops(Stay.undoCheckIn(repository.stops("t").first(), "s1"))
        tracker.fixAfter(7, north(there, 100.0))
        assertEquals(StopState.PLANNED, stop("t", "s1").state)
        tracker.fixAfter(5, north(there, 100.0))
        tracker.fixAfter(5, north(there, 100.0))
        assertEquals(LocalTime.of(16, 12), stop("t", "s1").arrivalTime)
    }

    @Test
    fun `recording flips the trip being recorded`() {
        val tracker = tracker()
        assertNull(tracker.state.value.recordingTripId)
        tracker.recording("t")
        assertEquals("t", tracker.state.value.recordingTripId)
        tracker.recording(null)
        assertNull(tracker.state.value.recordingTripId)
    }
}
