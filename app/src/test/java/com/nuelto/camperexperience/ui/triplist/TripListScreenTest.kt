package com.nuelto.camperexperience.ui.triplist

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.data.InMemorySettingsRepository
import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.Trip
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.nuelto.camperexperience.testutil.TestCamperApp

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
                onAddTrip = { clicks += "add" },
                onOpenMap = { clicks += "map" },
                onOpenSettings = { clicks += "settings" },
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun `empty state prompts for the first trip`() {
        setContent()
        compose.onNodeWithText("No trips yet.\nTap + to log your first one.").assertIsDisplayed()
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
    fun `top bar and fab fire navigation callbacks`() {
        setContent()
        compose.onNodeWithContentDescription("All trips map").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithContentDescription("New trip").performClick()
        assertEquals(listOf("map", "settings", "add"), clicks)
    }

    @Test
    fun `tapping a card opens the trip`() = runBlocking<Unit> {
        tripRepository.upsertTrip(Trip(id = "t1", name = "Jura", startDate = LocalDate.of(2026, 7, 1)))
        setContent()
        compose.onNodeWithText("Jura").performClick()
        assertEquals(listOf("trip:t1"), clicks)
    }
}
