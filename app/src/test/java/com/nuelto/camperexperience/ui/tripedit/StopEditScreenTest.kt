package com.nuelto.camperexperience.ui.tripedit

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.testutil.TestCamperApp
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
class StopEditScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val tripRepository = InMemoryTripRepository(seed = false)
    private val events = mutableListOf<String>()

    private fun setContent(stopId: String? = null, withLocationSection: Boolean = false) {
        val args = if (stopId == null) mapOf("tripId" to "t1") else mapOf("tripId" to "t1", "stopId" to stopId)
        val viewModel = StopEditViewModel(SavedStateHandle(args), tripRepository, null)
        compose.setContent {
            if (withLocationSection) {
                StopEditScreen(
                    onBack = { events += "back" },
                    viewModel = viewModel,
                    locationSection = { Text("Location section slot") },
                )
            } else {
                StopEditScreen(onBack = { events += "back" }, viewModel = viewModel)
            }
        }
    }

    @Test
    fun `new stop with nights stepper and save`() {
        setContent()
        compose.onNodeWithText("New stop").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Delete stop").assertDoesNotExist()

        compose.onNodeWithText("More nights").assertDoesNotExist()
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithContentDescription("More nights").performClick()
        compose.onNodeWithText("2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fewer nights").performClick()
        compose.onNodeWithText("1").assertIsDisplayed()

        compose.onNodeWithText("Campsite / place").performTextInput("Aire")
        compose.onNodeWithText("Camping cost (total for stay)").performTextInput("25")
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf("back"), events)
        runBlocking {
            val stop = tripRepository.stops("t1").first().single()
            assertEquals("Aire", stop.name)
            assertEquals(25.0, stop.campingCostTotal, 1e-9)
        }
    }

    @Test
    fun `location label defaults to not set and the slot is rendered`() {
        setContent(withLocationSection = true)
        compose.onNodeWithText("Not set").assertIsDisplayed()
        compose.onNodeWithText("Location section slot").assertIsDisplayed()
    }

    @Test
    fun `existing stop can be deleted from the top bar`() {
        runBlocking {
            tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", name = "Camp", location = LatLng(46.0, 7.0)))
        }
        setContent("s1")
        compose.onNodeWithText("Edit stop").assertIsDisplayed()
        compose.onNodeWithText("Camp").assertIsDisplayed()
        compose.onNodeWithText("46.0, 7.0").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete stop").performClick()
        assertEquals(listOf("back"), events)
        runBlocking { assertTrue(tripRepository.stops("t1").first().isEmpty()) }
    }

    @Test
    fun `cancel navigates back`() {
        setContent()
        compose.onNodeWithContentDescription("Cancel").performClick()
        assertEquals(listOf("back"), events)
    }
}
