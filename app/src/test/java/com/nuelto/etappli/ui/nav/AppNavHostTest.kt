package com.nuelto.etappli.ui.nav

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.AppContainer
import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.domain.SharedPlace
import com.nuelto.etappli.testutil.TestCamperApp
import com.nuelto.etappli.ui.map.LocalMapProvider
import com.nuelto.etappli.ui.map.PlaceholderMapProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * End-to-end navigation through the real NavHost, backed by the TestCamperApp's
 * seeded in-memory container. Maps render as placeholders (PlaceholderMapProvider).
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class AppNavHostTest {

    @get:Rule
    val compose = createComposeRule()

    private var consumed = 0

    private fun setContent(pending: SharedPlace? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalMapProvider provides PlaceholderMapProvider) {
                AppNavHost(pending) { consumed++ }
            }
        }
    }

    private val shared = SharedPlace("Camping Grimselblick", LatLng(46.5601, 8.332))

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
    fun `dropping a pin on the map feeds the stop editor`() {
        setContent()
        compose.onNodeWithText("Schwarzwald").performClick()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithText("Stop", useUnmergedTree = true).performClick()
        compose.onNodeWithText("  Pick on map").performClick()
        compose.onNodeWithText("Pick location").assertIsDisplayed()
        // Search is wired by the real factory; typing into it would hit the network.
        compose.onNodeWithText("Search a place").assertIsDisplayed()
        // Press and hold the map (the placeholder stands in for the gesture).
        compose.onNodeWithTag("map-placeholder").performClick()
        compose.onNodeWithText("Dropped pin").assertIsDisplayed()
        compose.onNodeWithText("Use this place").performClick()
        // Back on the stop editor, holding a place rather than a coordinate.
        compose.onNodeWithText("New stop").assertIsDisplayed()
        compose.onNodeWithText("Pin on map").assertIsDisplayed()
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
    @Test
    fun `home is picked on the map, exactly as a stop's location is`() {
        setContent()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Home").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("  Pick on map").performScrollTo().performClick()

        compose.onNodeWithText("Pick location").assertIsDisplayed()
        compose.onNodeWithTag("map-placeholder").performClick()
        compose.onNodeWithText("Use this place").performClick()

        // Back in Settings, with somewhere for new plans to start from.
        compose.onNodeWithText("Set — new plans start here.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `picking home can be cancelled`() {
        setContent()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("  Pick on map").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithText("Not set. Pick it below and new plans will start from it.")
            .performScrollTo().assertIsDisplayed()
    }


    @Test
    fun `a shared place is filed on the trip that is chosen for it`() {
        setContent(shared)
        compose.onNodeWithText("Add to a trip").assertIsDisplayed()
        compose.onNodeWithText("Which trip?").assertIsDisplayed()
        // Routed once; the place rides the back stack from here on.
        assertEquals(1, consumed)

        compose.onNodeWithText("Schwarzwald").performClick()
        compose.onNodeWithText("New stop").assertIsDisplayed()
        compose.onAllNodesWithText("Camping Grimselblick")[0].assertIsDisplayed()

        compose.onNodeWithText("Save").performScrollTo().performClick()
        // Saving lands on that trip's timeline, with the stop on it.
        compose.onNodeWithText("Camping Kirnbergsee").assertIsDisplayed()
        compose.onAllNodesWithText("Camping Grimselblick")[0].assertIsDisplayed()
    }

    @Test
    fun `the chooser can be cancelled`() {
        setContent(shared)
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithText("Trips").assertIsDisplayed()
    }

    @Test
    fun `a tour planned from the chooser comes back to it`() {
        ApplicationProvider.getApplicationContext<TestCamperApp>().container =
            AppContainer(null, InMemoryTripRepository(seed = false), InMemorySettingsRepository())
        setContent(shared)

        compose.onNodeWithText("Plan a tour").performClick()
        compose.onNodeWithText("Trip name").performTextInput("Ticino")
        compose.onNodeWithText("Save").performClick()

        // Back on the chooser rather than in the new tour, with the tour now listed.
        compose.onNodeWithText("Add to a trip").assertIsDisplayed()
        compose.onNodeWithText("Ticino").performClick()
        compose.onNodeWithText("New stop").assertIsDisplayed()
        compose.onAllNodesWithText("Camping Grimselblick")[0].assertIsDisplayed()
    }
}
