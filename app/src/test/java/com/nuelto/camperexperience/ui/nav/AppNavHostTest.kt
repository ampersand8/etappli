package com.nuelto.camperexperience.ui.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.testutil.TestCamperApp
import com.nuelto.camperexperience.ui.map.LocalMapEnabled
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * End-to-end navigation through the real NavHost, backed by the TestCamperApp's
 * seeded in-memory container. Maps render as placeholders (LocalMapEnabled=false).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class AppNavHostTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setContent() {
        compose.setContent {
            CompositionLocalProvider(LocalMapEnabled provides false) {
                AppNavHost()
            }
        }
    }

    @Test
    fun `starts on the trip list with demo data`() {
        setContent()
        compose.onNodeWithText("Trips").assertIsDisplayed()
        compose.onNodeWithText("Provence").assertIsDisplayed()
    }

    @Test
    fun `trip card opens the detail and back returns`() {
        setContent()
        compose.onNodeWithText("Schwarzwald").performClick()
        compose.onNodeWithText("Camping Kirnbergsee").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Trips").assertIsDisplayed()
    }

    @Test
    fun `trip edit opens from the detail and cancels back`() {
        setContent()
        compose.onNodeWithText("Schwarzwald").performClick()
        compose.onNodeWithContentDescription("Edit trip").performClick()
        compose.onNodeWithText("Edit trip").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithText("Camping Kirnbergsee").assertIsDisplayed()
    }

    @Test
    fun `new trip is created and opened`() {
        setContent()
        compose.onNodeWithContentDescription("New trip").performClick()
        compose.onNodeWithText("Log a trip", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Trip name").performTextInput("Ticino")
        compose.onNodeWithText("Save").performClick()
        // Saved -> pops the editor and navigates into the new trip.
        compose.onNodeWithText("Ticino").assertIsDisplayed()
        compose.onNodeWithText("No stops yet — add where you camped.").assertIsDisplayed()
    }

    @Test
    fun `a tour is planned from the fab menu`() {
        setContent()
        compose.onNodeWithContentDescription("New trip").performClick()
        compose.onNodeWithText("Plan a tour", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Planned start").assertIsDisplayed()
        compose.onNodeWithText("Trip name").performTextInput("Ticino-Tour")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Ticino-Tour").assertIsDisplayed()
    }

    @Test
    fun `stop editor opens with the location section injected`() {
        setContent()
        compose.onNodeWithText("Schwarzwald").performClick()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Stop", useUnmergedTree = true).performClick()
        compose.onNodeWithText("New stop").assertIsDisplayed()
        compose.onNodeWithText("  Pick on map").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithText("Camping Kirnbergsee").assertIsDisplayed()
    }

    @Test
    fun `picking a location on the map feeds the stop editor`() {
        setContent()
        compose.onNodeWithText("Schwarzwald").performClick()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Stop", useUnmergedTree = true).performClick()
        compose.onNodeWithText("  Pick on map").performClick()
        compose.onNodeWithText("Pick location").assertIsDisplayed()
        compose.onNodeWithText("Use this spot", useUnmergedTree = true).performClick()
        // Back on the stop editor with the picked map center as location.
        compose.onNodeWithText("New stop").assertIsDisplayed()
        compose.onNodeWithText("46.8, 8.2").assertIsDisplayed()
    }

    @Test
    fun `location picker can be cancelled`() {
        setContent()
        compose.onNodeWithText("Schwarzwald").performClick()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Stop", useUnmergedTree = true).performClick()
        compose.onNodeWithText("  Pick on map").performClick()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithText("New stop").assertIsDisplayed()
        compose.onNodeWithText("Not set").assertIsDisplayed()
    }

    @Test
    fun `all trips map opens from the list`() {
        setContent()
        compose.onNodeWithContentDescription("All trips map").performClick()
        compose.onNodeWithText("All trips").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Trips").assertIsDisplayed()
    }

    @Test
    fun `single trip map opens from the detail mini map`() {
        setContent()
        compose.onNodeWithText("Schwarzwald").performClick()
        compose.onNodeWithTag("map-placeholder").performClick()
        // Fullscreen map of just this trip carries the trip name as title.
        compose.onAllNodesWithText("Schwarzwald")[0].assertIsDisplayed()
    }

    @Test
    fun `settings open and return`() {
        setContent()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Trips").assertIsDisplayed()
    }
}
