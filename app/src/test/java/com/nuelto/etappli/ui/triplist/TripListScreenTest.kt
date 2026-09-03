package com.nuelto.etappli.ui.triplist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.Expense
import com.nuelto.etappli.data.model.ExpenseType
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.nuelto.etappli.testutil.TestCamperApp

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class TripListScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val tripRepository = InMemoryTripRepository(seed = false)
    private val settingsRepository = InMemorySettingsRepository()

    private val clicks = mutableListOf<String>()

    private fun setContent() {
        val viewModel = TripListViewModel(tripRepository, settingsRepository)
        compose.setContent {
            TripListScreen(
                onTripClick = { clicks += "trip:$it" },
                onLogTrip = { clicks += "log" },
                onTourPlanned = { clicks += "planned:$it" },
                onOpenMap = { clicks += "map" },
                onOpenSettings = { clicks += "settings" },
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun `empty state prompts for the first trip`() {
        setContent()
        compose.onNodeWithText("No trips yet.\nTap + to plan a tour or log a trip.").assertIsDisplayed()
    }

    @Test
    fun `trip card shows name dates nights and actual total`() = runBlocking<Unit> {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Provence", startDate = LocalDate.of(2026, 9, 12), endDate = LocalDate.of(2026, 9, 21)),
        )
        tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", nights = 3, campingCostTotal = 90.0))
        tripRepository.upsertExpense(Expense(id = "e1", tripId = "t1", type = ExpenseType.FUEL, amount = 10.0))
        setContent()
        compose.onNodeWithText("Provence").assertIsDisplayed()
        compose.onNodeWithText("3 nights").assertIsDisplayed()
        compose.onNodeWithText("CHF100.00").assertIsDisplayed()
    }

    @Test
    fun `done trips without logged fuel show an approximate total`() = runBlocking<Unit> {
        tripRepository.upsertTrip(
            Trip(
                id = "t1", name = "Jura", startDate = LocalDate.of(2026, 7, 1),
                endDate = LocalDate.of(2026, 7, 6), status = TripStatus.DONE,
            ),
        )
        tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", location = LatLng(47.0, 7.0), orderIndex = 0))
        tripRepository.upsertStop(Stop(id = "s2", tripId = "t1", location = LatLng(47.5, 7.5), orderIndex = 1))
        setContent()
        compose.onNode(hasText("≈", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `trips are sectioned by status`() = runBlocking<Unit> {
        tripRepository.upsertTrip(
            Trip(id = "active", name = "Unterwegs", startDate = LocalDate.of(2026, 8, 20), status = TripStatus.ACTIVE),
        )
        tripRepository.upsertTrip(
            Trip(id = "plan", name = "Jura-Plan", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
        )
        tripRepository.upsertTrip(
            Trip(
                id = "done", name = "Provence", startDate = LocalDate.of(2025, 9, 12),
                endDate = LocalDate.of(2025, 9, 21), status = TripStatus.DONE,
            ),
        )
        setContent()
        compose.onNodeWithText("On the road").assertIsDisplayed()
        compose.onNodeWithText("Planned tours").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun `planned cards show the live default-rate estimate`() = runBlocking<Unit> {
        tripRepository.upsertTrip(
            Trip(id = "plan", name = "Jura-Plan", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
        )
        tripRepository.upsertStop(Stop(id = "s1", tripId = "plan", nights = 2, costKnown = false))
        setContent()
        compose.onNodeWithText("≈ CHF90.00").assertIsDisplayed()
    }

    @Test
    fun `top bar and fab menu fire navigation callbacks`() {
        setContent()
        compose.onNodeWithContentDescription("All trips map").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("New trip").performClick()
        compose.onNodeWithText("Log a trip", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("New trip").performClick()
        compose.onNodeWithText("Plan a tour", useUnmergedTree = true).performClick()
        // Planning a tour makes it on the spot and hands over its id.
        val planned = runBlocking { tripRepository.trips().first().single() }
        assertEquals(TripStatus.PLANNED, planned.status)
        assertEquals(listOf("map", "settings", "log", "planned:${planned.id}"), clicks)
    }

    @Test
    fun `tapping a card opens the trip`() = runBlocking<Unit> {
        tripRepository.upsertTrip(Trip(id = "t1", name = "Jura", startDate = LocalDate.of(2026, 7, 1)))
        setContent()
        compose.onNodeWithText("Jura").performClick()
        assertEquals(listOf("trip:t1"), clicks)
    }
}
