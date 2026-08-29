package com.nuelto.camperexperience.ui.tripdetail

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.nuelto.camperexperience.data.InMemorySettingsRepository
import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.domain.VignetteTable
import com.nuelto.camperexperience.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TripDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripRepository = InMemoryTripRepository(seed = false)
    private val settingsRepository = InMemorySettingsRepository()

    private fun viewModel(tripId: String = "t1") = TripDetailViewModel(
        SavedStateHandle(mapOf("tripId" to tripId)),
        tripRepository,
        settingsRepository,
    )

    /** ViewModel with a live collector so uiState.value follows repository writes. */
    private fun TestScope.hotViewModel(tripId: String = "t1"): TripDetailViewModel {
        val vm = viewModel(tripId)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        return vm
    }

    private suspend fun seedTrip(status: TripStatus = TripStatus.DONE) {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Jura", startDate = LocalDate.of(2026, 7, 1), status = status),
        )
        tripRepository.upsertStop(
            Stop(
                id = "s1", tripId = "t1", name = "A", nights = 2, campingCostTotal = 40.0,
                location = LatLng(47.0, 7.0), orderIndex = 0, arrivalDate = LocalDate.of(2026, 7, 1),
            ),
        )
        tripRepository.upsertStop(
            Stop(
                id = "s2", tripId = "t1", name = "B", nights = 1, campingCostTotal = 20.0,
                location = LatLng(47.5, 7.5), orderIndex = 1, arrivalDate = LocalDate.of(2026, 7, 3),
            ),
        )
        tripRepository.upsertExpense(
            Expense(id = "e1", tripId = "t1", type = ExpenseType.ROAD_TAX, amount = 30.0, date = LocalDate.of(2026, 7, 2)),
        )
    }

    private suspend fun stops() = tripRepository.stops("t1").first()

    @Test
    fun `state combines trip stops expenses breakdown and estimate`() = runTest {
        seedTrip()
        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals("Jura", state.trip!!.name)
            assertEquals(listOf("s1", "s2"), state.stops.map { it.id })
            assertEquals(listOf("e1"), state.expenses.map { it.id })
            assertEquals(60.0, state.breakdown.getValue(ExpenseType.CAMPING), 1e-9)
            assertEquals(30.0, state.breakdown.getValue(ExpenseType.ROAD_TAX), 1e-9)
            assertNotNull(state.fuelEstimate) // no fuel logged, route exists
            assertEquals("CHF", state.settings.currency)
            assertTrue(!state.loading)
            // Done trips show actuals, not the live estimate.
            assertNull(state.estimate)
            assertNull(state.currentStopId)
            assertTrue(state.vignetteSuggestions.isEmpty())
        }
    }

    @Test
    fun `active trips get an estimate and the first upcoming stop as current`() = runTest {
        seedTrip(status = TripStatus.ACTIVE)
        tripRepository.upsertStop(stops().first { it.id == "s1" }.copy(state = StopState.DONE))
        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals("s2", state.currentStopId)
            // Estimate total = recorded camping 60 + road tax 30 + auto fuel.
            assertEquals(60.0 + 30.0 + state.fuelEstimate!!, state.estimate!!.total, 1e-9)
        }
    }

    @Test
    fun `skipped stops are never current`() = runTest {
        seedTrip(status = TripStatus.ACTIVE)
        tripRepository.upsertStop(stops().first { it.id == "s1" }.copy(state = StopState.SKIPPED))
        viewModel().uiState.test {
            assertEquals("s2", awaitItem().currentStopId)
        }
    }

    @Test
    fun `planned trips in a vignette country get a suggestion until a road-tax line exists`() = runTest {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Plan", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
        )
        tripRepository.upsertStop(
            Stop(id = "s1", tripId = "t1", name = "Luzern", nights = 2, location = LatLng(47.05, 8.31), orderIndex = 0),
        )
        val vm = hotViewModel()
        val suggestion = vm.uiState.value.vignetteSuggestions.single()
        assertEquals("CH", suggestion.vignette.countryCode)

        vm.addVignette(suggestion)
        assertTrue(vm.uiState.value.vignetteSuggestions.isEmpty())
        val expense = tripRepository.expenses("t1").first().single()
        assertEquals(ExpenseType.ROAD_TAX, expense.type)
        assertTrue(expense.isEstimate)
        assertEquals("CH annual vignette (${VignetteTable.YEAR})", expense.label)
        assertEquals(40.0, expense.amount, 1e-9)
        assertEquals(LocalDate.of(2027, 6, 10), expense.date)
    }

    @Test
    fun `changing nights shifts the downstream planned schedule`() = runTest {
        seedTrip(status = TripStatus.ACTIVE)
        val vm = hotViewModel()
        vm.changeNights("s1", +1)
        assertEquals(3, stops().first { it.id == "s1" }.nights)
        assertEquals(LocalDate.of(2026, 7, 4), stops().first { it.id == "s2" }.arrivalDate)
        vm.changeNights("s1", -1)
        assertEquals(2, stops().first { it.id == "s1" }.nights)
        assertEquals(LocalDate.of(2026, 7, 3), stops().first { it.id == "s2" }.arrivalDate)
    }

    @Test
    fun `nights never drop below zero`() = runTest {
        seedTrip(status = TripStatus.ACTIVE)
        val vm = hotViewModel()
        vm.changeNights("s1", -2)
        vm.changeNights("s1", -1) // already at 0 — no-op
        assertEquals(0, stops().first { it.id == "s1" }.nights)
    }

    @Test
    fun `arrived stamps today and shifts what follows by the lateness`() = runTest {
        val today = LocalDate.now()
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Now", startDate = today.minusDays(2), status = TripStatus.ACTIVE),
        )
        tripRepository.upsertStop(
            Stop(id = "s1", tripId = "t1", name = "A", nights = 1, arrivalDate = today.minusDays(1), orderIndex = 0),
        )
        tripRepository.upsertStop(
            Stop(id = "s2", tripId = "t1", name = "B", nights = 1, arrivalDate = today, orderIndex = 1),
        )
        val vm = hotViewModel()
        vm.arrived("s1")
        val s1 = stops().first { it.id == "s1" }
        assertEquals(StopState.DONE, s1.state)
        assertEquals(today, s1.arrivalDate)
        // One day late -> tomorrow instead of today.
        assertEquals(today.plusDays(1), stops().first { it.id == "s2" }.arrivalDate)
    }

    @Test
    fun `setting the price at check-in makes the cost known`() = runTest {
        seedTrip(status = TripStatus.ACTIVE)
        val vm = hotViewModel()
        vm.setStopPrice("s1", 96.0)
        val s1 = stops().first { it.id == "s1" }
        assertEquals(96.0, s1.campingCostTotal, 1e-9)
        assertTrue(s1.costKnown)
    }

    @Test
    fun `skip and restore flip the stop state`() = runTest {
        seedTrip(status = TripStatus.ACTIVE)
        val vm = hotViewModel()
        vm.skip("s2")
        assertEquals(StopState.SKIPPED, stops().first { it.id == "s2" }.state)
        vm.restore("s2")
        assertEquals(StopState.PLANNED, stops().first { it.id == "s2" }.state)
    }

    @Test
    fun `move stop swaps neighbors but never crosses a done stop or the edges`() = runTest {
        seedTrip(status = TripStatus.ACTIVE)
        val vm = hotViewModel()
        vm.moveStop("s2", -1)
        assertEquals(listOf("s2", "s1"), stops().map { it.id })
        vm.moveStop("s2", -1) // top edge — no-op
        assertEquals(listOf("s2", "s1"), stops().map { it.id })
        vm.moveStop("s2", 1)
        assertEquals(listOf("s1", "s2"), stops().map { it.id })
        tripRepository.upsertStop(stops().first { it.id == "s1" }.copy(state = StopState.DONE))
        vm.moveStop("s2", -1) // would cross a done stop — locked
        assertEquals(listOf("s1", "s2"), stops().map { it.id })
    }

    @Test
    fun `starting a tour as template lands on a fresh trip, in place on the same`() = runTest {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Plan", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
        )
        val vm = hotViewModel()
        var startedId: String? = null
        vm.startTour(LocalDate.of(2027, 6, 12), keepPlanAsTemplate = true) { startedId = it }
        assertNotEquals("t1", startedId)
        assertEquals(TripStatus.ACTIVE, tripRepository.trip(startedId!!).first()!!.status)
        assertEquals(TripStatus.PLANNED, tripRepository.trip("t1").first()!!.status)

        var inPlaceId: String? = null
        vm.startTour(LocalDate.of(2027, 6, 12), keepPlanAsTemplate = false) { inPlaceId = it }
        assertEquals("t1", inPlaceId)
        assertEquals(TripStatus.ACTIVE, tripRepository.trip("t1").first()!!.status)
    }

    @Test
    fun `finish trip stamps done and today as end date`() = runTest {
        seedTrip(status = TripStatus.ACTIVE)
        val vm = hotViewModel()
        vm.finishTrip()
        val trip = tripRepository.trip("t1").first()!!
        assertEquals(TripStatus.DONE, trip.status)
        assertEquals(LocalDate.now(), trip.endDate)
    }

    @Test
    fun `plan again creates a fresh planned copy`() = runTest {
        seedTrip()
        val vm = hotViewModel()
        var newId: String? = null
        vm.planAgain { newId = it }
        assertNotEquals("t1", newId)
        assertEquals(TripStatus.PLANNED, tripRepository.trip(newId!!).first()!!.status)
    }

    @Test
    fun `actions on unknown stops are no-ops`() = runTest {
        seedTrip(status = TripStatus.ACTIVE)
        val vm = hotViewModel()
        vm.changeNights("nope", 1)
        vm.arrived("nope")
        vm.setStopPrice("nope", 1.0)
        vm.skip("nope")
        vm.moveStop("nope", 1)
        assertEquals(listOf("s1", "s2"), stops().map { it.id })
        assertFalse(stops().any { it.state != StopState.PLANNED })
    }

    @Test
    fun `unknown trip yields null trip`() = runTest {
        viewModel("nope").uiState.test {
            assertNull(awaitItem().trip)
        }
    }

    @Test
    fun `finish on an unknown trip is a no-op`() = runTest {
        viewModel("nope").finishTrip()
        assertTrue(tripRepository.trips().first().isEmpty())
    }

    @Test
    fun `delete stop and expense write through`() = runTest {
        seedTrip()
        val vm = viewModel()
        vm.deleteStop("s1")
        vm.deleteExpense("e1")
        assertEquals(listOf("s2"), tripRepository.stops("t1").first().map { it.id })
        assertTrue(tripRepository.expenses("t1").first().isEmpty())
    }

    @Test
    fun `upsert expense forces the trip id`() = runTest {
        seedTrip()
        viewModel().upsertExpense(Expense(id = "e9", tripId = "other", type = ExpenseType.OTHER, amount = 5.0))
        assertEquals("t1", tripRepository.expenses("t1").first().first { it.id == "e9" }.tripId)
    }

    @Test
    fun `delete trip removes it and notifies`() = runTest {
        seedTrip()
        var deleted = false
        viewModel().deleteTrip { deleted = true }
        assertTrue(deleted)
        assertNull(tripRepository.trip("t1").first())
    }
}
