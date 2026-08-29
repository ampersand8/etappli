package com.nuelto.camperexperience.ui.tripdetail

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.data.InMemorySettingsRepository
import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.testutil.TestCamperApp
import com.nuelto.camperexperience.ui.map.LocalMapEnabled
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class TripDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val tripRepository = InMemoryTripRepository(seed = false)
    private val settingsRepository = InMemorySettingsRepository()
    private val events = mutableListOf<String>()

    private fun seed(withLocations: Boolean = false, withExpenses: Boolean = true) = runBlocking {
        tripRepository.upsertTrip(
            Trip(
                id = "t1", name = "Jura", startDate = LocalDate.of(2026, 7, 1),
                endDate = LocalDate.of(2026, 7, 4), notes = "Rainy but great",
            ),
        )
        tripRepository.upsertStop(
            Stop(
                id = "s1", tripId = "t1", name = "Camp A", nights = 2, campingCostTotal = 40.0,
                location = if (withLocations) LatLng(47.0, 7.0) else null, orderIndex = 0,
            ),
        )
        if (withExpenses) {
            tripRepository.upsertExpense(
                Expense(
                    id = "e1", tripId = "t1", type = ExpenseType.ROAD_TAX, amount = 30.0,
                    date = LocalDate.of(2026, 7, 2), label = "Vignette",
                ),
            )
            tripRepository.upsertExpense(
                Expense(
                    id = "e2", tripId = "t1", type = ExpenseType.OTHER, amount = 5.0,
                    date = LocalDate.of(2026, 7, 3), isEstimate = true,
                ),
            )
        }
    }

    private fun setContent(tripId: String = "t1") {
        val viewModel = TripDetailViewModel(
            SavedStateHandle(mapOf("tripId" to tripId)),
            tripRepository,
            settingsRepository,
        )
        compose.setContent {
            CompositionLocalProvider(LocalMapEnabled provides false) {
                TripDetailScreen(
                    onBack = { events += "back" },
                    onEditTrip = { events += "edit:$it" },
                    onAddStop = { events += "addStop:$it" },
                    onEditStop = { tripId2, stopId -> events += "editStop:$tripId2:$stopId" },
                    onOpenTripMap = { events += "map:$it" },
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun `shows trip header stops expenses and cost breakdown`() {
        seed()
        setContent()
        compose.onNodeWithText("Jura").assertIsDisplayed()
        compose.onNodeWithText("Rainy but great").assertIsDisplayed()
        compose.onNodeWithText("Camp A").assertIsDisplayed()
        compose.onNodeWithText("Vignette").assertIsDisplayed()
        compose.onNodeWithText("Total").assertIsDisplayed()
        compose.onNodeWithText("2 nights").assertIsDisplayed()
        // Camping 40 + road tax 30 + other 5
        compose.onNodeWithText("Camping").assertIsDisplayed()
        compose.onNodeWithText("Road tax").assertIsDisplayed()
        // The OTHER expense has no label -> falls back to type name; "(estimate)" tag shown.
        compose.onNodeWithText("  (estimate)").assertIsDisplayed()
    }

    @Test
    fun `empty trip prompts for stops and expenses`() {
        runBlocking {
            tripRepository.upsertTrip(Trip(id = "t1", name = "Empty", startDate = LocalDate.of(2026, 7, 1)))
        }
        setContent()
        compose.onNodeWithText("No stops yet — add where you camped.").assertIsDisplayed()
        compose.onNodeWithText("No expenses yet.").assertIsDisplayed()
    }

    @Test
    fun `unknown trip renders nothing but the scaffold`() {
        setContent("nope")
        compose.onNodeWithText("Stops").assertDoesNotExist()
    }

    @Test
    fun `mini map appears only when a stop has a location`() {
        seed(withLocations = false)
        setContent()
        compose.onNodeWithTag("map-placeholder").assertDoesNotExist()
    }

    @Test
    fun `tapping the mini map opens the fullscreen trip map`() {
        seed(withLocations = true)
        setContent()
        compose.onNodeWithTag("map-placeholder").assertExists()
        compose.onNodeWithTag("map-placeholder").performClick()
        assertEquals(listOf("map:t1"), events)
    }

    @Test
    fun `fuel estimate row and disclaimer show when no fuel is logged`() {
        runBlocking {
            seed(withLocations = true, withExpenses = false)
            tripRepository.upsertStop(
                Stop(id = "s2", tripId = "t1", name = "Camp B", location = LatLng(47.5, 7.5), orderIndex = 1),
            )
        }
        setContent()
        compose.onNodeWithText("Fuel (estimate)").assertIsDisplayed()
        compose.onNodeWithText(
            "Fuel is estimated by driving distance between stops — log a fuel expense to replace it.",
        ).assertIsDisplayed()
    }

    @Test
    fun `top bar actions navigate`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Edit trip").performClick()
        assertEquals(listOf("back", "edit:t1"), events)
    }

    @Test
    fun `delete dialog cancel keeps the trip`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Delete trip").performClick()
        compose.onNodeWithText("Delete trip?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete trip?").assertDoesNotExist()
        runBlocking { assertEquals(1, tripRepository.trips().first().size) }
    }

    @Test
    fun `delete dialog confirm deletes and navigates back`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Delete trip").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("back"), events)
        runBlocking { assertTrue(tripRepository.trips().first().isEmpty()) }
    }

    @Test
    fun `fab menu adds a stop`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Stop", useUnmergedTree = true).performClick()
        assertEquals(listOf("addStop:t1"), events)
    }

    @Test
    fun `fab menu toggles closed again`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithContentDescription("Close add menu").performClick()
        compose.onNodeWithContentDescription("Add").assertIsDisplayed()
    }

    @Test
    fun `fab menu opens the new expense sheet`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Expense", useUnmergedTree = true).performClick()
        compose.onNodeWithText("New expense").assertIsDisplayed()
    }

    @Test
    fun `tapping a stop opens its editor`() {
        seed()
        setContent()
        compose.onNodeWithText("Camp A").performClick()
        assertEquals(listOf("editStop:t1:s1"), events)
    }

    @Test
    fun `tapping an expense opens the edit sheet`() {
        seed()
        setContent()
        compose.onNodeWithText("Vignette").performClick()
        compose.onNodeWithText("Edit expense").assertIsDisplayed()
    }

    @Test
    fun `expense delete icon removes the expense`() {
        seed()
        setContent()
        compose.onAllNodesWithContentDescription("Delete expense")[0].performClick()
        runBlocking {
            assertEquals(listOf("e2"), tripRepository.expenses("t1").first().map { it.id })
        }
    }

    @Test
    fun `saving an expense from the sheet stores it on the trip`() {
        seed()
        setContent()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Expense", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Amount").performClick()
        compose.onNodeWithText("Amount").performTextInput("42.50")
        compose.onNodeWithText("Save").performClick()
        runBlocking {
            val saved = tripRepository.expenses("t1").first().single { it.amount == 42.5 }
            assertEquals("t1", saved.tripId)
        }
    }
}
