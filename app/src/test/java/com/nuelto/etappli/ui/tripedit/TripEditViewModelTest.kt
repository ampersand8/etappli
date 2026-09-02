package com.nuelto.etappli.ui.tripedit

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.StopKind
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
    fun `new trip starts blank and savable only with a name`() {
        val state = newTripViewModel().uiState.value
        assertTrue(state.isNew)
        assertTrue(state.loaded)
        assertEquals("", state.name)
        assertNull(state.endDate)
        assertFalse(state.canSave)
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
        assertTrue(state.canSave)
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
    fun `save without a name is refused`() = runTest {
        var called = false
        newTripViewModel().save { _, _ -> called = true }
        assertFalse(called)
        assertTrue(tripRepository.trips().first().isEmpty())
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
    fun `editing an id that vanished behaves like a new trip`() {
        val state = editViewModel("gone").uiState.value
        assertTrue(state.isNew)
        assertTrue(state.loaded)
    }

    @Test
    fun `a plan route creates a planned tour`() = runTest {
        val vm = TripEditViewModel(SavedStateHandle(mapOf("planned" to true)), tripRepository)
        assertTrue(vm.uiState.value.isPlan)
        vm.setName("Ticino")
        vm.save { _, _ -> }
        assertEquals(TripStatus.PLANNED, tripRepository.trips().first().single().status)
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
    // --- home as the plan's starting point ----------------------------------------------

    private val settingsRepository = InMemorySettingsRepository()

    private fun planViewModel() = TripEditViewModel(
        SavedStateHandle(mapOf("planned" to true)),
        tripRepository,
        settingsRepository,
    )

    private suspend fun setHome(name: String = "Luzern") {
        settingsRepository.update(
            settingsRepository.settings().first()
                .copy(homeName = name, homeLocation = LatLng(47.0502, 8.3093)),
        )
    }

    @Test
    fun `a new plan sets out from home and comes back to it`() = runTest {
        setHome()
        val vm = planViewModel()
        vm.setName("Ticino")
        vm.setStartDate(LocalDate.of(2027, 6, 10))
        var created: String? = null
        vm.save { id, _ -> created = id }

        val stops = tripRepository.stops(created!!).first().sortedBy { it.orderIndex }
        assertEquals(listOf(0, 1), stops.map { it.orderIndex })
        stops.forEach { home ->
            assertEquals("Luzern", home.name)
            assertEquals(StopKind.HOME, home.kind)
            assertEquals(LatLng(47.0502, 8.3093), home.location)
            assertEquals(LocalDate.of(2027, 6, 10), home.arrivalDate)
        }
    }

    @Test
    fun `with no home set a new plan starts empty`() = runTest {
        val vm = planViewModel()
        vm.setName("Ticino")
        var created: String? = null
        vm.save { id, _ -> created = id }

        assertTrue(tripRepository.stops(created!!).first().isEmpty())
    }

    @Test
    fun `a logged trip does not get a home stop`() = runTest {
        setHome()
        val vm = TripEditViewModel(SavedStateHandle(), tripRepository, settingsRepository)
        vm.setName("Last weekend")
        var created: String? = null
        vm.save { id, _ -> created = id }

        assertTrue(tripRepository.stops(created!!).first().isEmpty())
    }

    @Test
    fun `editing an existing plan does not add a second home`() = runTest {
        setHome()
        val first = planViewModel()
        first.setName("Ticino")
        var created: String? = null
        first.save { id, _ -> created = id }
        assertEquals(2, tripRepository.stops(created!!).first().size)

        val again = TripEditViewModel(
            SavedStateHandle(mapOf("tripId" to created!!)),
            tripRepository,
            settingsRepository,
        )
        again.setName("Ticino-Tour")
        again.save { _, _ -> }

        assertEquals(2, tripRepository.stops(created!!).first().size)
    }

}
