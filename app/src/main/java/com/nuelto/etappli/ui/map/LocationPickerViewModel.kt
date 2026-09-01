package com.nuelto.etappli.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuelto.etappli.containerViewModelFactory
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.domain.PlaceSearch
import com.nuelto.etappli.domain.PlaceSearchStatus
import com.nuelto.etappli.domain.PlaceSuggestion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LocationPickerUiState(
    val query: String = "",
    val results: List<PlaceSuggestion> = emptyList(),
    val status: PlaceSearchStatus = PlaceSearchStatus.IDLE,
    val selected: PlaceSuggestion? = null,
    val photo: ByteArray? = null,
)

/**
 * Debounced place search for the picker map: hits land as markers so you can pick the
 * one in the right spot. Search is additive — the crosshair keeps working without it.
 */
class LocationPickerViewModel(
    private val placeSearch: PlaceSearch? = null,
    private val loadPhoto: (suspend (String) -> ByteArray?)? = null,
) : ViewModel() {

    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow(LocationPickerUiState())
    val uiState: StateFlow<LocationPickerUiState> = _uiState

    /** One lookup per pause, never for a fragment. [near] is the current map center, so
     *  hits rank around what you are looking at. */
    fun setQuery(value: String, near: LatLng?, prefer: StopKind? = null) {
        searchJob?.cancel()
        _uiState.update { it.copy(query = value) }
        val query = value.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    selected = null,
                    photo = null,
                    status = PlaceSearchStatus.IDLE,
                )
            }
            return
        }
        val search = placeSearch ?: return
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _uiState.update { it.copy(status = PlaceSearchStatus.SEARCHING) }
            val found = search.search(query, near, prefer)
            _uiState.update {
                it.copy(
                    results = found.orEmpty(),
                    selected = null,
                    photo = null,
                    status = when {
                        found == null -> PlaceSearchStatus.UNAVAILABLE
                        found.isEmpty() -> PlaceSearchStatus.EMPTY
                        else -> PlaceSearchStatus.IDLE
                    },
                )
            }
        }
    }

    /** Back to the results, keeping the query — the card is otherwise a dead end. */
    fun clearSelection() = _uiState.update { it.copy(selected = null, photo = null) }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = LocationPickerUiState()
    }

    /**
     * A prediction names a place but knows neither where it is nor what it is like, and
     * a tapped POI knows only the former, so choosing either needs a second lookup.
     */
    fun select(place: PlaceSuggestion) {
        // A search still in flight would otherwise land and clear the choice.
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                selected = place,
                photo = null,
                status = if (place.location == null) PlaceSearchStatus.SEARCHING else PlaceSearchStatus.IDLE,
            )
        }
        if (place.location != null && place.details != null) return fetchPhoto(place)
        val search = placeSearch ?: return
        viewModelScope.launch {
            val resolved = search.resolve(place)
            _uiState.update { state ->
                // Ignore a late answer for something the user has moved on from.
                if (state.selected != place) return@update state
                state.copy(
                    selected = resolved ?: place,
                    status = if (resolved?.location == null) {
                        PlaceSearchStatus.UNAVAILABLE
                    } else {
                        PlaceSearchStatus.IDLE
                    },
                )
            }
            resolved?.let(::fetchPhoto)
        }
    }

    /** The picture arrives after the card, rather than holding it back. */
    private fun fetchPhoto(place: PlaceSuggestion) {
        val handle = place.details?.photo?.takeIf { it.isNotBlank() } ?: return
        val load = loadPhoto ?: return
        viewModelScope.launch {
            val image = load(handle) ?: return@launch
            _uiState.update { if (it.selected == place) it.copy(photo = image) else it }
        }
    }

    /** Press and hold on the map. No name — the editor reverse-geocodes one. */
    fun dropPin(at: LatLng) {
        searchJob?.cancel()
        _uiState.update {
            it.copy(selected = PlaceSuggestion(name = "", label = "", location = at), photo = null)
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val MIN_QUERY_LENGTH = 3

        // Search must match the map: Google Places may not be shown on a non-Google map.
        val Factory = containerViewModelFactory { container ->
            LocationPickerViewModel(
                container.mapProvider.placeSearch(),
                container.mapProvider::photo,
            )
        }
    }
}
