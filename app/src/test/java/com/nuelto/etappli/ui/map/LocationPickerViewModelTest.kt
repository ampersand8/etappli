package com.nuelto.etappli.ui.map

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.domain.PlaceDetails
import com.nuelto.etappli.domain.PlaceSearchStatus
import com.nuelto.etappli.domain.PlaceSuggestion
import com.nuelto.etappli.testutil.FakePlaceSearch
import com.nuelto.etappli.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    private val alpenblick =
        PlaceSuggestion("Camping Alpenblick", "Unterseen", LatLng(46.68, 7.83), "ChIJa")
    private val manorFarm =
        PlaceSuggestion("Camping Manor Farm", "Unterseen", LatLng(46.69, 7.82), "ChIJb")

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
        assertEquals("Lauterbrunnen", vm.uiState.value.predictions.single().name)
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
        assertEquals(1, vm.uiState.value.predictions.size)

        vm.setQuery("  La  ", bern)
        advanceTimeBy(1_000)
        assertEquals(1, search.requests.size)
        assertTrue(vm.uiState.value.predictions.isEmpty())
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
        assertTrue(vm.uiState.value.predictions.isEmpty())
    }

    @Test
    fun `a failed lookup reads as unavailable and clears the markers`() = runTest {
        val vm = viewModel()
        vm.setQuery("Lauter", bern)
        advanceTimeBy(301)
        assertEquals(1, vm.uiState.value.predictions.size)

        search.result = null
        vm.setQuery("Grindelwald", bern)
        advanceTimeBy(301)
        assertEquals(PlaceSearchStatus.UNAVAILABLE, vm.uiState.value.status)
        assertTrue(vm.uiState.value.predictions.isEmpty())
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
        vm.select(vm.uiState.value.predictions.single())
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
        vm.select(vm.uiState.value.predictions.single())
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
        assertTrue(vm.uiState.value.predictions.isEmpty())
    }

    @Test
    fun `choosing a prediction looks up where it actually is`() = runTest {
        val pass = PlaceSuggestion("Grimsel Pass", "Obergoms, Switzerland", location = null, id = "ChIJ1")
        search.result = listOf(pass)
        search.resolved = { it.copy(location = LatLng(46.5606, 8.3376)) }
        val vm = viewModel()
        vm.setQuery("grimsel", bern)
        advanceTimeBy(301)

        vm.select(vm.uiState.value.predictions.single())
        val selected = vm.uiState.value.selected!!
        assertEquals(LatLng(46.5606, 8.3376), selected.location)
        // The name the user picked from the list survives the lookup.
        assertEquals("Grimsel Pass", selected.name)
        assertEquals("ChIJ1", selected.id)
        assertEquals(PlaceSearchStatus.IDLE, vm.uiState.value.status)
    }

    @Test
    fun `a prediction that cannot be located says so instead of pretending`() = runTest {
        search.result = listOf(PlaceSuggestion("Grimsel Pass", "", location = null, id = "ChIJ1"))
        search.resolved = { null }
        val vm = viewModel()
        vm.setQuery("grimsel", bern)
        advanceTimeBy(301)

        vm.select(vm.uiState.value.predictions.single())
        assertNull(vm.uiState.value.selected?.location)
        assertEquals(PlaceSearchStatus.UNAVAILABLE, vm.uiState.value.status)
    }

    @Test
    fun `a late lookup for a prediction the user moved on from is ignored`() = runTest {
        val first = PlaceSuggestion("Grimselsee", "", location = null, id = "a")
        val second = PlaceSuggestion("Grimsel Hospiz", "", location = null, id = "b")
        search.resolved = { it.copy(location = LatLng(46.0, 8.0)) }
        val vm = viewModel()
        vm.select(first)
        vm.select(second)
        assertEquals("Grimsel Hospiz", vm.uiState.value.selected?.name)
    }

    @Test
    fun `a hit that already knows everything needs no lookup`() {
        val vm = viewModel()
        var asked = false
        search.resolved = { asked = true; it }
        vm.select(
            PlaceSuggestion("Aire", "", LatLng(46.56, 8.33), "ChIJ2", PlaceDetails(rating = 4.5)),
        )
        assertEquals(LatLng(46.56, 8.33), vm.uiState.value.selected?.location)
        assertEquals(PlaceSearchStatus.IDLE, vm.uiState.value.status)
        assertFalse(asked)
    }

    @Test
    fun `a tapped POI knows where it is but is still enriched`() {
        val vm = viewModel()
        search.resolved = { it.copy(details = PlaceDetails(rating = 4.8, ratingCount = 1787)) }
        vm.select(PlaceSuggestion("Aire de Grimsel", "", LatLng(46.56, 8.33), id = "ChIJ2"))
        val selected = vm.uiState.value.selected!!
        assertEquals(4.8, selected.details!!.rating!!, 1e-9)
        assertEquals(LatLng(46.56, 8.33), selected.location)
        assertEquals(PlaceSearchStatus.IDLE, vm.uiState.value.status)
    }

    @Test
    fun `a picture arrives after the card and only for the place still chosen`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val image = byteArrayOf(1, 2, 3)
        val vm = LocationPickerViewModel(search) { gate.await(); image }
        search.resolved = { it.copy(details = PlaceDetails(photo = "places/x/photos/y")) }

        vm.select(PlaceSuggestion("Camping", "", LatLng(46.0, 8.0), id = "ChIJ3"))
        assertNull(vm.uiState.value.photo)
        gate.complete(Unit)
        assertEquals(image, vm.uiState.value.photo)

        // Moving on drops it again.
        vm.dropPin(LatLng(47.0, 8.0))
        assertNull(vm.uiState.value.photo)
    }

    @Test
    fun `a place with no picture asks for none`() {
        var asked = false
        val vm = LocationPickerViewModel(search) { asked = true; null }
        vm.select(PlaceSuggestion("X", "", LatLng(46.0, 8.0), "id", PlaceDetails(rating = 3.0)))
        assertFalse(asked)
        assertNull(vm.uiState.value.photo)
    }

    @Test
    fun `without a search backend a prediction stays unlocated`() {
        val vm = LocationPickerViewModel()
        vm.select(PlaceSuggestion("Grimsel Pass", "", location = null, id = "ChIJ1"))
        assertNull(vm.uiState.value.selected?.location)
        assertEquals(PlaceSearchStatus.SEARCHING, vm.uiState.value.status)
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
        assertEquals(1, vm.uiState.value.predictions.size)
    }

    @Test
    fun `the kind of stop being added is passed to the search`() = runTest {
        val vm = viewModel()
        vm.setQuery("grimsel", bern, StopKind.STELLPLATZ)
        advanceTimeBy(301)
        assertEquals(listOf(StopKind.STELLPLATZ), search.preferences)

        vm.setQuery("grimsel again", bern)
        advanceTimeBy(301)
        assertEquals(listOf(StopKind.STELLPLATZ, null), search.preferences)
    }

    @Test
    fun `a search still in flight does not wipe the place you just chose`() = runTest {
        search.gated = true
        val vm = viewModel()
        vm.setQuery("grimsel", bern)
        advanceTimeBy(301)

        // Choose something (a tapped POI) before the search answers.
        vm.select(PlaceSuggestion("Aire", "", LatLng(46.5, 8.3), "ChIJ9", PlaceDetails(rating = 4.0)))
        assertEquals("Aire", vm.uiState.value.selected?.name)

        search.gates.single().complete(Unit)
        advanceTimeBy(1_000)
        assertEquals("Aire", vm.uiState.value.selected?.name)
    }

    @Test
    fun `a dropped pin survives a search landing behind it`() = runTest {
        search.gated = true
        val vm = viewModel()
        vm.setQuery("grimsel", bern)
        advanceTimeBy(301)

        vm.dropPin(LatLng(46.5, 7.9))
        search.gates.single().complete(Unit)
        advanceTimeBy(1_000)
        assertEquals(LatLng(46.5, 7.9), vm.uiState.value.selected?.location)
    }

    @Test
    fun `going back to the results keeps the query`() = runTest {
        val vm = viewModel()
        vm.setQuery("Lauter", bern)
        advanceTimeBy(301)
        vm.select(vm.uiState.value.predictions.single())
        assertNotNull(vm.uiState.value.selected)

        vm.clearSelection()
        assertNull(vm.uiState.value.selected)
        assertNull(vm.uiState.value.photo)
        assertEquals("Lauter", vm.uiState.value.query)
        assertEquals(1, vm.uiState.value.predictions.size)
    }

    // --- the search as submitted ---------------------------------------------

    @Test
    fun `the search as submitted locates its hits as pins and closes the list`() = runTest {
        search.found = listOf(alpenblick, manorFarm)
        val vm = viewModel()
        vm.setQuery("camping", bern)
        advanceTimeBy(301)
        assertEquals(1, vm.uiState.value.predictions.size)

        vm.search(bern)
        assertEquals(listOf("camping" to bern), search.finds)
        assertEquals(listOf(alpenblick, manorFarm), vm.uiState.value.results)
        assertTrue(vm.uiState.value.predictions.isEmpty())
        assertNull(vm.uiState.value.selected)
        assertEquals(PlaceSearchStatus.IDLE, vm.uiState.value.status)
        assertEquals("camping", vm.uiState.value.query)
    }

    @Test
    fun `submitting cancels the typing lookup still pending`() = runTest {
        search.found = listOf(alpenblick, manorFarm)
        val vm = viewModel()
        vm.setQuery("camping", bern)
        vm.search(bern)
        advanceTimeBy(1_000)
        assertTrue(search.requests.isEmpty())
        assertEquals(2, vm.uiState.value.results.size)
    }

    @Test
    fun `a search that finds one place opens it`() = runTest {
        search.found = listOf(alpenblick)
        search.resolved = { it.copy(details = PlaceDetails(rating = 4.6)) }
        val vm = viewModel()
        vm.setQuery("alpenblick", bern)
        vm.search(bern)
        assertEquals(listOf(alpenblick), vm.uiState.value.results)
        assertEquals(4.6, vm.uiState.value.selected!!.details!!.rating!!, 1e-9)
        assertTrue(vm.uiState.value.expanded)
    }

    @Test
    fun `a submitted search reads as searching, then empty or unavailable`() = runTest {
        search.gated = true
        val vm = viewModel()
        vm.setQuery("nirgendwo", bern)
        vm.search(bern)
        assertEquals(PlaceSearchStatus.SEARCHING, vm.uiState.value.status)
        search.found = emptyList()
        search.gates.single().complete(Unit)
        assertEquals(PlaceSearchStatus.EMPTY, vm.uiState.value.status)
        assertTrue(vm.uiState.value.results.isEmpty())

        search.gated = false
        search.found = null
        vm.search(bern)
        assertEquals(PlaceSearchStatus.UNAVAILABLE, vm.uiState.value.status)
    }

    @Test
    fun `a blank query, or no backend, submits nothing`() = runTest {
        val vm = viewModel()
        vm.setQuery("  ", bern)
        vm.search(bern)
        assertTrue(search.finds.isEmpty())

        val offline = LocationPickerViewModel()
        offline.setQuery("camping", bern)
        offline.search(bern)
        assertEquals(PlaceSearchStatus.IDLE, offline.uiState.value.status)
    }

    @Test
    fun `pins stay while typing on and while choosing, and go with a short query`() = runTest {
        search.found = listOf(alpenblick, manorFarm)
        val vm = viewModel()
        vm.setQuery("camping", bern)
        vm.search(bern)
        vm.setQuery("camping interlaken", bern)
        advanceTimeBy(301)
        assertEquals(2, vm.uiState.value.results.size)
        assertEquals(1, vm.uiState.value.predictions.size)

        vm.select(vm.uiState.value.predictions.single())
        assertEquals(2, vm.uiState.value.results.size)

        vm.setQuery("ca", bern)
        assertTrue(vm.uiState.value.results.isEmpty())
    }

    @Test
    fun `a place chosen while the search is still out is not overridden by its one hit`() = runTest {
        search.gated = true
        search.found = listOf(alpenblick)
        val vm = viewModel()
        vm.setQuery("alpenblick", bern)
        vm.search(bern)
        vm.select(manorFarm.copy(details = PlaceDetails(rating = 4.0)))
        search.gates.single().complete(Unit)
        advanceTimeBy(1_000)
        assertEquals("Camping Manor Farm", vm.uiState.value.selected?.name)
        assertTrue(vm.uiState.value.results.isEmpty())
    }

    // --- back ----------------------------------------------------------------

    @Test
    fun `back shrinks the card, then drops the choice, then clears the search, then gives up`() = runTest {
        search.found = listOf(alpenblick, manorFarm)
        val vm = viewModel()
        assertFalse(vm.uiState.value.canGoBack)
        assertFalse(vm.back())

        vm.setQuery("camping", bern)
        vm.search(bern)
        vm.select(alpenblick)
        assertTrue(vm.uiState.value.expanded)

        assertTrue(vm.back())
        assertFalse(vm.uiState.value.expanded)
        assertEquals(alpenblick, vm.uiState.value.selected)

        assertTrue(vm.back())
        assertNull(vm.uiState.value.selected)
        assertEquals(2, vm.uiState.value.results.size)

        assertTrue(vm.back())
        assertEquals(LocationPickerUiState(), vm.uiState.value)
        assertFalse(vm.back())
    }

    @Test
    fun `a typed fragment is also something to go back from`() {
        val vm = viewModel()
        vm.setQuery("ca", bern)
        assertTrue(vm.uiState.value.canGoBack)
        assertTrue(vm.back())
        assertEquals("", vm.uiState.value.query)
    }

    @Test
    fun `the card opens in full again for the next choice`() {
        val vm = viewModel()
        vm.select(alpenblick)
        vm.setExpanded(false)
        assertFalse(vm.uiState.value.expanded)
        vm.dropPin(LatLng(46.5, 7.9))
        assertTrue(vm.uiState.value.expanded)
        vm.setExpanded(false)
        vm.select(manorFarm)
        assertTrue(vm.uiState.value.expanded)
    }
}
