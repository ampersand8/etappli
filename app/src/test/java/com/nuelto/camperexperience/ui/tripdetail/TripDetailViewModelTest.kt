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
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

    private suspend fun seedTrip() {
        tripRepository.upsertTrip(Trip(id = "t1", name = "Jura", startDate = LocalDate.of(2026, 7, 1)))
        tripRepository.upsertStop(
            Stop(id = "s1", tripId = "t1", name = "A", nights = 2, campingCostTotal = 40.0, location = LatLng(47.0, 7.0), orderIndex = 0),
        )
        tripRepository.upsertStop(
            Stop(id = "s2", tripId = "t1", name = "B", nights = 1, campingCostTotal = 20.0, location = LatLng(47.5, 7.5), orderIndex = 1),
        )
        tripRepository.upsertExpense(
            Expense(id = "e1", tripId = "t1", type = ExpenseType.ROAD_TAX, amount = 30.0, date = LocalDate.of(2026, 7, 2)),
        )
    }

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
        }
    }

    @Test
    fun `unknown trip yields null trip`() = runTest {
        viewModel("nope").uiState.test {
            assertNull(awaitItem().trip)
        }
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
