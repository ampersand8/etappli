package com.nuelto.etappli.ui.share

import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.domain.SharedPlace
import com.nuelto.etappli.testutil.FakeShareLinkResolver
import com.nuelto.etappli.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddToTripViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripRepository = InMemoryTripRepository(seed = false)
    private val resolver = FakeShareLinkResolver()

    private fun viewModel(place: SharedPlace) = AddToTripViewModel(place, tripRepository, resolver::expand)

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
        assertFalse(vm.uiState.value.expanding)
        assertEquals("Camping X", vm.uiState.value.title)
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
    fun `a pin nobody named still has a headline, and no trips is no trips`() {
        val state = viewModel(SharedPlace(location = LatLng(46.5, 8.3))).uiState.value
        assertEquals("Shared pin", state.title)
        assertFalse(state.hasTrips)
        assertFalse(state.loading)
    }
}
