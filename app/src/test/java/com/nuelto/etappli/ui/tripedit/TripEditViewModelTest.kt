package com.nuelto.etappli.ui.tripedit

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripRepository = InMemoryTripRepository(seed = false)

    private fun newTripViewModel() =
        TripEditViewModel(SavedStateHandle(), tripRepository)

    private fun editViewModel(tripId: String) =
        TripEditViewModel(SavedStateHandle(mapOf("tripId" to tripId)), tripRepository)

    @Test
    fun `new trip starts blank, with its name optional`() {
        val state = newTripViewModel().uiState.value
        assertTrue(state.isNew)
        assertTrue(state.loaded)
        assertEquals("", state.name)
        assertNull(state.endDate)
        assertEquals("Optional — named after its stops", state.nameHint)
    }

    @Test
    fun `setters update the state`() {
        val vm = newTripViewModel()
        vm.setName("Jura")
        vm.setStartDate(LocalDate.of(2026, 7, 1))
        vm.setEndDate(LocalDate.of(2026, 7, 5))
        vm.setNotes("wet")
        val state = vm.uiState.value
        assertEquals("Jura", state.name)
        assertEquals(LocalDate.of(2026, 7, 1), state.startDate)
        assertEquals(LocalDate.of(2026, 7, 5), state.endDate)
        assertEquals("wet", state.notes)
        vm.setEndDate(null)
        assertNull(vm.uiState.value.endDate)
    }

    @Test
    fun `save creates a new trip with trimmed fields`() = runTest {
        val vm = newTripViewModel()
        vm.setName("  Jura ")
        vm.setNotes(" note ")
        var saved: Pair<String, Boolean>? = null
        vm.save { id, isNew -> saved = id to isNew }
        assertTrue(saved!!.second)
        val trip = tripRepository.trip(saved!!.first).first()!!
        assertEquals("Jura", trip.name)
        assertEquals("note", trip.notes)
    }

    @Test
    fun `a trip saves without a name`() = runTest {
        var saved: String? = null
        newTripViewModel().save { id, _ -> saved = id }
        assertEquals("", tripRepository.trip(saved!!).first()!!.name)
    }

    @Test
    fun `existing trip is loaded and updated in place`() = runTest {
        tripRepository.upsertTrip(
            Trip(
                id = "t1", name = "Old", startDate = LocalDate.of(2026, 5, 1),
                endDate = LocalDate.of(2026, 5, 3), notes = "n",
            ),
        )
        val vm = editViewModel("t1")
        val loadedState = vm.uiState.value
        assertFalse(loadedState.isNew)
        assertTrue(loadedState.loaded)
        assertEquals("Old", loadedState.name)
        assertEquals(LocalDate.of(2026, 5, 3), loadedState.endDate)
        assertEquals("n", loadedState.notes)

        vm.setName("New")
        var saved: Pair<String, Boolean>? = null
        vm.save { id, isNew -> saved = id to isNew }
        assertEquals("t1" to false, saved)
        assertEquals(listOf("New"), tripRepository.trips().first().map { it.name })
    }

    @Test
    fun `the hint says what an unnamed trip is called`() = runTest {
        tripRepository.upsertTrip(Trip(id = "t1", name = "Old"))
        assertEquals("Leave blank to call it \"Rusty Edelweiss\"", editViewModel("t1").uiState.value.nameHint)
        tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", region = com.nuelto.etappli.data.model.StopRegion(name = "Ticino")))
        assertEquals("Leave blank to call it \"Ticino\"", editViewModel("t1").uiState.value.nameHint)
    }

    @Test
    fun `editing an id that vanished behaves like a new trip`() {
        val state = editViewModel("gone").uiState.value
        assertTrue(state.isNew)
        assertTrue(state.loaded)
    }

    @Test
    fun `a new trip with a past end date is saved as done`() = runTest {
        val vm = newTripViewModel()
        assertFalse(vm.uiState.value.isPlan)
        vm.setName("Last month")
        vm.setStartDate(LocalDate.now().minusDays(20))
        vm.setEndDate(LocalDate.now().minusDays(10))
        vm.save { _, _ -> }
        assertEquals(TripStatus.DONE, tripRepository.trips().first().single().status)
    }

    @Test
    fun `an open-ended trip stays active`() = runTest {
        val vm = newTripViewModel()
        vm.setName("Ongoing")
        vm.save { _, _ -> }
        assertEquals(TripStatus.ACTIVE, tripRepository.trips().first().single().status)
    }

    @Test
    fun `moving a plan's start date shifts its whole itinerary`() = runTest {
        tripRepository.upsertTrip(
            Trip(id = "p1", name = "Plan", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
        )
        tripRepository.upsertStop(
            Stop(id = "s1", tripId = "p1", arrivalDate = LocalDate.of(2027, 6, 10), nights = 2, orderIndex = 0),
        )
        tripRepository.upsertStop(
            Stop(id = "s2", tripId = "p1", arrivalDate = LocalDate.of(2027, 6, 12), nights = 1, orderIndex = 1),
        )
        val vm = editViewModel("p1")
        vm.setStartDate(LocalDate.of(2027, 6, 17))
        vm.save { _, _ -> }
        assertEquals(
            listOf(LocalDate.of(2027, 6, 17), LocalDate.of(2027, 6, 19)),
            tripRepository.stops("p1").first().map { it.arrivalDate },
        )
    }

    @Test
    fun `editing a planned tour keeps it planned even with past dates`() = runTest {
        tripRepository.upsertTrip(
            Trip(
                id = "p1", name = "Plan", startDate = LocalDate.now().minusDays(30),
                endDate = LocalDate.now().minusDays(20), status = TripStatus.PLANNED,
            ),
        )
        val vm = editViewModel("p1")
        assertTrue(vm.uiState.value.isPlan)
        vm.setName("Plan B")
        vm.save { _, _ -> }
        assertEquals(TripStatus.PLANNED, tripRepository.trip("p1").first()!!.status)
    }
}
