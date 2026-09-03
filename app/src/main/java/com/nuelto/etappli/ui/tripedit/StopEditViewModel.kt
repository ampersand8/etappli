package com.nuelto.etappli.ui.tripedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nuelto.etappli.containerViewModelFactory
import com.nuelto.etappli.data.SettingsRepository
import com.nuelto.etappli.data.TripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.isStay
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.data.model.UserSettings
import com.nuelto.etappli.domain.DateCascade
import com.nuelto.etappli.domain.HomeStop
import com.nuelto.etappli.domain.MapsUri
import com.nuelto.etappli.domain.Timeline
import com.nuelto.etappli.domain.nearestLocated
import com.nuelto.etappli.location.PlaceNameResolver
import com.nuelto.etappli.ui.components.parseDecimal
import com.nuelto.etappli.ui.nav.StopEditRoute
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StopEditUiState(
    val name: String = "",
    val arrivalDate: LocalDate = LocalDate.now(),
    val nights: Int = 1,
    val campingCost: String = "",
    val location: LatLng? = null,
    val locationName: String? = null,
    val notes: String = "",
    val kind: StopKind = StopKind.CAMPSITE,
    val isNew: Boolean = true,
    val tripStatus: TripStatus? = null,
    val settings: UserSettings = UserSettings(),
    val autoLocatePending: Boolean = false,
    // Nearest located stop — where the picker opens when this stop has no spot yet.
    val nearbyLocation: LatLng? = null,
    // Set only for a Google Places hit; its coordinate expires (domain/PlaceCache).
    val placeId: String? = null,
    val locationCachedAt: LocalDate? = null,
) {
    // A new stop has no name field: somewhere to be is enough, [savedName] finds it a name.
    val canSave: Boolean get() = name.isNotBlank() || location != null
    /** Nights and a price only make sense where you actually stay. */
    val isStay: Boolean get() = kind.isStay
    val pickerStart: LatLng? get() = location ?: nearbyLocation

    /** Google Maps link for this stop — the place itself when it came from Google. */
    val shareUrl: String? get() = MapsUri.share(name, location, placeId)

    // Never a raw coordinate: a place has a name, and a dropped pin is just "set".
    val locationLabel: String
        get() = locationName
            ?: location?.let { "Pin on map" }
            ?: "Not set"
}

/** A pin nothing could name still needs one: the place if we know it, else the kind. */
private fun StopEditUiState.savedName(): String =
    name.trim().ifBlank { locationName ?: kind.displayName }

class StopEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    settingsRepository: SettingsRepository,
    private val placeNameResolver: PlaceNameResolver? = null,
) : ViewModel() {

    private val route: StopEditRoute = savedStateHandle.toRoute<StopEditRoute>()
    val tripId: String get() = route.tripId
    private var existing: Stop? = null
    private var tripStatus: TripStatus? = null
    // Order index a stop inserted between two rows takes over; null when it appends.
    private var insertAt: Int? = null

    // Last name we auto-filled from reverse geocoding or a suggestion; anything else was
    // typed by the user and must never be overwritten.
    private var autoFilledName: String? = null

    private val _uiState = MutableStateFlow(StopEditUiState())
    val uiState: StateFlow<StopEditUiState> = _uiState

    init {
        viewModelScope.launch {
            val trip = tripRepository.trip(route.tripId).first()
            tripStatus = trip?.status
            val settings = settingsRepository.settings().first()
            _uiState.update { it.copy(tripStatus = trip?.status, settings = settings) }
            val stops = tripRepository.stops(route.tripId).first()

            if (route.stopId == null) {
                // Inserted in front of a row, or — with no key — landing at the end,
                // where the whole trip is "before" it.
                val at = route.insertBefore?.let { Timeline.insertion(Timeline.rows(stops), it) }
                insertAt = at?.orderIndex
                // A plan ends with the drive home, so "the end" is in front of that.
                val home = HomeStop.returning(stops)
                _uiState.update {
                    it.copy(nearbyLocation = nearestLocated(stops, at?.orderIndex ?: home?.orderIndex ?: Int.MAX_VALUE))
                }
                if (at != null) {
                    // The slot says which day this starts on; no GPS fix improves on that.
                    _uiState.update { it.copy(arrivalDate = at.date) }
                } else if (trip == null || trip.status == TripStatus.ACTIVE) {
                    // Logging where you are: immediately try a GPS fix — unless a share
                    // already said where, in which case your own position is beside the point.
                    if (!route.fromShare) _uiState.update { it.copy(autoLocatePending = true) }
                } else {
                    // Planning ahead or back-filling a finished trip: no GPS fix
                    // (your couch is not the campsite), arrival chains from the last stop.
                    val last = stops
                        .filterNot { it.state == StopState.SKIPPED || it.id == home?.id }
                        .maxByOrNull { it.orderIndex }
                    val arrival = last?.arrivalDate?.plusDays(last.nights.toLong()) ?: trip.startDate
                    _uiState.update { it.copy(arrivalDate = arrival) }
                }
                applyShared()
                return@launch
            }

            existing = stops.find { it.id == route.stopId }
            existing?.let { stop ->
                _uiState.update {
                    it.copy(
                        nearbyLocation = nearestLocated(stops, stop.orderIndex),
                        name = stop.name,
                        arrivalDate = stop.arrivalDate,
                        nights = stop.nights,
                        campingCost = if (stop.campingCostTotal == 0.0) "" else stop.campingCostTotal.toString(),
                        location = stop.location,
                        notes = stop.notes,
                        kind = stop.kind,
                        isNew = false,
                        placeId = stop.placeId,
                        locationCachedAt = stop.locationCachedAt,
                    )
                }
                stop.location?.let { resolvePlaceName(it, autoFillName = false) }
            }
        }
    }

    /** A place shared into the app: what the map picker delivers, minus the Places clock. */
    private fun applyShared() {
        val name = route.placeName.orEmpty()
        val at = if (route.lat != null && route.lon != null) LatLng(route.lat, route.lon) else null
        if (at != null) {
            return setPickedLocation(at, name, "", route.placeId.orEmpty(), fromPlaces = false)
        }
        // No coordinate, but a place id can still fetch one: PlaceCacheSweeper picks up a
        // stop that has an id and nowhere to be.
        _uiState.update { it.copy(name = name.ifBlank { it.name }, placeId = route.placeId) }
    }

    fun autoLocateHandled() = _uiState.update { it.copy(autoLocatePending = false) }

    fun setName(value: String) = _uiState.update { it.copy(name = value) }
    fun setArrivalDate(value: LocalDate) = _uiState.update { it.copy(arrivalDate = value) }
    fun setNights(value: Int) = _uiState.update { it.copy(nights = value.coerceIn(0, 365)) }
    fun setCampingCost(value: String) = _uiState.update { it.copy(campingCost = value) }
    fun setNotes(value: String) = _uiState.update { it.copy(notes = value) }

    fun setKind(value: StopKind) {
        // Home is a place you already have: choosing the kind puts the stop there.
        if (value == StopKind.HOME) {
            val settings = _uiState.value.settings
            settings.homeLocation?.let { setPickedLocation(it, HomeStop.name(settings), "", fromPlaces = false) }
        }
        _uiState.update {
            when {
                !value.isStay -> it.copy(kind = value, nights = 0, campingCost = "")
                !it.isStay -> it.copy(kind = value, nights = 1)
                else -> it.copy(kind = value)
            }
        }
    }

    fun setLocation(location: LatLng?) {
        val rounded = location?.let(::round)
        // A GPS fix or crosshair pick is ours to keep — no place id, no expiry.
        _uiState.update {
            it.copy(location = rounded, locationName = null, placeId = null, locationCachedAt = null)
        }
        rounded?.let { resolvePlaceName(it, autoFillName = true) }
    }

    /** The opportunistic GPS fix for a new stop is advisory: it never displaces a
     *  location the user already searched for or picked while it was in flight. */
    fun setAutoLocation(location: LatLng) {
        if (_uiState.value.location != null) return
        setLocation(location)
    }

    // ~1 m precision is plenty for a campsite; keeps the label readable.
    private fun round(location: LatLng) = LatLng(
        Math.round(location.latitude * 100_000.0) / 100_000.0,
        Math.round(location.longitude * 100_000.0) / 100_000.0,
    )

    /**
     * Result of the map picker. Choosing a named place is an explicit act, so it names
     * the stop — that is the default you wanted when you went looking for it. Type over
     * it afterwards and the name sticks until you pick a different place.
     *
     * It never routes through setLocation: that would drop the name and reverse geocode
     * over it. Moving the location in the same update also retires any geocode in flight.
     */
    fun setPickedLocation(
        location: LatLng,
        name: String,
        label: String,
        placeId: String = "",
        // A shared link hands over a coordinate lifted from a URL, not one the Places API
        // gave us: it carries no §14.3 clock, so PlaceCacheSweeper must leave it alone.
        fromPlaces: Boolean = true,
    ) {
        if (name.isBlank()) return setLocation(location)
        autoFilledName = name
        _uiState.update { state ->
            state.copy(
                location = round(location),
                locationName = label.ifBlank { name },
                name = name,
                // A Google place is kept by id; its coordinate is only cached (30 days).
                placeId = placeId.ifBlank { null },
                locationCachedAt = placeId.ifBlank { null }?.takeIf { fromPlaces }?.let { LocalDate.now() },
            )
        }
    }

    private fun resolvePlaceName(location: LatLng, autoFillName: Boolean) {
        val resolver = placeNameResolver ?: return
        viewModelScope.launch {
            val place = resolver.placeName(location) ?: return@launch
            _uiState.update { state ->
                // Ignore a late result for a location that has since changed.
                if (state.location != location) return@update state
                val name = if (autoFillName && (state.name.isBlank() || state.name == autoFilledName)) {
                    autoFilledName = place
                    place
                } else {
                    state.name
                }
                state.copy(locationName = place, name = name)
            }
        }
    }

    private var saving = false

    /**
     * Saves the stop and moves the plan behind it in the same write: a new stop takes its
     * slot and pushes the rest back by the nights it takes, a moved departure shifts the
     * planned schedule along. One `upsertStops` for all of it, so nothing — not the
     * timeline, not a route being written back — ever sees the plan half-moved.
     */
    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave || saving) return
        saving = true
        viewModelScope.launch {
            try {
                val stops = tripRepository.stops(route.tripId).first()
                // The stop as it is now, not as it was when the editor opened: a route or a
                // height written to it meanwhile must survive the save.
                val old = existing?.let { known -> stops.find { it.id == known.id } ?: known }
                val at = if (old == null) slot(stops) else null
                val base = old ?: Stop(
                    tripId = route.tripId,
                    orderIndex = at ?: (stops.maxOfOrNull { it.orderIndex } ?: -1) + 1,
                )
                val parsed = parseDecimal(state.campingCost)
                val nights = if (state.isStay) state.nights else 0
                // No parseable price means "estimate at the kind's default rate" —
                // except kinds free by nature, and unchanged legacy known-zero stops.
                val costKnown = parsed != null ||
                    state.kind == StopKind.FREE_CAMP || !state.isStay ||
                    (old != null && old.costKnown && old.campingCostTotal == 0.0 && old.kind == state.kind)
                val saved = base.copy(
                    name = state.savedName(),
                    arrivalDate = state.arrivalDate,
                    nights = nights,
                    campingCostTotal = if (state.isStay) parsed ?: 0.0 else 0.0,
                    location = state.location,
                    notes = state.notes.trim(),
                    kind = state.kind,
                    costKnown = costKnown,
                    placeId = state.placeId,
                    locationCachedAt = state.locationCachedAt,
                )
                val writes = if (old == null) {
                    // The stops from the slot on move down one to make room, and an inserted
                    // stay pushes them back by the nights it takes.
                    val shoved = stops.filter { at != null && it.orderIndex >= at }
                        .map { it.copy(orderIndex = it.orderIndex + 1) }
                    val pushed = insertAt?.let { DateCascade.shift(shoved, it, nights.toLong()) }
                        .orEmpty().associateBy { it.id }
                    shoved.map { pushed[it.id] ?: it } + saved
                } else {
                    // A moved departure shifts the downstream planned schedule along.
                    val days = ChronoUnit.DAYS.between(
                        old.arrivalDate.plusDays(old.nights.toLong()),
                        state.arrivalDate.plusDays(nights.toLong()),
                    )
                    listOf(saved) + DateCascade.shift(stops, old.orderIndex, days)
                }
                tripRepository.upsertStops(writes)
                onSaved()
            } finally {
                saving = false
            }
        }
    }

    /**
     * The slot a new stop takes, or null to append: the row it was inserted in front of;
     * mid-trip with check-ins, right after the last done stop; on a plan that ends with
     * the drive home, in front of that — pushing the plan back like any insertion.
     */
    private fun slot(stops: List<Stop>): Int? {
        val lastDone = stops.filter { it.state == StopState.DONE }.maxOfOrNull { it.orderIndex }
        return when {
            insertAt != null -> insertAt
            tripStatus == TripStatus.ACTIVE && lastDone != null -> lastDone + 1
            else -> HomeStop.returning(stops)?.orderIndex?.also { insertAt = it }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val stop = existing ?: return
        viewModelScope.launch {
            tripRepository.deleteStop(stop.tripId, stop.id)
            onDeleted()
        }
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            StopEditViewModel(
                createSavedStateHandle(),
                container.tripRepository,
                container.settingsRepository,
                this[APPLICATION_KEY]?.let(::PlaceNameResolver),
            )
        }
    }
}
