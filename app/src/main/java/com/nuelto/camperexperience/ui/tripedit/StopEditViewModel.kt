package com.nuelto.camperexperience.ui.tripedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nuelto.camperexperience.containerViewModelFactory
import com.nuelto.camperexperience.data.SettingsRepository
import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopKind
import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.domain.DateCascade
import com.nuelto.camperexperience.domain.PlaceSearch
import com.nuelto.camperexperience.domain.PlaceSuggestion
import com.nuelto.camperexperience.domain.searchBias
import com.nuelto.camperexperience.location.PhotonPlaceSearch
import com.nuelto.camperexperience.location.PlaceNameResolver
import com.nuelto.camperexperience.ui.components.parseDecimal
import com.nuelto.camperexperience.ui.nav.StopEditRoute
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Search feedback; IDLE also covers "results are showing" and "nothing typed yet". */
enum class PlaceSearchStatus { IDLE, SEARCHING, EMPTY, UNAVAILABLE }

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
    val placeQuery: String = "",
    val suggestions: List<PlaceSuggestion> = emptyList(),
    val searchStatus: PlaceSearchStatus = PlaceSearchStatus.IDLE,
    // No search backend wired (tests, or a future build without it) — hide the field.
    val searchEnabled: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()
    val isVisit: Boolean get() = kind == StopKind.VISIT

    val locationLabel: String
        get() = locationName
            ?: location?.let { "${it.latitude}, ${it.longitude}" }
            ?: "Not set"
}

class StopEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    settingsRepository: SettingsRepository,
    private val placeNameResolver: PlaceNameResolver? = null,
    private val placeSearch: PlaceSearch? = null,
) : ViewModel() {

    private val route: StopEditRoute = savedStateHandle.toRoute<StopEditRoute>()
    val tripId: String get() = route.tripId
    private var existing: Stop? = null
    private var tripStatus: TripStatus? = null

    // Last name we auto-filled from reverse geocoding or a suggestion; anything else was
    // typed by the user and must never be overwritten.
    private var autoFilledName: String? = null

    private var searchJob: Job? = null
    private var biasNear: LatLng? = null

    private val _uiState = MutableStateFlow(StopEditUiState(searchEnabled = placeSearch != null))
    val uiState: StateFlow<StopEditUiState> = _uiState

    init {
        viewModelScope.launch {
            val trip = tripRepository.trip(route.tripId).first()
            tripStatus = trip?.status
            val settings = settingsRepository.settings().first()
            _uiState.update { it.copy(tripStatus = trip?.status, settings = settings) }
            val stops = tripRepository.stops(route.tripId).first()

            if (route.stopId == null) {
                // A new stop lands at the end, so the whole trip is "before" it.
                biasNear = searchBias(stops, Int.MAX_VALUE)
                if (trip == null || trip.status == TripStatus.ACTIVE) {
                    // Logging where you are: immediately try a GPS fix.
                    _uiState.update { it.copy(autoLocatePending = true) }
                } else {
                    // Planning ahead or back-filling a finished trip: no GPS fix
                    // (your couch is not the campsite), arrival chains from the last stop.
                    val last = stops
                        .filterNot { it.state == StopState.SKIPPED }
                        .maxByOrNull { it.orderIndex }
                    val arrival = last?.arrivalDate?.plusDays(last.nights.toLong()) ?: trip.startDate
                    _uiState.update { it.copy(arrivalDate = arrival) }
                }
                return@launch
            }

            existing = stops.find { it.id == route.stopId }
            existing?.let { stop ->
                biasNear = searchBias(stops, stop.orderIndex)
                _uiState.update {
                    it.copy(
                        name = stop.name,
                        arrivalDate = stop.arrivalDate,
                        nights = stop.nights,
                        campingCost = if (stop.campingCostTotal == 0.0) "" else stop.campingCostTotal.toString(),
                        location = stop.location,
                        notes = stop.notes,
                        kind = stop.kind,
                        isNew = false,
                    )
                }
                stop.location?.let { resolvePlaceName(it, autoFillName = false) }
            }
        }
    }

    fun autoLocateHandled() = _uiState.update { it.copy(autoLocatePending = false) }

    fun setName(value: String) = _uiState.update { it.copy(name = value) }
    fun setArrivalDate(value: LocalDate) = _uiState.update { it.copy(arrivalDate = value) }
    fun setNights(value: Int) = _uiState.update { it.copy(nights = value.coerceIn(0, 365)) }
    fun setCampingCost(value: String) = _uiState.update { it.copy(campingCost = value) }
    fun setNotes(value: String) = _uiState.update { it.copy(notes = value) }

    fun setKind(value: StopKind) = _uiState.update {
        when {
            value == StopKind.VISIT -> it.copy(kind = value, nights = 0, campingCost = "")
            it.isVisit -> it.copy(kind = value, nights = 1)
            else -> it.copy(kind = value)
        }
    }

    fun setLocation(location: LatLng?) {
        val rounded = location?.let(::round)
        _uiState.update { it.copy(location = rounded, locationName = null) }
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

    /** Debounced type-ahead: one lookup per pause, never for a fragment. */
    fun setPlaceQuery(value: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(placeQuery = value) }
        val query = value.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            _uiState.update {
                it.copy(suggestions = emptyList(), searchStatus = PlaceSearchStatus.IDLE)
            }
            return
        }
        val search = placeSearch ?: return
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _uiState.update { it.copy(searchStatus = PlaceSearchStatus.SEARCHING) }
            val found = search.search(query, _uiState.value.location ?: biasNear)
            _uiState.update {
                it.copy(
                    suggestions = found.orEmpty(),
                    searchStatus = when {
                        found == null -> PlaceSearchStatus.UNAVAILABLE
                        found.isEmpty() -> PlaceSearchStatus.EMPTY
                        else -> PlaceSearchStatus.IDLE
                    },
                )
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(placeQuery = "", suggestions = emptyList(), searchStatus = PlaceSearchStatus.IDLE)
        }
    }

    /**
     * A suggestion names the place better than reverse geocoding could, so this never
     * routes through setLocation — that would drop the name and geocode over it. Moving
     * the location in the same update also retires any reverse geocode still in flight.
     */
    fun pickSuggestion(suggestion: PlaceSuggestion) {
        searchJob?.cancel()
        _uiState.update { state ->
            val fillName = state.name.isBlank() || state.name == autoFilledName
            if (fillName) autoFilledName = suggestion.name
            state.copy(
                location = round(suggestion.location),
                locationName = suggestion.label.ifBlank { suggestion.name },
                name = if (fillName) suggestion.name else state.name,
                placeQuery = "",
                suggestions = emptyList(),
                searchStatus = PlaceSearchStatus.IDLE,
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

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave || saving) return
        saving = true
        viewModelScope.launch {
            try {
                val old = existing
                val base = old ?: Stop(tripId = route.tripId, orderIndex = newOrderIndex())
                val parsed = parseDecimal(state.campingCost)
                val nights = if (state.isVisit) 0 else state.nights
                // No parseable price means "estimate at the kind's default rate" —
                // except kinds free by nature, and unchanged legacy known-zero stops.
                val costKnown = parsed != null ||
                    state.kind == StopKind.FREE_CAMP || state.isVisit ||
                    (old != null && old.costKnown && old.campingCostTotal == 0.0 && old.kind == state.kind)
                tripRepository.upsertStop(
                    base.copy(
                        name = state.name.trim(),
                        arrivalDate = state.arrivalDate,
                        nights = nights,
                        campingCostTotal = if (state.isVisit) 0.0 else parsed ?: 0.0,
                        location = state.location,
                        notes = state.notes.trim(),
                        kind = state.kind,
                        costKnown = costKnown,
                    ),
                )
                // A moved departure shifts the downstream planned schedule along.
                if (old != null) {
                    val days = ChronoUnit.DAYS.between(
                        old.arrivalDate.plusDays(old.nights.toLong()),
                        state.arrivalDate.plusDays(nights.toLong()),
                    )
                    DateCascade.shift(tripRepository.stops(route.tripId).first(), old.orderIndex, days)
                        .forEach { tripRepository.upsertStop(it) }
                }
                onSaved()
            } finally {
                saving = false
            }
        }
    }

    /**
     * New stops append — except mid-trip with check-ins, where a spontaneous stop
     * slots in right after the last done stop (upcoming stops move down one).
     */
    private suspend fun newOrderIndex(): Int {
        val stops = tripRepository.stops(route.tripId).first()
        val lastDone = stops.filter { it.state == StopState.DONE }.maxOfOrNull { it.orderIndex }
        if (tripStatus != TripStatus.ACTIVE || lastDone == null) {
            return (stops.maxOfOrNull { it.orderIndex } ?: -1) + 1
        }
        val insertAt = lastDone + 1
        stops.filter { it.orderIndex >= insertAt }.forEach {
            tripRepository.upsertStop(it.copy(orderIndex = it.orderIndex + 1))
        }
        return insertAt
    }

    fun delete(onDeleted: () -> Unit) {
        val stop = existing ?: return
        viewModelScope.launch {
            tripRepository.deleteStop(stop.tripId, stop.id)
            onDeleted()
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val MIN_QUERY_LENGTH = 3

        val Factory = containerViewModelFactory { container ->
            StopEditViewModel(
                createSavedStateHandle(),
                container.tripRepository,
                container.settingsRepository,
                this[APPLICATION_KEY]?.let(::PlaceNameResolver),
                PhotonPlaceSearch(),
            )
        }
    }
}
