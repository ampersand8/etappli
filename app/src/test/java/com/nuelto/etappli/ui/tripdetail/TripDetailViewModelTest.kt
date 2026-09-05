package com.nuelto.etappli.ui.tripdetail

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.Expense
import com.nuelto.etappli.data.model.ExpenseType
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopLeg
import com.nuelto.etappli.data.model.StopRegion
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.domain.VignetteTable
import com.nuelto.etappli.domain.PlaceCacheSweeper
import com.nuelto.etappli.domain.RegionResolver
import com.nuelto.etappli.domain.RoutedLeg
import com.nuelto.etappli.domain.RouteRefresher
import com.nuelto.etappli.domain.RouteTracker
import com.nuelto.etappli.testutil.MainDispatcherRule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
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

    private fun viewModel(tripId: String = "t1", sweeper: PlaceCacheSweeper? = null) =
        TripDetailViewModel(
            SavedStateHandle(mapOf("tripId" to tripId)),
            tripRepository,
            settingsRepository,
            sweeper,
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
            Stop(
                id = "s1", tripId = "t1", name = "Luzern", nights = 2, location = LatLng(47.05, 8.31),
                orderIndex = 0, arrivalDate = LocalDate.of(2027, 6, 10),
            ),
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
    fun `a multi-count vignette suggestion dismisses itself once accepted`() = runTest {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Austria", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
        )
        tripRepository.upsertStop(
            Stop(id = "s1", tripId = "t1", name = "Innsbruck", nights = 11, location = LatLng(47.26, 11.39), orderIndex = 0),
        )
        val vm = hotViewModel()
        val suggestion = vm.uiState.value.vignetteSuggestions.single()
        // 12 covered days -> two 10-day vignettes beat twelve 1-day ones and the annual.
        assertEquals(2, suggestion.count)
        vm.addVignette(suggestion)
        assertTrue(vm.uiState.value.vignetteSuggestions.isEmpty())
        val expense = tripRepository.expenses("t1").first().single()
        assertEquals("AT 10-day vignette ×2 (${VignetteTable.YEAR})", expense.label)
        assertEquals(25.60 * 0.94, expense.amount, 1e-9)
    }

    @Test
    fun `a checked-in stop stays current mid-stay`() = runTest {
        val today = LocalDate.now()
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Now", startDate = today.minusDays(1), status = TripStatus.ACTIVE),
        )
        tripRepository.upsertStop(
            Stop(id = "s1", tripId = "t1", name = "A", nights = 2, arrivalDate = today.minusDays(1), orderIndex = 0, state = StopState.DONE),
        )
        tripRepository.upsertStop(
            Stop(id = "s2", tripId = "t1", name = "B", nights = 1, arrivalDate = today.plusDays(1), orderIndex = 1),
        )
        viewModel().uiState.test {
            assertEquals("s1", awaitItem().currentStopId)
        }
    }

    @Test
    fun `restore is refused on a finished trip`() = runTest {
        seedTrip(status = TripStatus.DONE)
        tripRepository.upsertStop(stops().first { it.id == "s2" }.copy(state = StopState.SKIPPED))
        val vm = hotViewModel()
        vm.restore("s2")
        assertEquals(StopState.SKIPPED, stops().first { it.id == "s2" }.state)
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
        assertNotNull(s1.arrivalTime)
        // One day late -> tomorrow instead of today.
        assertEquals(today.plusDays(1), stops().first { it.id == "s2" }.arrivalDate)
    }

    @Test
    fun `check-out ends the stay today and pulls the plan forward`() = runTest {
        val today = LocalDate.now()
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Now", startDate = today.minusDays(1), status = TripStatus.ACTIVE),
        )
        tripRepository.upsertStop(
            Stop(
                id = "s1", tripId = "t1", name = "A", nights = 3, arrivalDate = today.minusDays(1), orderIndex = 0,
                state = StopState.DONE, arrivalTime = LocalTime.of(16, 0),
            ),
        )
        tripRepository.upsertStop(
            Stop(id = "s2", tripId = "t1", name = "B", nights = 1, arrivalDate = today.plusDays(2), orderIndex = 1),
        )
        val vm = hotViewModel()
        assertEquals("s1", vm.uiState.value.currentStopId)
        vm.checkOut("s1")
        assertEquals(1, stops().first { it.id == "s1" }.nights)
        assertEquals(today, stops().first { it.id == "s2" }.arrivalDate)
        assertEquals("s2", vm.uiState.value.currentStopId)
    }

    @Test
    fun `undoing a check-in plans the stop again and holds the tracker off it`() = runTest {
        val today = LocalDate.now()
        tripRepository.upsertTrip(Trip(id = "t1", name = "Now", startDate = today, status = TripStatus.ACTIVE))
        tripRepository.upsertStop(
            Stop(
                id = "s1", tripId = "t1", name = "A", nights = 2, arrivalDate = today, orderIndex = 0,
                location = hereFix, state = StopState.DONE, arrivalTime = LocalTime.of(16, 0),
            ),
        )
        var clock = ZonedDateTime.now()
        val tracker = RouteTracker(tripRepository, now = { clock })
        val vm = locatingViewModel({ hereFix }, tracker = tracker) { _, _ -> null }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.undoArrival("s1")
        val s1 = stops().single()
        assertEquals(StopState.PLANNED, s1.state)
        assertNull(s1.arrivalTime)
        assertEquals(today, s1.arrivalDate)
        // Standing there for as long as you like changes nothing now — without the hold-off,
        // these fixes would check the stop in again.
        repeat(4) {
            vm.refreshDriveFromHere()
            clock += Duration.ofMinutes(4)
        }
        assertEquals(StopState.PLANNED, stops().single().state)
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
    fun `a stop skipped is not arrived at`() = runTest {
        seedTrip(status = TripStatus.ACTIVE)
        tripRepository.upsertStop(stops().first { it.id == "s1" }.copy(state = StopState.DONE, arrivalTime = LocalTime.of(16, 0)))
        val vm = hotViewModel()
        vm.skip("s1")
        assertNull(stops().first { it.id == "s1" }.arrivalTime)
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
    fun `a dropped row lands on its index but never crosses a done stop`() = runTest {
        seedTrip(status = TripStatus.ACTIVE)
        val vm = hotViewModel()
        assertEquals("s2", vm.moveRow("s2", 0))
        assertEquals(listOf("s2", "s1"), stops().map { it.id })
        vm.moveRow("s2", 0) // already there — no-op
        assertEquals(listOf("s2", "s1"), stops().map { it.id })
        vm.moveRow("s2", 1)
        assertEquals(listOf("s1", "s2"), stops().map { it.id })
        tripRepository.upsertStop(stops().first { it.id == "s1" }.copy(state = StopState.DONE))
        vm.moveRow("s2", 0) // would cross a done stop — clamped back
        assertEquals(listOf("s1", "s2"), stops().map { it.id })
    }

    @Test
    fun `reordering re-dates the plan around the new order`() = runTest {
        seedTrip(status = TripStatus.PLANNED)
        val vm = hotViewModel()
        vm.moveRow("s2", 0)
        assertEquals(listOf("s2", "s1"), stops().map { it.id })
        assertEquals(LocalDate.of(2026, 7, 1), stops()[0].arrivalDate) // trip still starts Jul 1
        assertEquals(LocalDate.of(2026, 7, 2), stops()[1].arrivalDate) // after B's single night
    }

    @Test
    fun `unplanned nights can be resized, dropped and dragged along the plan`() = runTest {
        seedTrip(status = TripStatus.PLANNED)
        // Push B two days out: A leaves Jul 3, B arrives Jul 5.
        tripRepository.upsertStop(stops().first { it.id == "s2" }.copy(arrivalDate = LocalDate.of(2026, 7, 5)))
        val vm = hotViewModel()
        assertEquals(listOf("s1", "gap-s2", "s2"), vm.uiState.value.rows.map { it.key })

        vm.setGapNights("gap-s2", 3)
        assertEquals(LocalDate.of(2026, 7, 6), stops().first { it.id == "s2" }.arrivalDate)

        // Dragging the nights in front of the first stop is refused; the plan holds.
        vm.moveRow("gap-s2", 0)
        assertEquals(LocalDate.of(2026, 7, 6), stops().first { it.id == "s2" }.arrivalDate)

        vm.setGapNights("gap-s2", 0)
        assertEquals(LocalDate.of(2026, 7, 3), stops().first { it.id == "s2" }.arrivalDate)
        assertEquals(listOf("s1", "s2"), vm.uiState.value.rows.map { it.key })
    }

    @Test
    fun `an unplanned night inserted in front of a stop moves the rest of the plan back`() = runTest {
        seedTrip(status = TripStatus.PLANNED)
        val vm = hotViewModel()
        vm.insertGap("s2")
        assertEquals(LocalDate.of(2026, 7, 1), stops().first { it.id == "s1" }.arrivalDate) // untouched
        assertEquals(LocalDate.of(2026, 7, 4), stops().first { it.id == "s2" }.arrivalDate)
        assertEquals(listOf("s1", "gap-s2", "s2"), vm.uiState.value.rows.map { it.key })
        // A row that is no longer there cannot be inserted in front of.
        vm.insertGap("gone")
        assertEquals(LocalDate.of(2026, 7, 4), stops().first { it.id == "s2" }.arrivalDate)
    }

    @Test
    fun `a gap dragged past a stop is renamed after the stop it lands in front of`() = runTest {
        tripRepository.upsertTrip(Trip(id = "t1", name = "P", startDate = LocalDate.of(2026, 7, 1)))
        listOf(
            Stop(id = "a", tripId = "t1", orderIndex = 0, arrivalDate = LocalDate.of(2026, 7, 1)),
            Stop(id = "b", tripId = "t1", orderIndex = 1, arrivalDate = LocalDate.of(2026, 7, 4)),
            Stop(id = "c", tripId = "t1", orderIndex = 2, arrivalDate = LocalDate.of(2026, 7, 5)),
        ).forEach { tripRepository.upsertStop(it) }
        val vm = hotViewModel()
        assertEquals(listOf("a", "gap-b", "b", "c"), vm.uiState.value.rows.map { it.key })

        assertEquals("gap-c", vm.moveRow("gap-b", 2))
        assertEquals(listOf("a", "b", "gap-c", "c"), vm.uiState.value.rows.map { it.key })
        assertEquals(LocalDate.of(2026, 7, 2), stops().first { it.id == "b" }.arrivalDate)
        assertEquals(LocalDate.of(2026, 7, 5), stops().first { it.id == "c" }.arrivalDate) // trip still ends the 5th
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
        vm.moveRow("nope", 1)
        vm.setGapNights("nope", 2)
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

    @Test
    fun `opening a trip renews any Places coordinate that ran out of retention`() = runTest {
        tripRepository.upsertStop(
            Stop(
                id = "s1", tripId = "t1", name = "Camping Delta",
                location = LatLng(46.17, 8.79),
                placeId = "ChIJ1", locationCachedAt = LocalDate.now().minusDays(31),
            ),
        )
        viewModel(sweeper = PlaceCacheSweeper(tripRepository) { null })
        // No refresh available, so the terms say delete — the place id survives.
        val stop = tripRepository.stops("t1").first().single()
        assertNull(stop.location)
        assertEquals("ChIJ1", stop.placeId)
    }

    // --- drives -----------------------------------------------------------------------

    private fun leg(from: LatLng, to: LatLng) =
        StopLeg(from = from, to = to, distanceMeters = 124_000, durationSeconds = 6_300)

    private fun refreshingViewModel(refresher: RouteRefresher) =
        TripDetailViewModel(
            SavedStateHandle(mapOf("tripId" to "t1")),
            tripRepository,
            settingsRepository,
            routeRefresher = refresher,
        )

    /** Routes one leg per pair and records the points every request was made for. */
    private fun refresher(asked: MutableList<List<LatLng>>) = RouteRefresher(
        tripRepository,
        { points ->
            asked += points
            points.zipWithNext().map { RoutedLeg(polyline = "", distanceMeters = 124_000, durationSeconds = 6_300) }
        },
    )

    @Test
    fun `drives key the leg by the stop it arrives at`() = runTest {
        seedTrip()
        tripRepository.upsertStop(
            stops().first { it.id == "s2" }.copy(leg = leg(LatLng(47.0, 7.0), LatLng(47.5, 7.5))),
        )
        viewModel().uiState.test {
            val drives = awaitItem().drives
            assertEquals(listOf("s2"), drives.keys.toList()) // nothing drives to the first stop
            assertEquals(124_000, drives.getValue("s2").distanceMeters)
            assertEquals(6_300, drives.getValue("s2").durationSeconds)
        }
    }

    @Test
    fun `a leg whose endpoints no longer match its stops is left out`() = runTest {
        seedTrip()
        tripRepository.upsertStop(
            stops().first { it.id == "s2" }.copy(leg = leg(LatLng(47.0, 7.0), LatLng(47.5, 7.5))),
        )
        // Re-pinned departure: the stored leg describes a drive nobody makes any more.
        tripRepository.upsertStop(stops().first { it.id == "s1" }.copy(location = LatLng(46.0, 6.0)))
        viewModel().uiState.test {
            assertTrue(awaitItem().drives.isEmpty())
        }
    }

    @Test
    fun `a route refresher fills the drives in`() = runTest {
        seedTrip()
        val asked = mutableListOf<List<LatLng>>()
        refreshingViewModel(refresher(asked)).uiState.test {
            assertEquals(listOf(listOf(LatLng(47.0, 7.0), LatLng(47.5, 7.5))), asked)
            val drive = awaitItem().drives.getValue("s2")
            assertEquals(124_000, drive.distanceMeters)
            assertEquals(6_300, drive.durationSeconds)
            assertEquals(LatLng(47.0, 7.0), drive.from)
            assertEquals(LatLng(47.5, 7.5), drive.to)
            assertEquals(LocalDate.now(), drive.fetchedAt)
        }
        assertNotNull(stops().first { it.id == "s2" }.leg)
    }

    @Test
    fun `writing the fetched legs back does not fetch again`() = runTest {
        seedTrip()
        val asked = mutableListOf<List<LatLng>>()
        val vm = refreshingViewModel(refresher(asked))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        testScheduler.advanceUntilIdle()
        assertEquals(1, asked.size) // the legs it just wrote are not a new set of drives

        // A real change to the drives does re-route — once.
        tripRepository.upsertStop(stops().first { it.id == "s2" }.copy(location = LatLng(46.0, 6.0)))
        testScheduler.advanceUntilIdle()
        assertEquals(2, asked.size)
        assertEquals(listOf(LatLng(47.0, 7.0), LatLng(46.0, 6.0)), asked.last())
    }

    @Test
    fun `without a refresher nothing is routed and the trip still reads`() = runTest {
        seedTrip()
        viewModel().uiState.test {
            val state = awaitItem()
            assertTrue(state.drives.isEmpty())
            assertFalse(state.loading)
            assertEquals(listOf("s1", "s2"), state.stops.map { it.id })
        }
        assertNull(stops().first { it.id == "s2" }.leg)
    }
    // --- distance from here ------------------------------------------------------------

    private val hereFix = LatLng(46.8709, 8.2492)

    /** An ACTIVE trip whose current stop is [target]. */
    private suspend fun seedOnTheRoad(target: LatLng = LatLng(46.1591, 8.7853)) {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "On the road", startDate = LocalDate.now(), status = TripStatus.ACTIVE),
        )
        tripRepository.upsertStop(
            Stop(
                id = "s1", tripId = "t1", name = "Tonight", orderIndex = 0, location = target,
                arrivalDate = LocalDate.now(), nights = 1,
            ),
        )
    }

    private fun locatingViewModel(
        fix: suspend () -> LatLng?,
        startTracking: () -> Unit = {},
        tracker: RouteTracker? = null,
        drive: suspend (LatLng, LatLng) -> RoutedLeg?,
    ) = TripDetailViewModel(
        SavedStateHandle(mapOf("tripId" to "t1")),
        tripRepository,
        settingsRepository,
        currentLocation = fix,
        tracker = tracker ?: RouteTracker(tripRepository, drive = drive),
        startTracking = startTracking,
    )

    @Test
    fun `the drive from here is routed from the current fix to tonight's stop`() = runTest {
        val target = LatLng(46.1591, 8.7853)
        seedOnTheRoad(target)
        val asked = mutableListOf<Pair<LatLng, LatLng>>()
        val vm = locatingViewModel({ hereFix }) { from, to ->
            asked += from to to
            RoutedLeg("", 90_000, 5_400)
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.refreshDriveFromHere()

        assertEquals(listOf(hereFix to target), asked)
        val live = vm.uiState.value.driveFromHere!!
        assertEquals(hereFix, live.from)
        assertEquals(target, live.to)
        assertEquals(90_000, live.distanceMeters)
        assertEquals(5_400, live.durationSeconds)
    }

    @Test
    fun `standing at the stop shows no distance and spends no route`() = runTest {
        val target = LatLng(46.1591, 8.7853)
        seedOnTheRoad(target)
        var calls = 0
        // 50 m away: inside LiveDrive.ARRIVED_WITHIN_METERS.
        val nearly = LatLng(target.latitude + 50 / 111_195.0, target.longitude)
        val vm = locatingViewModel({ nearly }) { _, _ -> calls++; RoutedLeg("", 1, 1) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.refreshDriveFromHere()

        assertNull(vm.uiState.value.driveFromHere)
        assertEquals(0, calls)
    }

    @Test
    fun `a second fix in the same place does not spend another route`() = runTest {
        seedOnTheRoad()
        var calls = 0
        val vm = locatingViewModel({ hereFix }) { _, _ -> calls++; RoutedLeg("", 90_000, 5_400) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.refreshDriveFromHere()
        vm.refreshDriveFromHere()

        assertEquals(1, calls)
        assertEquals(90_000, vm.uiState.value.driveFromHere!!.distanceMeters)
    }

    @Test
    fun `no fix and no route both leave the card alone`() = runTest {
        seedOnTheRoad()
        val noFix = locatingViewModel({ null }) { _, _ -> RoutedLeg("", 1, 1) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { noFix.uiState.collect { } }
        noFix.refreshDriveFromHere()
        assertNull(noFix.uiState.value.driveFromHere)

        val noRoute = locatingViewModel({ hereFix }) { _, _ -> null }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { noRoute.uiState.collect { } }
        noRoute.refreshDriveFromHere()
        assertNull(noRoute.uiState.value.driveFromHere)
    }

    @Test
    fun `a trip with nowhere to drive to asks for nothing`() = runTest {
        seedTrip(TripStatus.DONE)
        var fixes = 0
        val vm = locatingViewModel({ fixes++; hereFix }) { _, _ -> RoutedLeg("", 1, 1) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.refreshDriveFromHere()

        assertEquals(0, fixes)
        assertNull(vm.uiState.value.driveFromHere)
    }

    @Test
    fun `checking in drops the distance and buys no more routes`() = runTest {
        val target = LatLng(46.1591, 8.7853)
        seedOnTheRoad(target)
        var calls = 0
        val vm = locatingViewModel({ hereFix }) { _, _ -> calls++; RoutedLeg("", 90_000, 5_400) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.refreshDriveFromHere()
        assertEquals(90_000, vm.uiState.value.driveFromHere!!.distanceMeters)

        vm.arrived("s1")

        assertNull(vm.uiState.value.driveFromHere)
        vm.refreshDriveFromHere()
        assertEquals(1, calls)
        assertNull(vm.uiState.value.driveFromHere)
    }

    // --- tracking ----------------------------------------------------------------------

    @Test
    fun `the opening fix lands on the track of the stop you are heading for`() = runTest {
        seedOnTheRoad()
        var here = hereFix
        val vm = locatingViewModel({ here }) { _, _ -> null }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.refreshDriveFromHere()
        here = LatLng(46.8, 8.3)
        vm.refreshDriveFromHere()

        assertEquals(listOf(hereFix, LatLng(46.8, 8.3)), stops().single().track)
    }

    @Test
    fun `tracking follows what the service records and drops on check-in`() = runTest {
        seedOnTheRoad()
        val tracker = RouteTracker(tripRepository)
        val vm = locatingViewModel({ hereFix }, tracker = tracker) { _, _ -> null }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        assertFalse(vm.uiState.value.tracking)

        tracker.recording("t1")
        assertTrue(vm.uiState.value.tracking)
        tracker.recording("other")
        assertFalse(vm.uiState.value.tracking)

        tracker.recording("t1")
        vm.arrived("s1")
        assertFalse(vm.uiState.value.tracking)
    }

    @Test
    fun `opening the trip starts the service, but not once you have checked in`() = runTest {
        seedOnTheRoad()
        var starts = 0
        val vm = locatingViewModel({ hereFix }, startTracking = { starts++ }) { _, _ -> null }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.refreshDriveFromHere()
        assertEquals(1, starts)

        vm.arrived("s1")
        vm.refreshDriveFromHere()
        assertEquals(1, starts)
    }

    @Test
    fun `a tour started ahead of its date does not start the service before the day`() = runTest {
        seedOnTheRoad()
        tripRepository.upsertTrip(tripRepository.trip("t1").first()!!.copy(startDate = LocalDate.now().plusDays(1)))
        var starts = 0
        val vm = locatingViewModel({ hereFix }, startTracking = { starts++ }) { _, _ -> RoutedLeg("", 90_000, 5_400) }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect { } }
        vm.refreshDriveFromHere()

        assertEquals(0, starts)
        // The distance still reads; nothing lands on the track.
        assertEquals(90_000, vm.uiState.value.driveFromHere!!.distanceMeters)
        assertTrue(stops().single().track.isEmpty())
    }

}

@RunWith(AndroidJUnit4::class)
class TripDetailRegionTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripRepository = InMemoryTripRepository(seed = false)

    @Test
    fun `opening a trip finds the region of every located stop, and again when a pin moves`() = runTest {
        tripRepository.upsertTrip(Trip(id = "t1", status = TripStatus.PLANNED))
        tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", location = LatLng(46.17, 8.79), orderIndex = 0))
        tripRepository.upsertStop(Stop(id = "s2", tripId = "t1", orderIndex = 1))
        val asked = mutableListOf<LatLng>()
        TripDetailViewModel(
            SavedStateHandle(mapOf("tripId" to "t1")),
            tripRepository,
            InMemorySettingsRepository(),
            regionResolver = RegionResolver(tripRepository) { at ->
                asked += at
                StopRegion(name = "Ticino", country = "Switzerland")
            },
        )
        assertEquals(listOf(LatLng(46.17, 8.79)), asked)
        assertEquals("Ticino", tripRepository.trip("t1").first()!!.region)

        // A stop that gets a pin is looked up; one whose pin moves is looked up again.
        val s2 = tripRepository.stops("t1").first().first { it.id == "s2" }
        tripRepository.upsertStop(s2.copy(location = LatLng(47.0, 7.0)))
        tripRepository.upsertStop(s2.copy(location = LatLng(47.5, 7.5)))
        assertEquals(listOf(LatLng(46.17, 8.79), LatLng(47.0, 7.0), LatLng(47.5, 7.5)), asked)
    }
}
