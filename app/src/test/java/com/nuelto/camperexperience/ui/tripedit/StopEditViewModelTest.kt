package com.nuelto.camperexperience.ui.tripedit

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.testutil.FakePlaceNameResolver
import com.nuelto.camperexperience.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StopEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tripRepository = InMemoryTripRepository(seed = false)
    private val resolver = FakePlaceNameResolver(ApplicationProvider.getApplicationContext())

    private fun newStopViewModel() = StopEditViewModel(
        SavedStateHandle(mapOf("tripId" to "t1")),
        tripRepository,
        resolver,
    )

    private fun editViewModel(stopId: String) = StopEditViewModel(
        SavedStateHandle(mapOf("tripId" to "t1", "stopId" to stopId)),
        tripRepository,
        resolver,
    )

    @Test
    fun `new stop wants an immediate GPS fix exactly once`() {
        val vm = newStopViewModel()
        assertTrue(vm.uiState.value.autoLocatePending)
        vm.autoLocateHandled()
        assertFalse(vm.uiState.value.autoLocatePending)
        assertEquals("t1", vm.tripId)
    }

    @Test
    fun `location label falls back from place name to coordinates to not set`() {
        assertEquals("Not set", StopEditUiState().locationLabel)
        assertEquals("46.5, 7.5", StopEditUiState(location = LatLng(46.5, 7.5)).locationLabel)
        assertEquals("Thun", StopEditUiState(location = LatLng(46.5, 7.5), locationName = "Thun").locationLabel)
    }

    @Test
    fun `setters update state and nights are clamped`() {
        val vm = newStopViewModel()
        vm.setName("Aire")
        vm.setArrivalDate(LocalDate.of(2026, 7, 2))
        vm.setCampingCost("12,50")
        vm.setNotes("ok")
        vm.setNights(-3)
        assertEquals(0, vm.uiState.value.nights)
        vm.setNights(400)
        assertEquals(365, vm.uiState.value.nights)
        val state = vm.uiState.value
        assertEquals("Aire", state.name)
        assertEquals(LocalDate.of(2026, 7, 2), state.arrivalDate)
        assertEquals("12,50", state.campingCost)
        assertEquals("ok", state.notes)
    }

    @Test
    fun `set location rounds to five decimals and resolves the place name`() {
        val vm = newStopViewModel()
        vm.setLocation(LatLng(46.123456789, 7.987654321))
        val location = vm.uiState.value.location!!
        assertEquals(46.12346, location.latitude, 1e-9)
        assertEquals(7.98765, location.longitude, 1e-9)
        assertEquals("Place@46.12346", vm.uiState.value.locationName)
    }

    @Test
    fun `clearing the location clears the name label`() {
        val vm = newStopViewModel()
        vm.setLocation(LatLng(46.0, 7.0))
        vm.setLocation(null)
        assertNull(vm.uiState.value.location)
        assertNull(vm.uiState.value.locationName)
    }

    @Test
    fun `blank stop name is auto-filled from the place and follows relocation`() {
        val vm = newStopViewModel()
        vm.setLocation(LatLng(46.0, 7.0))
        assertEquals("Place@46.0", vm.uiState.value.name)
        // Still auto-filled -> a new location may overwrite it.
        vm.setLocation(LatLng(47.0, 7.0))
        assertEquals("Place@47.0", vm.uiState.value.name)
    }

    @Test
    fun `a name typed by the user is never overwritten`() {
        val vm = newStopViewModel()
        vm.setName("My spot")
        vm.setLocation(LatLng(46.0, 7.0))
        assertEquals("My spot", vm.uiState.value.name)
        assertEquals("Place@46.0", vm.uiState.value.locationName)
    }

    @Test
    fun `late place result for an outdated location is ignored`() {
        resolver.gated = true
        val vm = newStopViewModel()
        vm.setLocation(LatLng(46.0, 7.0))
        vm.setLocation(LatLng(47.0, 7.0))
        assertEquals(2, resolver.gates.size)
        resolver.gates[1].complete(Unit) // current location resolves first
        assertEquals("Place@47.0", vm.uiState.value.name)
        resolver.gates[0].complete(Unit) // stale result must not win
        assertEquals("Place@47.0", vm.uiState.value.name)
        assertEquals("Place@47.0", vm.uiState.value.locationName)
    }

    @Test
    fun `save appends a new stop with the next order index`() = runTest {
        tripRepository.upsertStop(Stop(id = "s0", tripId = "t1", name = "First", orderIndex = 0))
        val vm = newStopViewModel()
        vm.setName("  Aire ")
        vm.setCampingCost("30")
        vm.setNotes(" n ")
        vm.setNights(2)
        var saved = false
        vm.save { saved = true }
        assertTrue(saved)
        val stop = tripRepository.stops("t1").first().single { it.name == "Aire" }
        assertEquals(1, stop.orderIndex)
        assertEquals(30.0, stop.campingCostTotal, 1e-9)
        assertEquals(2, stop.nights)
        assertEquals("n", stop.notes)
    }

    @Test
    fun `save without a name is refused`() = runTest {
        var saved = false
        newStopViewModel().save { saved = true }
        assertFalse(saved)
        assertTrue(tripRepository.stops("t1").first().isEmpty())
    }

    @Test
    fun `unparseable camping cost saves as zero`() = runTest {
        val vm = newStopViewModel()
        vm.setName("Aire")
        vm.setCampingCost("abc")
        vm.save {}
        assertEquals(0.0, tripRepository.stops("t1").first().single().campingCostTotal, 1e-9)
    }

    @Test
    fun `existing stop is loaded and its place name resolved without renaming`() = runTest {
        tripRepository.upsertStop(
            Stop(
                id = "s1", tripId = "t1", name = "Camp", arrivalDate = LocalDate.of(2026, 7, 3),
                nights = 3, campingCostTotal = 45.0, location = LatLng(46.0, 7.0), notes = "x",
            ),
        )
        val vm = editViewModel("s1")
        val state = vm.uiState.value
        assertFalse(state.isNew)
        assertEquals("Camp", state.name) // not overwritten by "Place@46.0"
        assertEquals("45.0", state.campingCost)
        assertEquals("Place@46.0", state.locationName)
        assertEquals(3, state.nights)
        assertEquals("x", state.notes)
    }

    @Test
    fun `zero camping cost shows as empty field`() = runTest {
        tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", name = "Camp", campingCostTotal = 0.0))
        assertEquals("", editViewModel("s1").uiState.value.campingCost)
    }

    @Test
    fun `saving an existing stop keeps id and order`() = runTest {
        tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", name = "Camp", orderIndex = 4))
        val vm = editViewModel("s1")
        vm.setName("Renamed")
        vm.save {}
        val stop = tripRepository.stops("t1").first().single()
        assertEquals("s1", stop.id)
        assertEquals(4, stop.orderIndex)
        assertEquals("Renamed", stop.name)
    }

    @Test
    fun `delete removes an existing stop and is a no-op for new ones`() = runTest {
        tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", name = "Camp"))
        var deleted = false
        editViewModel("s1").delete { deleted = true }
        assertTrue(deleted)
        assertTrue(tripRepository.stops("t1").first().isEmpty())

        var newDeleted = false
        newStopViewModel().delete { newDeleted = true }
        assertFalse(newDeleted)
    }

    @Test
    fun `works without a place name resolver`() {
        val vm = StopEditViewModel(SavedStateHandle(mapOf("tripId" to "t1")), tripRepository)
        vm.setLocation(LatLng(46.0, 7.0))
        assertNull(vm.uiState.value.locationName)
        assertEquals("46.0, 7.0", vm.uiState.value.locationLabel)
    }
}
