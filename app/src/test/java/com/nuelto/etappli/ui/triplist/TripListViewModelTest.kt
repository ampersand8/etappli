package com.nuelto.etappli.ui.triplist

import app.cash.turbine.test
import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.Expense
import com.nuelto.etappli.data.model.ExpenseType
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.data.model.UserSettings
import com.nuelto.etappli.testutil.MainDispatcherRule
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

    private suspend fun addDoneTrip(id: String, start: LocalDate = LocalDate.of(2026, 7, 1)) {
        tripRepository.upsertTrip(
            Trip(id = id, name = id, startDate = start, endDate = start.plusDays(5), status = TripStatus.DONE),
        )
    }

    @Test
    fun `initial state is loading and empty`() {
        val state = viewModel().uiState.value
        assertTrue(state.loading)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `trips are sectioned by status with planned sorted soonest-first`() = runTest {
        addDoneTrip("done")
        tripRepository.upsertTrip(
            Trip(id = "active", name = "Now", startDate = LocalDate.of(2026, 8, 20), status = TripStatus.ACTIVE),
        )
        tripRepository.upsertTrip(
            Trip(id = "later", name = "Later", startDate = LocalDate.of(2027, 8, 1), status = TripStatus.PLANNED),
        )
        tripRepository.upsertTrip(
            Trip(id = "soon", name = "Soon", startDate = LocalDate.of(2027, 6, 1), status = TripStatus.PLANNED),
        )

        viewModel().uiState.test {
            val state = awaitItem()
            assertFalse(state.loading)
            assertFalse(state.isEmpty)
            assertEquals(listOf("active"), state.active.map { it.id })
            assertEquals(listOf("soon", "later"), state.planned.map { it.id })
            assertEquals(listOf("done"), state.done.map { it.id })
        }
    }

    @Test
    fun `planned and active trips get a live estimate, done trips do not`() = runTest {
        addDoneTrip("done")
        tripRepository.upsertTrip(
            Trip(id = "plan", name = "Plan", startDate = LocalDate.of(2027, 6, 1), status = TripStatus.PLANNED),
        )
        tripRepository.upsertStop(Stop(id = "s1", tripId = "plan", nights = 2, costKnown = false))
        settingsRepository.update(UserSettings(campsitePerNight = 45.0))

        viewModel().uiState.test {
            val state = awaitItem()
            assertEquals(setOf("plan"), state.estimates.keys)
            assertEquals(90.0, state.estimates.getValue("plan").total, 1e-9)
            assertTrue(state.estimates.getValue("plan").hasEstimates)
        }
    }

    @Test
    fun `done trips without logged fuel keep the auto fuel estimate`() = runTest {
        addDoneTrip("done")
        tripRepository.upsertStop(Stop(id = "s1", tripId = "done", location = LatLng(47.0, 7.0), orderIndex = 0))
        tripRepository.upsertStop(Stop(id = "s2", tripId = "done", location = LatLng(47.5, 7.5), orderIndex = 1))

        viewModel().uiState.test {
            val state = awaitItem()
            assertTrue(state.fuelEstimates.getValue("done") > 0.0)
            assertTrue(state.estimates.isEmpty())
        }
    }

    @Test
    fun `done trips with recorded fuel get no estimate`() = runTest {
        addDoneTrip("done")
        tripRepository.upsertStop(Stop(id = "s1", tripId = "done", location = LatLng(47.0, 7.0), orderIndex = 0))
        tripRepository.upsertStop(Stop(id = "s2", tripId = "done", location = LatLng(47.5, 7.5), orderIndex = 1))
        tripRepository.upsertExpense(Expense(id = "e1", tripId = "done", type = ExpenseType.FUEL, amount = 50.0))

        viewModel().uiState.test {
            assertTrue(awaitItem().fuelEstimates.isEmpty())
        }
    }

    @Test
    fun `estimates only use the trips own stops and expenses`() = runTest {
        addDoneTrip("t2", start = LocalDate.of(2026, 8, 1))
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "A", startDate = LocalDate.of(2026, 7, 1), endDate = LocalDate.of(2026, 7, 6), status = TripStatus.DONE),
        )
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
    fun `settings flow through and state updates when a trip is added`() = runTest {
        settingsRepository.update(UserSettings(currency = "EUR"))
        val vm = viewModel()
        vm.uiState.test {
            val empty = awaitItem()
            assertTrue(empty.isEmpty)
            assertEquals("EUR", empty.settings.currency)
            tripRepository.upsertTrip(Trip(id = "t1", name = "New", startDate = LocalDate.of(2026, 7, 1)))
            assertEquals(listOf("New"), awaitItem().active.map { it.name })
        }
    }
}
