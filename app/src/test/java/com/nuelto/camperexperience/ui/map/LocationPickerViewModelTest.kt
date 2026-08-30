package com.nuelto.camperexperience.ui.map

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.PlaceSearchStatus
import com.nuelto.camperexperience.domain.PlaceSuggestion
import com.nuelto.camperexperience.testutil.FakePlaceSearch
import com.nuelto.camperexperience.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val search = FakePlaceSearch()
    private val bern = LatLng(46.95, 7.45)
    private val lauterbrunnen =
        PlaceSuggestion("Lauterbrunnen", "Bern, Switzerland", LatLng(46.5939043, 7.9078016))

    private fun viewModel() = LocationPickerViewModel(search)

    @Test
    fun `typing debounces into a single search around the map centre`() = runTest {
        val vm = viewModel()
        vm.setQuery("Lau", bern)
        vm.setQuery("Laut", bern)
        vm.setQuery("Lauter", bern)
        advanceTimeBy(299)
        assertTrue(search.requests.isEmpty())

        advanceTimeBy(2)
        assertEquals(listOf("Lauter" to bern), search.requests)
        assertEquals("Lauterbrunnen", vm.uiState.value.results.single().name)
        assertEquals(PlaceSearchStatus.IDLE, vm.uiState.value.status)
        assertEquals("Lauter", vm.uiState.value.query)
    }

    @Test
    fun `three characters are enough to search, two are not`() = runTest {
        val vm = viewModel()
        vm.setQuery("Lau", bern)
        advanceTimeBy(301)
        assertEquals(listOf("Lau"), search.requests.map { it.first })

        vm.setQuery("La", bern)
        advanceTimeBy(1_000)
        assertEquals(1, search.requests.size)
    }

    @Test
    fun `a query under three characters searches nothing and drops stale hits`() = runTest {
        val vm = viewModel()
        vm.setQuery("Lauter", bern)
        advanceTimeBy(301)
        assertEquals(1, vm.uiState.value.results.size)

        vm.setQuery("  La  ", bern)
        advanceTimeBy(1_000)
        assertEquals(1, search.requests.size)
        assertTrue(vm.uiState.value.results.isEmpty())
        assertEquals(PlaceSearchStatus.IDLE, vm.uiState.value.status)
    }

    @Test
    fun `the status is searching in flight and empty when nothing matches`() = runTest {
        search.gated = true
        val vm = viewModel()
        vm.setQuery("Nirgendwo", bern)
        advanceTimeBy(301)
        assertEquals(PlaceSearchStatus.SEARCHING, vm.uiState.value.status)

        search.result = emptyList()
        search.gates.single().complete(Unit)
        assertEquals(PlaceSearchStatus.EMPTY, vm.uiState.value.status)
        assertTrue(vm.uiState.value.results.isEmpty())
    }

    @Test
    fun `a failed lookup reads as unavailable and clears the markers`() = runTest {
        val vm = viewModel()
        vm.setQuery("Lauter", bern)
        advanceTimeBy(301)
        assertEquals(1, vm.uiState.value.results.size)

        search.result = null
        vm.setQuery("Grindelwald", bern)
        advanceTimeBy(301)
        assertEquals(PlaceSearchStatus.UNAVAILABLE, vm.uiState.value.status)
        assertTrue(vm.uiState.value.results.isEmpty())
    }

    @Test
    fun `a search without a map centre still runs`() = runTest {
        val vm = viewModel()
        vm.setQuery("Lauterbrunnen", null)
        advanceTimeBy(301)
        assertNull(search.requests.single().second)
    }

    @Test
    fun `tapping a marker selects it and a fresh search deselects`() = runTest {
        val vm = viewModel()
        vm.setQuery("Lauter", bern)
        advanceTimeBy(301)
        vm.select(vm.uiState.value.results.single())
        assertEquals(lauterbrunnen, vm.uiState.value.selected)

        vm.setQuery("Grindelwald", bern)
        advanceTimeBy(301)
        assertNull(vm.uiState.value.selected)
    }

    @Test
    fun `a short query also drops the selection`() = runTest {
        val vm = viewModel()
        vm.setQuery("Lauter", bern)
        advanceTimeBy(301)
        vm.select(vm.uiState.value.results.single())
        vm.setQuery("La", bern)
        assertNull(vm.uiState.value.selected)
    }

    @Test
    fun `clearing cancels a pending search and resets everything`() = runTest {
        val vm = viewModel()
        vm.setQuery("Lauter", bern)
        vm.clearSearch()
        advanceTimeBy(1_000)
        assertTrue(search.requests.isEmpty())
        assertEquals(LocationPickerUiState(), vm.uiState.value)
    }

    @Test
    fun `without a search backend typing does nothing`() = runTest {
        val vm = LocationPickerViewModel()
        vm.setQuery("Lauterbrunnen", bern)
        advanceTimeBy(1_000)
        assertEquals("Lauterbrunnen", vm.uiState.value.query)
        assertEquals(PlaceSearchStatus.IDLE, vm.uiState.value.status)
        assertTrue(vm.uiState.value.results.isEmpty())
    }

    @Test
    fun `pressing and holding the map drops a nameless pin`() {
        val vm = viewModel()
        vm.dropPin(LatLng(46.5, 7.9))
        val selected = vm.uiState.value.selected!!
        assertEquals(LatLng(46.5, 7.9), selected.location)
        // No name: the stop editor reverse-geocodes one from the coordinate.
        assertEquals("", selected.name)
        assertEquals("", selected.label)
        assertEquals("", selected.id)
    }

    @Test
    fun `a dropped pin gives way to a fresh search`() = runTest {
        val vm = viewModel()
        vm.dropPin(LatLng(46.5, 7.9))
        vm.setQuery("Lauter", bern)
        advanceTimeBy(301)
        assertNull(vm.uiState.value.selected)
        assertEquals(1, vm.uiState.value.results.size)
    }
}
