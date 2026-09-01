package com.nuelto.etappli.ui.tripedit

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.testutil.FakePlaceNameResolver
import com.nuelto.etappli.testutil.MainDispatcherRule
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
    private val settingsRepository = InMemorySettingsRepository()
    private val resolver = FakePlaceNameResolver(ApplicationProvider.getApplicationContext())

    private fun newStopViewModel() = StopEditViewModel(
        SavedStateHandle(mapOf("tripId" to "t1")),
        tripRepository,
        settingsRepository,
        resolver,
    )

    private fun sharedViewModel(
        lat: Double? = 46.5601,
        lon: Double? = 8.332,
        placeName: String? = "Camping Grimselblick",
        placeId: String? = "ChIJCyinolJ-hUcR",
    ) = StopEditViewModel(
        SavedStateHandle(
            mapOf(
                "tripId" to "t1",
                "lat" to lat,
                "lon" to lon,
                "placeName" to placeName,
                "placeId" to placeId,
                "fromShare" to true,
            ),
        ),
        tripRepository,
        settingsRepository,
        resolver,
    )

    private fun editViewModel(stopId: String) = StopEditViewModel(
        SavedStateHandle(mapOf("tripId" to "t1", "stopId" to stopId)),
        tripRepository,
        settingsRepository,
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
    fun `location label names the place, never a coordinate`() {
        assertEquals("Not set", StopEditUiState().locationLabel)
        assertEquals("Pin on map", StopEditUiState(location = LatLng(46.5, 7.5)).locationLabel)
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
    fun `save without a name or a place is refused`() = runTest {
        var saved = false
        newStopViewModel().save { saved = true }
        assertFalse(saved)
        assertTrue(tripRepository.stops("t1").first().isEmpty())
    }

    @Test
    fun `a pin nothing can name saves under its kind`() = runTest {
        // No resolver, no field on a new stop: the kind is all the name there is.
        val vm = StopEditViewModel(SavedStateHandle(mapOf("tripId" to "t1")), tripRepository, settingsRepository)
        vm.setLocation(LatLng(46.0, 7.0))
        assertTrue(vm.uiState.value.canSave)
        vm.setKind(StopKind.FREE_CAMP)
        vm.save {}
        assertEquals("Free camp", tripRepository.stops("t1").first().single().name)
    }

    @Test
    fun `clearing the name of an existing stop falls back to the place`() = runTest {
        tripRepository.upsertStop(Stop(id = "s1", tripId = "t1", name = "Camp", location = LatLng(46.0, 7.0)))
        val vm = editViewModel("s1")
        vm.setName("  ")
        vm.save {}
        assertEquals("Place@46.0", tripRepository.stops("t1").first().single().name)
    }

    @Test
    fun `unparseable camping cost saves as zero and stays an estimate`() = runTest {
        val vm = newStopViewModel()
        vm.setName("Aire")
        vm.setCampingCost("abc")
        vm.save {}
        val stop = tripRepository.stops("t1").first().single()
        assertEquals(0.0, stop.campingCostTotal, 1e-9)
        assertFalse(stop.costKnown)
    }

    @Test
    fun `switching a free camp to a paid kind drops the known-zero price`() = runTest {
        tripRepository.upsertStop(
            Stop(id = "s1", tripId = "t1", name = "Waldrand", kind = StopKind.FREE_CAMP, costKnown = true),
        )
        val vm = editViewModel("s1")
        vm.setKind(StopKind.CAMPSITE)
        vm.save {}
        assertFalse(tripRepository.stops("t1").first().single().costKnown)
    }

    private fun insertViewModel(before: String) = StopEditViewModel(
        SavedStateHandle(mapOf("tripId" to "t1", "insertBefore" to before)),
        tripRepository,
        settingsRepository,
        resolver,
    )

    @Test
    fun `a stop inserted in front of another takes its slot and pushes the plan back`() = runTest {
        tripRepository.upsertStop(
            Stop(id = "a", tripId = "t1", name = "A", orderIndex = 0, nights = 2, arrivalDate = LocalDate.of(2027, 6, 10)),
        )
        tripRepository.upsertStop(
            Stop(id = "b", tripId = "t1", name = "B", orderIndex = 1, nights = 1, arrivalDate = LocalDate.of(2027, 6, 12)),
        )
        val vm = insertViewModel("b")
        // It arrives the day A leaves, and no GPS fix is asked for.
        assertEquals(LocalDate.of(2027, 6, 12), vm.uiState.value.arrivalDate)
        assertFalse(vm.uiState.value.autoLocatePending)
        vm.setName("Between")
        vm.setNights(2)
        vm.save {}
        val stops = tripRepository.stops("t1").first().sortedBy { it.orderIndex }
        assertEquals(listOf("A", "Between", "B"), stops.map { it.name })
        assertEquals(LocalDate.of(2027, 6, 14), stops.last().arrivalDate) // pushed back two nights
    }

    @Test
    fun `a stop inserted in front of unplanned nights starts on the first of them`() = runTest {
        tripRepository.upsertStop(
            Stop(id = "a", tripId = "t1", name = "A", orderIndex = 0, nights = 2, arrivalDate = LocalDate.of(2027, 6, 10)),
        )
        tripRepository.upsertStop(
            Stop(id = "b", tripId = "t1", name = "B", orderIndex = 1, nights = 1, arrivalDate = LocalDate.of(2027, 6, 15)),
        )
        val vm = insertViewModel("gap-b")
        assertEquals(LocalDate.of(2027, 6, 12), vm.uiState.value.arrivalDate)
        vm.setName("Between")
        vm.setNights(1)
        vm.save {}
        val stops = tripRepository.stops("t1").first().sortedBy { it.orderIndex }
        assertEquals(listOf("A", "Between", "B"), stops.map { it.name })
        // The three empty nights survive the insert, now after it.
        assertEquals(LocalDate.of(2027, 6, 16), stops.last().arrivalDate)
    }

    @Test
    fun `a stop inserted in front of a row that is gone just appends`() = runTest {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Plan", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
        )
        tripRepository.upsertStop(
            Stop(id = "a", tripId = "t1", name = "A", orderIndex = 0, nights = 2, arrivalDate = LocalDate.of(2027, 6, 10)),
        )
        val vm = insertViewModel("gone")
        assertEquals(LocalDate.of(2027, 6, 12), vm.uiState.value.arrivalDate) // chained off A
        vm.setName("Last")
        vm.save {}
        assertEquals(listOf("A", "Last"), tripRepository.stops("t1").first().sortedBy { it.orderIndex }.map { it.name })
    }

    @Test
    fun `new stops append after the highest order index, gaps included`() = runTest {
        tripRepository.upsertStop(Stop(id = "a", tripId = "t1", orderIndex = 0))
        tripRepository.upsertStop(Stop(id = "b", tripId = "t1", orderIndex = 5))
        val vm = newStopViewModel()
        vm.setName("New")
        vm.save {}
        assertEquals(6, tripRepository.stops("t1").first().single { it.name == "New" }.orderIndex)
    }

    @Test
    fun `extending a stay in the editor shifts the downstream planned schedule`() = runTest {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Plan", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
        )
        tripRepository.upsertStop(
            Stop(id = "s1", tripId = "t1", name = "A", arrivalDate = LocalDate.of(2027, 6, 10), nights = 2, orderIndex = 0),
        )
        tripRepository.upsertStop(
            Stop(id = "s2", tripId = "t1", name = "B", arrivalDate = LocalDate.of(2027, 6, 12), nights = 1, orderIndex = 1),
        )
        val vm = editViewModel("s1")
        vm.setNights(4)
        vm.save {}
        assertEquals(
            LocalDate.of(2027, 6, 14),
            tripRepository.stops("t1").first().single { it.id == "s2" }.arrivalDate,
        )
    }

    @Test
    fun `back-filling a done trip skips the GPS fix and chains the arrival`() = runTest {
        tripRepository.upsertTrip(
            Trip(
                id = "t1", name = "Done", startDate = LocalDate.of(2026, 5, 14),
                endDate = LocalDate.of(2026, 5, 18), status = TripStatus.DONE,
            ),
        )
        tripRepository.upsertStop(
            Stop(id = "s0", tripId = "t1", arrivalDate = LocalDate.of(2026, 5, 14), nights = 2, orderIndex = 0),
        )
        val vm = newStopViewModel()
        assertFalse(vm.uiState.value.autoLocatePending)
        assertEquals(LocalDate.of(2026, 5, 16), vm.uiState.value.arrivalDate)
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
        val vm = StopEditViewModel(SavedStateHandle(mapOf("tripId" to "t1")), tripRepository, settingsRepository)
        vm.setLocation(LatLng(46.0, 7.0))
        assertNull(vm.uiState.value.locationName)
        assertEquals("Pin on map", vm.uiState.value.locationLabel)
    }

    @Test
    fun `a named pick sets the location and name without a reverse geocode`() {
        val vm = newStopViewModel()
        vm.setPickedLocation(LatLng(46.5939043, 7.9078016), "Lauterbrunnen", "Bern, Switzerland")
        val state = vm.uiState.value
        assertEquals(LatLng(46.5939, 7.9078), state.location)
        assertEquals("Lauterbrunnen", state.name)
        assertEquals("Bern, Switzerland", state.locationName)
        assertTrue(resolver.requests.isEmpty())
    }

    @Test
    fun `an unnamed crosshair pick still gets reverse geocoded`() {
        val vm = newStopViewModel()
        vm.setPickedLocation(LatLng(46.0, 7.0), "", "")
        assertEquals("Place@46.0", vm.uiState.value.name)
        assertEquals(listOf(LatLng(46.0, 7.0)), resolver.requests)
    }

    @Test
    fun `choosing a named place renames the stop, whatever it was called`() {
        val vm = newStopViewModel()
        vm.setName("Grandma's driveway")
        vm.setPickedLocation(LatLng(46.6, 7.9), "Lauterbrunnen", "Bern")
        assertEquals("Lauterbrunnen", vm.uiState.value.name)
        assertEquals("Bern", vm.uiState.value.locationName)
    }

    @Test
    fun `an already-named stop takes the name of a place picked for it`() = runTest {
        tripRepository.upsertStop(
            Stop(id = "s1", tripId = "t1", name = "Camping Lido Luzern", location = LatLng(47.04, 8.33)),
        )
        val vm = editViewModel("s1")
        assertEquals("Camping Lido Luzern", vm.uiState.value.name)

        vm.setPickedLocation(LatLng(46.72, 8.22), "Camping Grimselblick", "Innertkirchen", "ChIJ1")
        assertEquals("Camping Grimselblick", vm.uiState.value.name)
        vm.save {}
        assertEquals("Camping Grimselblick", tripRepository.stops("t1").first().single().name)
    }

    @Test
    fun `a name typed after choosing a place is kept`() {
        val vm = newStopViewModel()
        vm.setPickedLocation(LatLng(46.6, 7.9), "Lauterbrunnen", "Bern")
        vm.setName("The spot by the river")
        assertEquals("The spot by the river", vm.uiState.value.name)
    }

    @Test
    fun `a place without an address label labels itself`() {
        val vm = newStopViewModel()
        vm.setPickedLocation(LatLng(45.97, 7.65), "Matterhorn", "")
        assertEquals("Matterhorn", vm.uiState.value.locationName)
    }

    @Test
    fun `moving a picked stop renames it again`() {
        val vm = newStopViewModel()
        vm.setPickedLocation(LatLng(46.6, 7.9), "Lauterbrunnen", "Bern")
        vm.setLocation(LatLng(47.0, 7.0))
        assertEquals("Place@47.0", vm.uiState.value.name)
    }

    @Test
    fun `an unasked-for GPS fix never displaces a location already chosen`() {
        val vm = newStopViewModel()
        vm.setAutoLocation(LatLng(46.0, 7.0))
        assertEquals(LatLng(46.0, 7.0), vm.uiState.value.location)

        vm.setPickedLocation(LatLng(46.6, 7.9), "Lauterbrunnen", "Bern")
        vm.setAutoLocation(LatLng(48.0, 9.0))
        assertEquals(LatLng(46.6, 7.9), vm.uiState.value.location)
    }

    @Test
    fun `the picker opens on the stop, else near the one before it, else nowhere`() = runTest {
        assertNull(newStopViewModel().uiState.value.pickerStart)

        tripRepository.upsertStop(Stop(id = "a", tripId = "t1", orderIndex = 0, location = LatLng(46.95, 7.45)))
        tripRepository.upsertStop(Stop(id = "b", tripId = "t1", orderIndex = 1, location = LatLng(46.32, 7.99)))
        assertEquals(LatLng(46.32, 7.99), newStopViewModel().uiState.value.pickerStart)
        // Editing a located stop: its own spot wins over the neighbour's.
        assertEquals(LatLng(46.32, 7.99), editViewModel("b").uiState.value.pickerStart)
        // An unlocated stop at the end falls back to the one before it.
        tripRepository.upsertStop(Stop(id = "c", tripId = "t1", name = "C", orderIndex = 2))
        assertEquals(LatLng(46.32, 7.99), editViewModel("c").uiState.value.pickerStart)
    }

    @Test
    fun `on a planned tour there is no auto GPS and arrival chains from the last stop`() = runTest {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Plan", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
        )
        tripRepository.upsertStop(
            Stop(id = "s0", tripId = "t1", arrivalDate = LocalDate.of(2027, 6, 10), nights = 2, orderIndex = 0),
        )
        val vm = newStopViewModel()
        assertFalse(vm.uiState.value.autoLocatePending)
        assertEquals(TripStatus.PLANNED, vm.uiState.value.tripStatus)
        assertEquals(LocalDate.of(2027, 6, 12), vm.uiState.value.arrivalDate)
    }

    @Test
    fun `a planned tour without stops prefills the trip start`() = runTest {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Plan", startDate = LocalDate.of(2027, 6, 10), status = TripStatus.PLANNED),
        )
        assertEquals(LocalDate.of(2027, 6, 10), newStopViewModel().uiState.value.arrivalDate)
    }

    @Test
    fun `the visit kind zeroes nights and cost and restores a night when switched back`() {
        val vm = newStopViewModel()
        vm.setNights(3)
        vm.setCampingCost("42")
        vm.setKind(StopKind.VISIT)
        assertEquals(0, vm.uiState.value.nights)
        assertEquals("", vm.uiState.value.campingCost)
        assertFalse(vm.uiState.value.isStay)
        vm.setKind(StopKind.STELLPLATZ)
        assertEquals(1, vm.uiState.value.nights)
    }

    @Test
    fun `a visit saves with zero nights and a known zero cost`() = runTest {
        val vm = newStopViewModel()
        vm.setKind(StopKind.VISIT)
        vm.setName("Aareschlucht")
        vm.save {}
        val stop = tripRepository.stops("t1").first().single()
        assertEquals(StopKind.VISIT, stop.kind)
        assertEquals(0, stop.nights)
        assertEquals(0.0, stop.campingCostTotal, 1e-9)
        assertTrue(stop.costKnown)
    }

    @Test
    fun `a blank price on a campsite saves as not yet known, a typed one as known`() = runTest {
        val vm = newStopViewModel()
        vm.setName("Camping Lido")
        vm.save {}
        assertFalse(tripRepository.stops("t1").first().single().costKnown)

        val vm2 = newStopViewModel()
        vm2.setName("Camping Delta")
        vm2.setCampingCost("62")
        vm2.save {}
        assertTrue(tripRepository.stops("t1").first().single { it.name == "Camping Delta" }.costKnown)
    }

    @Test
    fun `a free camp with a blank price is known to cost nothing`() = runTest {
        val vm = newStopViewModel()
        vm.setKind(StopKind.FREE_CAMP)
        vm.setName("Waldrand")
        vm.save {}
        val stop = tripRepository.stops("t1").first().single()
        assertTrue(stop.costKnown)
        assertEquals(0.0, stop.campingCostTotal, 1e-9)
    }

    @Test
    fun `a legacy stop recorded as known zero stays known after an edit`() = runTest {
        tripRepository.upsertStop(
            Stop(id = "s1", tripId = "t1", name = "Camp", campingCostTotal = 0.0, costKnown = true),
        )
        val vm = editViewModel("s1")
        vm.setName("Renamed")
        vm.save {}
        assertTrue(tripRepository.stops("t1").first().single().costKnown)
    }

    @Test
    fun `mid-trip with check-ins a new stop slots in after the last done stop`() = runTest {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Trip", startDate = LocalDate.of(2026, 8, 20), status = TripStatus.ACTIVE),
        )
        tripRepository.upsertStop(Stop(id = "done", tripId = "t1", name = "Done", orderIndex = 0, state = StopState.DONE))
        tripRepository.upsertStop(Stop(id = "up", tripId = "t1", name = "Upcoming", orderIndex = 1))
        val vm = newStopViewModel()
        vm.setName("Spontan")
        vm.save {}
        assertEquals(
            listOf("Done", "Spontan", "Upcoming"),
            tripRepository.stops("t1").first().map { it.name },
        )
    }

    @Test
    fun `an active trip without check-ins keeps appending`() = runTest {
        tripRepository.upsertTrip(
            Trip(id = "t1", name = "Trip", startDate = LocalDate.of(2026, 8, 20), status = TripStatus.ACTIVE),
        )
        tripRepository.upsertStop(Stop(id = "a", tripId = "t1", name = "A", orderIndex = 0))
        val vm = newStopViewModel()
        vm.setName("B")
        vm.save {}
        assertEquals(listOf("A", "B"), tripRepository.stops("t1").first().map { it.name })
    }

    @Test
    fun `a shared place prefills the stop, and never starts the Places clock`() {
        val state = sharedViewModel().uiState.value
        assertEquals("Camping Grimselblick", state.name)
        assertEquals(LatLng(46.5601, 8.332), state.location)
        assertEquals("ChIJCyinolJ-hUcR", state.placeId)
        // The coordinate came out of a link, not out of the Places API: it never expires.
        assertNull(state.locationCachedAt)
        // Somewhere to be already: no unasked-for GPS fix on top of it.
        assertFalse(state.autoLocatePending)
    }

    @Test
    fun `a shared pin with no name is reverse geocoded`() {
        val state = sharedViewModel(placeName = null, placeId = null).uiState.value
        assertEquals("Place@46.5601", state.name)
        assertEquals(LatLng(46.5601, 8.332), state.location)
        assertNull(state.placeId)
    }

    @Test
    fun `a shared name with no spot is still savable`() = runTest {
        val vm = sharedViewModel(lat = null, lon = null, placeId = null)
        val state = vm.uiState.value
        assertEquals("Camping Grimselblick", state.name)
        assertNull(state.location)
        assertTrue(state.canSave)
        assertFalse(state.autoLocatePending)

        vm.save {}
        assertEquals("Camping Grimselblick", tripRepository.stops("t1").first().single().name)
    }

    @Test
    fun `a place picked on the map still carries the Places clock`() {
        val vm = newStopViewModel()
        vm.setPickedLocation(LatLng(46.72, 8.22), "Camping Grimselblick", "Innertkirchen", "ChIJ1")
        assertEquals(LocalDate.now(), vm.uiState.value.locationCachedAt)
    }

    @Test
    fun `a share with nothing in it still keeps your own position out of the stop`() {
        // The link could not be followed: no name, no coordinate — and no GPS fix either,
        // because sharing a place says you are not standing at it.
        val state = sharedViewModel(lat = null, lon = null, placeName = null, placeId = null)
            .uiState.value
        assertFalse(state.autoLocatePending)
        assertEquals("", state.name)
        assertNull(state.location)
    }

    @Test
    fun `a shared place id with no coordinate is kept for the sweeper`() {
        val state = sharedViewModel(lat = null, lon = null).uiState.value
        assertEquals("ChIJCyinolJ-hUcR", state.placeId)
        assertNull(state.location)
        assertNull(state.locationCachedAt)
    }
}
