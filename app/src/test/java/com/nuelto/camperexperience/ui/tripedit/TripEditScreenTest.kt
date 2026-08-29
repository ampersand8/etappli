package com.nuelto.camperexperience.ui.tripedit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.testutil.TestCamperApp
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

    private fun setContent(tripId: String? = null, planned: Boolean = false) {
        val handle = when {
            tripId != null -> SavedStateHandle(mapOf("tripId" to tripId))
            planned -> SavedStateHandle(mapOf("planned" to true))
            else -> SavedStateHandle()
        }
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
    fun `new trip cannot be saved until it has a name`() {
        setContent()
        compose.onNodeWithText("New trip").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Trip name").performTextInput("Jura")
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Save").performClick()
        runBlocking {
            val trip = tripRepository.trips().first().single()
            assertEquals("Jura", trip.name)
            assertEquals(listOf("saved:${trip.id}:true"), events)
        }
    }

    @Test
    fun `plan mode titles the form and saves a planned tour`() {
        setContent(planned = true)
        compose.onNodeWithText("Plan a tour").assertIsDisplayed()
        compose.onNodeWithText("Planned start").assertIsDisplayed()
        compose.onNodeWithText("Trip name").performTextInput("Ticino")
        compose.onNodeWithText("Save").performClick()
        runBlocking {
            assertEquals(TripStatus.PLANNED, tripRepository.trips().first().single().status)
        }
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
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Clear").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()
        runBlocking {
            assertEquals(null, tripRepository.trip("t1").first()!!.endDate)
        }
        assertEquals(listOf("saved:t1:false"), events)
    }
}
