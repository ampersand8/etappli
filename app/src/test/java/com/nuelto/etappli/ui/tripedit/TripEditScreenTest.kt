package com.nuelto.etappli.ui.tripedit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.testutil.TestCamperApp
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class TripEditScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val tripRepository = InMemoryTripRepository(seed = false)
    private val events = mutableListOf<String>()

    private fun setContent(tripId: String? = null) {
        val handle = if (tripId != null) SavedStateHandle(mapOf("tripId" to tripId)) else SavedStateHandle()
        val viewModel = TripEditViewModel(handle, tripRepository)
        compose.setContent {
            TripEditScreen(
                onBack = { events += "back" },
                onSaved = { id, isNew -> events += "saved:$id:$isNew" },
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun `a new trip saves with the name typed`() {
        setContent()
        compose.onNodeWithText("New trip").assertIsDisplayed()
        compose.onNodeWithText("Optional — named after its stops").assertIsDisplayed()
        compose.onNodeWithText("Trip name").performTextInput("Jura")
        compose.onNodeWithText("Save").performClick()
        runBlocking {
            val trip = tripRepository.trips().first().single()
            assertEquals("Jura", trip.name)
            assertEquals(listOf("saved:${trip.id}:true"), events)
        }
    }

    @Test
    fun `a new trip saves without a name`() {
        setContent()
        compose.onNodeWithText("Save").performClick()
        runBlocking { assertEquals("", tripRepository.trips().first().single().name) }
    }

    @Test
    fun `cancel goes back without saving`() {
        setContent()
        compose.onNodeWithContentDescription("Cancel").performClick()
        assertEquals(listOf("back"), events)
        runBlocking { assertEquals(0, tripRepository.trips().first().size) }
    }

    @Test
    fun `existing trip shows its data and clear removes the end date`() {
        runBlocking {
            tripRepository.upsertTrip(
                Trip(
                    id = "t1", name = "Old", startDate = LocalDate.of(2026, 5, 1),
                    endDate = LocalDate.of(2026, 5, 3), notes = "n",
                ),
            )
        }
        setContent("t1")
        compose.onNodeWithText("Edit trip").assertIsDisplayed()
        compose.onNodeWithText("Old").assertIsDisplayed()
        compose.onNodeWithText("Leave blank to call it \"Rusty Edelweiss\"").assertIsDisplayed()
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Clear").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()
        runBlocking {
            assertEquals(null, tripRepository.trip("t1").first()!!.endDate)
        }
        assertEquals(listOf("saved:t1:false"), events)
    }

    @Test
    fun `a plan is edited under its own title, with a planned start`() {
        runBlocking {
            tripRepository.upsertTrip(Trip(id = "p1", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED))
        }
        setContent("p1")
        compose.onNodeWithText("Edit plan").assertIsDisplayed()
        compose.onNodeWithText("Planned start").assertIsDisplayed()
    }
}
