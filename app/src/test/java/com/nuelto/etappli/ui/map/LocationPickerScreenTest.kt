package com.nuelto.etappli.ui.map

import android.os.Looper
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.domain.PlaceDetails
import com.nuelto.etappli.domain.PlaceSuggestion
import com.nuelto.etappli.testutil.FakePlaceSearch
import com.nuelto.etappli.testutil.TestCamperApp
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class LocationPickerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val search = FakePlaceSearch()
    private val picked = mutableListOf<Pair<LatLng, PlaceSuggestion?>>()
    private var cancelled = 0
    private lateinit var back: OnBackPressedDispatcher

    private val alpenblick =
        PlaceSuggestion("Camping Alpenblick", "Unterseen", LatLng(46.68, 7.83), "ChIJa")
    private val manorFarm =
        PlaceSuggestion("Camping Manor Farm", "Unterseen", LatLng(46.69, 7.82), "ChIJb")

    private fun setContent() {
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            LocationPickerScreen(
                initial = null,
                onPicked = { at, place -> picked += at to place },
                onCancel = { cancelled++ },
                viewModel = LocationPickerViewModel(search),
            )
        }
    }

    private fun submit(query: String) {
        compose.onNodeWithTag("place-search").performTextInput(query)
        compose.onNodeWithContentDescription("Search").performClick()
    }

    private fun pressBack() {
        compose.runOnUiThread { back.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `a submitted search lines its pins up as cards, and one of them opens in full`() {
        search.found = listOf(alpenblick, manorFarm)
        search.resolved = {
            it.copy(
                details = PlaceDetails(
                    kind = "Campground",
                    rating = 4.6,
                    ratingCount = 12,
                    summary = "Lakeside pitches.",
                ),
            )
        }
        setContent()
        submit("camping")
        compose.onNodeWithText("Camping Alpenblick").assertIsDisplayed()
        compose.onNodeWithText("Camping Manor Farm").assertIsDisplayed()

        compose.onNodeWithText("Camping Manor Farm").performClick()
        compose.onNodeWithText("Lakeside pitches.").assertIsDisplayed()
        compose.onNodeWithText("Campground · 4.6 ★ (12)").assertIsDisplayed()
        compose.onNodeWithText("Use this place").assertIsDisplayed()
        // The other pins wait behind the card.
        compose.onNodeWithText("Camping Alpenblick").assertDoesNotExist()
    }

    @Test
    fun `back shrinks the card to a strip, then brings the pins back, then clears the search`() {
        search.found = listOf(alpenblick, manorFarm)
        search.resolved = { it.copy(details = PlaceDetails(kind = "Campground", summary = "Lakeside pitches.")) }
        setContent()
        submit("camping")
        compose.onNodeWithText("Camping Manor Farm").performClick()

        pressBack()
        compose.onNodeWithText("Camping Manor Farm").assertIsDisplayed()
        compose.onNodeWithText("Lakeside pitches.").assertDoesNotExist()
        compose.onNodeWithText("Campground").assertIsDisplayed()
        compose.onNodeWithText("Use").assertIsDisplayed()

        // The strip opens up again on a tap, and folds on its chevron.
        compose.onNodeWithText("Camping Manor Farm").performClick()
        compose.onNodeWithText("Lakeside pitches.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show less").performClick()
        compose.onNodeWithText("Lakeside pitches.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show more").performClick()
        compose.onNodeWithText("Lakeside pitches.").assertIsDisplayed()
        pressBack()

        pressBack()
        compose.onNodeWithText("Camping Alpenblick").assertIsDisplayed()
        compose.onNodeWithText("Use").assertDoesNotExist()

        pressBack()
        compose.onNodeWithText("Camping Alpenblick").assertDoesNotExist()
        compose.onNodeWithText("Search a place").assertIsDisplayed()
        assertEquals(0, cancelled)
    }

    @Test
    fun `a single hit opens by itself and can be used from the strip`() {
        search.found = listOf(alpenblick)
        setContent()
        submit("alpenblick")
        compose.onNodeWithText("Use this place").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show less").performClick()
        compose.onNodeWithText("Use").performClick()
        assertEquals(listOf(alpenblick.location!! to alpenblick), picked)
    }

    @Test
    fun `typing lists predictions under the field, and picking one closes the list`() {
        search.result = listOf(PlaceSuggestion("Grimsel Pass", "Obergoms", location = null, id = "ChIJp"))
        search.resolved = { it.copy(location = LatLng(46.56, 8.34)) }
        setContent()
        compose.onNodeWithTag("place-search").performTextInput("grimsel")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        compose.onNodeWithText("Obergoms").assertIsDisplayed()

        compose.onNodeWithText("Grimsel Pass").performClick()
        compose.onNodeWithText("Use this place").assertIsDisplayed()
        compose.onNodeWithText("Obergoms").assertIsDisplayed()
        compose.onNodeWithText("Use this place").performClick()
        assertEquals(LatLng(46.56, 8.34), picked.single().first)
        assertEquals("Grimsel Pass", picked.single().second?.name)
    }

    @Test
    fun `a search that finds nothing says so, and a dropped pin still works`() {
        setContent()
        submit("nirgendwo")
        compose.onNodeWithText("Nothing found.").assertIsDisplayed()
        compose.onNodeWithTag("map-placeholder").performClick()
        compose.onNodeWithText("Dropped pin").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show less").performClick()
        compose.onNodeWithText("Dropped pin").assertIsDisplayed()
    }
}
