package com.nuelto.etappli.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nuelto.etappli.containerViewModelFactory
import com.nuelto.etappli.data.SettingsRepository
import com.nuelto.etappli.data.TripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.domain.NewPlan
import com.nuelto.etappli.domain.PlaceSuggestion
import com.nuelto.etappli.domain.SharedPlace
import com.nuelto.etappli.ui.nav.AddToTripRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddToTripUiState(
    val place: SharedPlace = SharedPlace(),
    val active: List<Trip> = emptyList(),
    // Sorted like the trip list: tours soonest first, finished trips as they come.
    val planned: List<Trip> = emptyList(),
    val done: List<Trip> = emptyList(),
    val expanding: Boolean = false,
    // Looking the place up by name, because the link named it but pinned nothing.
    val locating: Boolean = false,
    // The link could not be followed, or the lookup did not come back: network, so retry.
    val expandFailed: Boolean = false,
    val loading: Boolean = true,
) {
    val hasTrips: Boolean get() = active.isNotEmpty() || planned.isNotEmpty() || done.isNotEmpty()

    /** Choosing a trip now would file the stop without the coordinate still on its way. */
    val busy: Boolean get() = expanding || locating

    /** A pin nobody named still needs a headline. */
    val title: String get() = place.name.ifBlank { "Shared pin" }
}

/**
 * The chooser a shared place lands on: which trip does this belong to? Everything about
 * the place is already parsed — the work here is following a short link, whose coordinate
 * sits behind a redirect, looking the place up when the link only named it, and planning
 * a tour for the place when there is none.
 */
class AddToTripViewModel(
    place: SharedPlace,
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository,
    private val expandLink: suspend (String) -> String? = { null },
    // Text Search (PlaceSearch.find), for a share that names the place but pins nothing.
    // Null when the map provider has no search: such a share then stays unpinned.
    private val find: (suspend (query: String, near: LatLng?) -> List<PlaceSuggestion>?)? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddToTripUiState(place = place))
    val uiState: StateFlow<AddToTripUiState> = _uiState

    init {
        tripRepository.trips()
            .onEach { trips ->
                _uiState.update { state ->
                    state.copy(
                        active = trips.filter { it.status == TripStatus.ACTIVE },
                        planned = trips.filter { it.status == TripStatus.PLANNED }
                            .sortedBy { it.startDate },
                        done = trips.filter { it.status == TripStatus.DONE },
                        loading = false,
                    )
                }
            }
            .launchIn(viewModelScope)
        expand()
    }

    /**
     * Follows a short link, then looks the place up if that still left it unpinned. A
     * no-op once there is nothing left to do; offered again when a step failed.
     */
    fun expand() {
        val link = _uiState.value.place.link
        if (link.isBlank()) return locate()
        _uiState.update { it.copy(expanding = true, expandFailed = false) }
        viewModelScope.launch {
            val expanded = _uiState.value.place.expandedWith(expandLink(link))
            _uiState.update {
                it.copy(place = expanded, expanding = false, expandFailed = expanded.link.isNotBlank())
            }
            locate()
        }
    }

    /**
     * The Maps app's links carry only the place's name and an ftid, and nothing turns an
     * ftid into a coordinate — but Text Search answers the name with the one place, the way
     * the picker opens a submitted name. Several hits pin nothing: a wrong campsite is worse
     * than none. Biased to the pin when there is an approximate one, else to home.
     */
    private fun locate() {
        val place = _uiState.value.place
        if (!place.needsLookup) return
        val search = find ?: return
        _uiState.update { it.copy(locating = true, expandFailed = false) }
        viewModelScope.launch {
            val near = place.location ?: settingsRepository.settings().first().homeLocation
            val found = search(place.name, near)
            _uiState.update {
                it.copy(place = it.place.locatedBy(found?.singleOrNull()), locating = false, expandFailed = found == null)
            }
        }
    }

    // A double-tap must not mint two tours.
    private var planning = false

    /** A tour planned on the spot for the place (domain/NewPlan); [onCreated] gets its id. */
    fun planTour(onCreated: (String) -> Unit) {
        if (planning) return
        planning = true
        viewModelScope.launch {
            try {
                onCreated(NewPlan.create(tripRepository, settingsRepository.settings().first()))
            } finally {
                planning = false
            }
        }
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            AddToTripViewModel(
                createSavedStateHandle().toRoute<AddToTripRoute>().sharedPlace(),
                container.tripRepository,
                container.settingsRepository,
                container.expandShareLink,
                container.mapProvider.placeSearch()?.let { it::find },
            )
        }
    }
}

/** Route scalars back into a place — kept out of the ViewModel so its tests need no Bundle. */
private fun AddToTripRoute.sharedPlace() = SharedPlace(
    name = name.orEmpty(),
    location = if (lat != null && lon != null) LatLng(lat, lon) else null,
    placeId = placeId.orEmpty(),
    link = link.orEmpty(),
    approximate = approximate,
)
