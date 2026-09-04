package com.nuelto.etappli.ui.share

import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.data.model.UserSettings
import com.nuelto.etappli.domain.PlaceSuggestion
import com.nuelto.etappli.domain.SharedPlace
import com.nuelto.etappli.testutil.FakePlaceSearch
import com.nuelto.etappli.testutil.FakeShareLinkResolver
import com.nuelto.etappli.testutil.GatedSettingsRepository
import com.nuelto.etappli.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddToTripViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripRepository = InMemoryTripRepository(seed = false)
    private val settingsRepository = InMemorySettingsRepository()
    private val resolver = FakeShareLinkResolver()
    private val search = FakePlaceSearch()

    private fun viewModel(place: SharedPlace) =
        AddToTripViewModel(place, tripRepository, settingsRepository, resolver::expand, search::find)

    private val zielhaus = PlaceSuggestion("Zielhaus am Klausenpass", "Spiringen", LatLng(46.8695, 8.8543), "ChIJz")

    private suspend fun trip(name: String, status: TripStatus, start: LocalDate = LocalDate.of(2026, 6, 1)) {
        tripRepository.upsertTrip(Trip(id = name, name = name, startDate = start, status = status))
    }

    @Test
    fun `trips are split by status, tours soonest first`() = runTest {
        trip("Vierwaldstättersee", TripStatus.ACTIVE)
        trip("Ticino", TripStatus.PLANNED, LocalDate.of(2026, 9, 1))
        trip("Jura", TripStatus.PLANNED, LocalDate.of(2026, 7, 1))
        trip("Provence", TripStatus.DONE)

        val state = viewModel(SharedPlace("Camping X", LatLng(46.5, 8.3))).uiState.value
        assertEquals(listOf("Vierwaldstättersee"), state.active.map { it.name })
        assertEquals(listOf("Jura", "Ticino"), state.planned.map { it.name })
        assertEquals(listOf("Provence"), state.done.map { it.name })
        assertTrue(state.hasTrips)
        assertFalse(state.loading)
    }

    @Test
    fun `a share that already has a coordinate never touches the network`() {
        val vm = viewModel(SharedPlace("Camping X", LatLng(46.5, 8.3)))
        assertEquals(emptyList<String>(), resolver.requests)
        assertTrue(search.finds.isEmpty())
        assertFalse(vm.uiState.value.busy)
        assertEquals("Camping X", vm.uiState.value.title)
    }

    // --- a name and no spot: the Maps app's links carry only an ftid ----------------------

    @Test
    fun `a name with no spot is looked up near home, and the one hit is the place`() = runTest {
        settingsRepository.update(UserSettings(homeName = "Luzern", homeLocation = LatLng(47.05, 8.31)))
        search.gated = true
        search.found = listOf(zielhaus)
        val vm = viewModel(SharedPlace("Zielhaus am Klausenpass"))

        assertTrue(vm.uiState.value.locating)
        assertTrue(vm.uiState.value.busy)
        assertFalse(vm.uiState.value.expanding)
        assertEquals(listOf("Zielhaus am Klausenpass" to LatLng(47.05, 8.31)), search.finds)

        search.gates.single().complete(Unit)
        val state = vm.uiState.value
        assertEquals(LatLng(46.8695, 8.8543), state.place.location)
        assertEquals("ChIJz", state.place.placeId)
        assertEquals("Zielhaus am Klausenpass", state.place.name)
        assertTrue(state.place.fromPlaces)
        assertFalse(state.locating)
        assertFalse(state.expandFailed)
    }

    @Test
    fun `a link that only names the place is followed, then looked up`() {
        resolver.result = "https://www.google.com/maps/place/Zielhaus+am+Klausenpass/" +
            "data=!4m2!3m1!1s0x479a:0x6a7b?utm_source=mstt_1&entry=gps"
        search.found = listOf(zielhaus)
        val vm = viewModel(SharedPlace(link = "https://maps.app.goo.gl/x1"))
        assertEquals(listOf("Zielhaus am Klausenpass" to null), search.finds)
        assertEquals(LatLng(46.8695, 8.8543), vm.uiState.value.place.location)
        assertEquals("", vm.uiState.value.place.link)
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `without a place search a name stays unpinned, and nothing is tried or reported`() {
        val vm = AddToTripViewModel(SharedPlace("Zielhaus am Klausenpass"), tripRepository, settingsRepository)
        assertNull(vm.uiState.value.place.location)
        assertFalse(vm.uiState.value.busy)
        assertFalse(vm.uiState.value.expandFailed)
        vm.expand()
        assertFalse(vm.uiState.value.busy)
    }

    @Test
    fun `a link that could not be followed is not looked up yet`() {
        viewModel(SharedPlace("Zielhaus am Klausenpass", link = "https://maps.app.goo.gl/x1"))
        assertTrue(search.finds.isEmpty())
    }

    @Test
    fun `several hits pin nothing, and a pinned share or one with an id asks for none`() {
        search.found = listOf(zielhaus, zielhaus.copy(id = "ChIJother"))
        val vm = viewModel(SharedPlace("Camping Seeblick"))
        assertNull(vm.uiState.value.place.location)
        assertFalse(vm.uiState.value.place.fromPlaces)
        assertFalse(vm.uiState.value.expandFailed)
        assertEquals(1, search.finds.size)

        viewModel(SharedPlace("Camping X", LatLng(46.5, 8.3)))
        viewModel(SharedPlace("Camping X", placeId = "ChIJ1"))
        assertEquals(1, search.finds.size)
    }

    @Test
    fun `a lookup that did not come back can be tried again`() {
        search.found = null
        val vm = viewModel(SharedPlace("Zielhaus am Klausenpass"))
        assertTrue(vm.uiState.value.expandFailed)
        assertFalse(vm.uiState.value.locating)

        search.found = listOf(zielhaus)
        search.gated = true
        vm.expand()
        assertTrue(vm.uiState.value.locating)
        assertFalse(vm.uiState.value.expandFailed)
        search.gates.single().complete(Unit)
        assertFalse(vm.uiState.value.expandFailed)
        assertEquals(LatLng(46.8695, 8.8543), vm.uiState.value.place.location)
        assertEquals(2, search.finds.size)

        // Pinned: nothing left to look up.
        vm.expand()
        assertEquals(2, search.finds.size)
    }

    @Test
    fun `an approximate pin is looked up near itself and made exact`() {
        search.found = listOf(PlaceSuggestion("Titisee", "", LatLng(47.8918, 8.1454), "ChIJt"))
        val vm = viewModel(SharedPlace("Titisee", LatLng(47.89, 8.14), approximate = true))
        assertEquals(listOf("Titisee" to LatLng(47.89, 8.14)), search.finds)
        assertEquals(LatLng(47.8918, 8.1454), vm.uiState.value.place.location)
        assertFalse(vm.uiState.value.place.approximate)
    }

    @Test
    fun `a short link is followed and folded in`() {
        resolver.gate = CompletableDeferred()
        resolver.result = "https://www.google.com/maps/place/Grimselblick/data=!3d46.5601!4d8.332"
        val vm = viewModel(SharedPlace("Camping Grimselblick", link = "https://maps.app.goo.gl/x1"))

        assertTrue(vm.uiState.value.expanding)
        assertEquals(listOf("https://maps.app.goo.gl/x1"), resolver.requests)

        resolver.gate?.complete(Unit)
        val state = vm.uiState.value
        assertEquals(LatLng(46.5601, 8.332), state.place.location)
        assertEquals("Camping Grimselblick", state.place.name)
        assertEquals("", state.place.link)
        assertFalse(state.expanding)
        assertFalse(state.expandFailed)
    }

    @Test
    fun `a link that could not be followed can be tried again`() {
        val vm = viewModel(SharedPlace("Camping Grimselblick", link = "https://maps.app.goo.gl/x1"))
        assertTrue(vm.uiState.value.expandFailed)
        assertEquals("https://maps.app.goo.gl/x1", vm.uiState.value.place.link)

        resolver.result = "https://www.google.com/maps/place/Grimselblick/data=!3d46.5601!4d8.332"
        vm.expand()
        assertFalse(vm.uiState.value.expandFailed)
        assertEquals(LatLng(46.5601, 8.332), vm.uiState.value.place.location)
        assertEquals(2, resolver.requests.size)

        // Nothing left to follow: a second try is a no-op, not another request.
        vm.expand()
        assertEquals(2, resolver.requests.size)
    }

    @Test
    fun `planning a tour for the place makes an unnamed plan from home and hands back its id`() = runTest {
        settingsRepository.update(UserSettings(homeName = "Luzern", homeLocation = LatLng(47.05, 8.31)))
        val created = mutableListOf<String>()
        viewModel(SharedPlace("Camping X", LatLng(46.5, 8.3))).planTour { created += it }
        val trip = tripRepository.trip(created.single()).first()!!
        assertEquals(TripStatus.PLANNED, trip.status)
        assertEquals("", trip.name)
        assertEquals(2, tripRepository.stops(trip.id).first().size)
    }

    @Test
    fun `a second tap while the plan is being made mints no second plan`() = runTest {
        val gated = GatedSettingsRepository()
        val vm = AddToTripViewModel(SharedPlace("Camping X", LatLng(46.5, 8.3)), tripRepository, gated)
        val created = mutableListOf<String>()
        vm.planTour { created += it }
        vm.planTour { created += it }
        gated.gate.complete(Unit)
        assertEquals(1, created.size)
        vm.planTour { created += it }
        assertEquals(2, tripRepository.trips().first().size)
    }

    @Test
    fun `a pin nobody named still has a headline, and no trips is no trips`() {
        val state = viewModel(SharedPlace(location = LatLng(46.5, 8.3))).uiState.value
        assertEquals("Shared pin", state.title)
        assertFalse(state.hasTrips)
        assertFalse(state.loading)
    }
}
