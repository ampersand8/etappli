package com.nuelto.camperexperience.ui.tripedit

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.PlaceSuggestion
import com.nuelto.camperexperience.testutil.TestCamperApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class PlaceSearchFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private val queries = mutableListOf<String>()
    private val picks = mutableListOf<PlaceSuggestion>()
    private var cleared = 0

    private val lauterbrunnen =
        PlaceSuggestion("Lauterbrunnen", "Bern, Switzerland", LatLng(46.5939, 7.9078))
    private val matterhorn = PlaceSuggestion("Matterhorn", "", LatLng(45.97, 7.65))

    private fun setContent(initial: StopEditUiState): MutableState<StopEditUiState> {
        val state = mutableStateOf(initial)
        compose.setContent {
            PlaceSearchField(
                state = state.value,
                onQueryChange = { queries += it },
                onClear = { cleared++ },
                onPick = { picks += it },
            )
        }
        return state
    }

    @Test
    fun `an empty query offers no clear button and says nothing`() {
        setContent(StopEditUiState())
        compose.onNodeWithText("Search a place").assertIsDisplayed()
        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        compose.onNodeWithText("Searching…").assertDoesNotExist()
        compose.onNodeWithText("Nothing found.").assertDoesNotExist()
        compose.onNodeWithText("Search unavailable — pick on the map instead.").assertDoesNotExist()
    }

    @Test
    fun `typing reports the query`() {
        setContent(StopEditUiState())
        compose.onNodeWithText("Search a place").performTextInput("Lau")
        assertEquals(listOf("Lau"), queries)
    }

    @Test
    fun `the clear button resets the field`() {
        setContent(StopEditUiState(placeQuery = "Lau"))
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun `every search status has its own line`() {
        val state = setContent(StopEditUiState(searchStatus = PlaceSearchStatus.SEARCHING))
        compose.onNodeWithText("Searching…").assertIsDisplayed()

        state.value = StopEditUiState(searchStatus = PlaceSearchStatus.EMPTY)
        compose.onNodeWithText("Nothing found.").assertIsDisplayed()

        state.value = StopEditUiState(searchStatus = PlaceSearchStatus.UNAVAILABLE)
        compose.onNodeWithText("Search unavailable — pick on the map instead.").assertIsDisplayed()
    }

    @Test
    fun `suggestions render their address line and report a pick`() {
        setContent(StopEditUiState(suggestions = listOf(lauterbrunnen, matterhorn)))
        compose.onNodeWithText("Bern, Switzerland").assertIsDisplayed()
        compose.onNodeWithText("Matterhorn").assertIsDisplayed()
        compose.onNodeWithText("Lauterbrunnen").performClick()
        assertEquals(listOf(lauterbrunnen), picks)
    }
}
