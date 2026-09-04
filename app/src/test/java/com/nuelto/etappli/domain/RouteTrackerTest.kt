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
import java.time.LocalDate
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
    private val here = LatLng(46.8709, 8.2492)
    private val there = LatLng(46.1591, 8.7853)
    private val repository = InMemoryTripRepository(seed = false)
    private val asked = mutableListOf<Pair<LatLng, LatLng>>()
    private var answer: RoutedLeg? = RoutedLeg("", 90_000, 5_400)

    private fun tracker() = RouteTracker(repository, { from, to -> asked += from to to; answer }, { today })

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
    ) = repository.upsertStop(
        Stop(
            id = id, tripId = tripId, name = id, orderIndex = order, location = location,
            state = state, nights = nights, arrivalDate = today, leg = leg,
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
