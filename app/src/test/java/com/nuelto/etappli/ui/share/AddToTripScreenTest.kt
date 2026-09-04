package com.nuelto.etappli.ui.share

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.domain.PlaceSuggestion
import com.nuelto.etappli.domain.SharedPlace
import com.nuelto.etappli.testutil.FakePlaceSearch
import com.nuelto.etappli.testutil.FakeShareLinkResolver
import com.nuelto.etappli.testutil.TestCamperApp
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class AddToTripScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val tripRepository = InMemoryTripRepository(seed = false)
    private val resolver = FakeShareLinkResolver()
    private val search = FakePlaceSearch()
    private var cancelled = 0
    private val added = mutableListOf<Pair<String, SharedPlace>>()

    private fun setContent(place: SharedPlace) {
        compose.setContent {
            AddToTripScreen(
                onCancel = { cancelled++ },
                onAddTo = { tripId, shared -> added += tripId to shared },
                viewModel = AddToTripViewModel(
                    place, tripRepository, InMemorySettingsRepository(), resolver::expand, search::find,
                ),
            )
        }
    }

    private fun seedTrips() = runTest {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Schwarzwald", startDate = LocalDate.of(2026, 5, 1), status = TripStatus.DONE),
        )
        tripRepository.upsertTrip(
            Trip(id = "t2", name = "Ticino", startDate = LocalDate.of(2026, 9, 1), status = TripStatus.PLANNED),
        )
    }

    @Test
    fun `a shared place is filed on the trip that is tapped`() {
        seedTrips()
        val place = SharedPlace("Camping Grimselblick", LatLng(46.5601, 8.332))
        setContent(place)

        compose.onNodeWithText("Camping Grimselblick").assertIsDisplayed()
        compose.onNodeWithText("Pin from Google Maps.").assertIsDisplayed()
        compose.onNodeWithText("Which trip?").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Planned tours").assertIsDisplayed()

        compose.onNodeWithText("Ticino").performClick()
        assertEquals(listOf("t2" to place), added)
    }

    @Test
    fun `an approximate pin says so`() {
        seedTrips()
        setContent(SharedPlace("Titisee", LatLng(47.89, 8.14), approximate = true))
        compose.onNodeWithText("Approximate spot — check the pin on the map after.").assertIsDisplayed()
    }

    @Test
    fun `a share with no spot says what happens next`() {
        seedTrips()
        setContent(SharedPlace(name = "Camping Delta"))
        compose.onNodeWithText("No spot in that link — pick the place on the map after.").assertIsDisplayed()
    }

    @Test
    fun `a name with no spot is looked up, and the trips wait for the answer`() {
        seedTrips()
        search.gated = true
        search.found = listOf(PlaceSuggestion("Zielhaus am Klausenpass", "Spiringen", LatLng(46.8695, 8.8543), "ChIJz"))
        setContent(SharedPlace("Zielhaus am Klausenpass"))
        compose.onNodeWithText("Finding the place on Google Maps…").assertIsDisplayed()
        compose.onNodeWithText("Ticino").assertIsNotEnabled()

        search.gates.single().complete(Unit)
        compose.onNodeWithText("Pin from Google Maps.").assertIsDisplayed()
        compose.onNodeWithText("Ticino").performClick()
        val filed = added.single().second
        assertEquals(LatLng(46.8695, 8.8543), filed.location)
        assertTrue(filed.fromPlaces)
    }

    @Test
    fun `following a link is visible, and a failure can be retried`() {
        seedTrips()
        resolver.gate = CompletableDeferred()
        setContent(SharedPlace("Camping X", link = "https://maps.app.goo.gl/x1"))
        compose.onNodeWithText("Opening the Google Maps link…").assertIsDisplayed()

        // Choosing now would file the stop without the coordinate one redirect away.
        compose.onNodeWithText("Ticino").assertIsNotEnabled()

        resolver.gate?.complete(Unit)
        compose.onNodeWithText("Couldn't open that link. Check your connection.").assertIsDisplayed()
        compose.onNodeWithText("Ticino").performClick()
        assertEquals(1, added.size)

        resolver.result = "https://www.google.com/maps/place/X/data=!3d46.5!4d8.3"
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Pin from Google Maps.").assertIsDisplayed()
        assertEquals(2, resolver.requests.size)
    }

    @Test
    fun `with no trips at all the chooser plans one and files the place on it`() = runTest {
        val place = SharedPlace("Camping X", LatLng(46.5, 8.3))
        setContent(place)
        compose.onNodeWithText("No trips yet.\nPlan a tour, then add this place to it.").assertIsDisplayed()
        compose.onNodeWithText("Plan a tour").performClick()
        val planned = tripRepository.trips().first().single()
        assertEquals(TripStatus.PLANNED, planned.status)
        assertEquals(listOf(planned.id to place), added)

        compose.onNodeWithContentDescription("Cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun `an active trip is offered first`() = runTest {
        tripRepository.upsertTrip(Trip(id = "t3", name = "Vierwaldstättersee", status = TripStatus.ACTIVE))
        setContent(SharedPlace("Camping X", LatLng(46.5, 8.3)))
        compose.onNodeWithText("On the road").assertIsDisplayed()
        compose.onNodeWithText("Vierwaldstättersee").performClick()
        assertEquals(listOf("t3"), added.map { it.first })
    }
}
