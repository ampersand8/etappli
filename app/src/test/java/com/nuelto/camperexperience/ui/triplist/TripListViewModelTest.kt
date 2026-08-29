package com.nuelto.camperexperience.ui.triplist

import app.cash.turbine.test
import com.nuelto.camperexperience.data.InMemorySettingsRepository
import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TripListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripRepository = InMemoryTripRepository(seed = false)
    private val settingsRepository = InMemorySettingsRepository()

    private fun viewModel() = TripListViewModel(tripRepository, settingsRepository)

    @Test
    fun `initial state is loading with no trips`() {
        val state = viewModel().uiState.value
        assertTrue(state.loading)
        assertTrue(state.trips.isEmpty())
    }

    @Test
    fun `emits trips settings and fuel estimates`() = runTest {
        tripRepository.upsertTrip(Trip(id = "t1", name = "Jura", startDate = LocalDate.of(2026, 7, 1)))
        tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", location = LatLng(47.0, 7.0), orderIndex = 0))
        tripRepository.upsertStop(Stop(id = "s2", tripId = "t1", location = LatLng(47.5, 7.5), orderIndex = 1))
        settingsRepository.update(UserSettings(currency = "EUR"))

        viewModel().uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertEquals(listOf("Jura"), state.trips.map { it.name })
            assertEquals("EUR", state.settings.currency)
            assertTrue(state.fuelEstimates.getValue("t1") > 0.0)
        }
    }

    @Test
    fun `trips with recorded fuel get no estimate`() = runTest {
        tripRepository.upsertTrip(Trip(id = "t1", name = "Jura", startDate = LocalDate.of(2026, 7, 1)))
        tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", location = LatLng(47.0, 7.0), orderIndex = 0))
        tripRepository.upsertStop(Stop(id = "s2", tripId = "t1", location = LatLng(47.5, 7.5), orderIndex = 1))
        tripRepository.upsertExpense(Expense(id = "e1", tripId = "t1", type = ExpenseType.FUEL, amount = 50.0))

        viewModel().uiState.test {
            assertTrue(awaitItem().fuelEstimates.isEmpty())
        }
    }

    @Test
    fun `estimates only use the trips own stops and expenses`() = runTest {
        // t1 has a route; t2 has fuel logged. Cross-contamination would flip both.
        tripRepository.upsertTrip(Trip(id = "t1", name = "A", startDate = LocalDate.of(2026, 7, 1)))
        tripRepository.upsertTrip(Trip(id = "t2", name = "B", startDate = LocalDate.of(2026, 8, 1)))
        tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", location = LatLng(47.0, 7.0), orderIndex = 0))
        tripRepository.upsertStop(Stop(id = "s2", tripId = "t1", location = LatLng(47.5, 7.5), orderIndex = 1))
        tripRepository.upsertStop(Stop(id = "s3", tripId = "t2", location = LatLng(46.0, 6.0), orderIndex = 0))
        tripRepository.upsertStop(Stop(id = "s4", tripId = "t2", location = LatLng(46.5, 6.5), orderIndex = 1))
        tripRepository.upsertExpense(Expense(id = "e1", tripId = "t2", type = ExpenseType.FUEL, amount = 50.0))

        viewModel().uiState.test {
            assertEquals(setOf("t1"), awaitItem().fuelEstimates.keys)
        }
    }

    @Test
    fun `state updates when a trip is added`() = runTest {
        val vm = viewModel()
        vm.uiState.test {
            assertTrue(awaitItem().trips.isEmpty())
            tripRepository.upsertTrip(Trip(id = "t1", name = "New", startDate = LocalDate.of(2026, 7, 1)))
            assertEquals(listOf("New"), awaitItem().trips.map { it.name })
        }
    }
}
