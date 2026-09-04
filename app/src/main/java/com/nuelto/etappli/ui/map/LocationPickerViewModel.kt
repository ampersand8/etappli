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
    // What the typing so far could mean: a list to pick from, located once picked.
    val predictions: List<PlaceSuggestion> = emptyList(),
    // What a submitted search found, each with a coordinate: the pins on the map.
    val results: List<PlaceSuggestion> = emptyList(),
    val status: PlaceSearchStatus = PlaceSearchStatus.IDLE,
    val selected: PlaceSuggestion? = null,
    val photo: ByteArray? = null,
    // The chosen place's card shows everything, or shrinks to a strip so the map around
    // the place can be looked at.
    val expanded: Boolean = true,
) {
    /** Whether back has something of the picker's own to undo before it closes it. */
    val canGoBack: Boolean
        get() = selected != null || query.isNotEmpty() || results.isNotEmpty()
}

/**
 * The picker map's search, the way a maps app works: typing offers predictions, the
 * search as submitted drops pins, and a pin, a prediction or a POI opens a card. Search
 * is additive — pressing and holding the map keeps working without it.
 */
class LocationPickerViewModel(
    private val placeSearch: PlaceSearch? = null,
    private val loadPhoto: (suspend (String) -> ByteArray?)? = null,
) : ViewModel() {

    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow(LocationPickerUiState())
    val uiState: StateFlow<LocationPickerUiState> = _uiState

    /** One lookup per pause, never for a fragment. [near] is the current map center, so
     *  hits rank around what you are looking at. The pins stay: they are behind the list. */
    fun setQuery(value: String, near: LatLng?, prefer: StopKind? = null) {
        searchJob?.cancel()
        _uiState.update { it.copy(query = value) }
        val query = value.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            _uiState.update {
                it.copy(
                    predictions = emptyList(),
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
                    predictions = found.orEmpty(),
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

    /**
     * The search as submitted: everything it matches, located, as pins. A single hit — a
     * name, as a rule — is opened straight away, the way a maps app does.
     */
    fun search(near: LatLng?) {
        searchJob?.cancel()
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        val search = placeSearch ?: return
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    predictions = emptyList(),
                    selected = null,
                    photo = null,
                    status = PlaceSearchStatus.SEARCHING,
                )
            }
            val found = search.find(query, near)
            // Done: opening the one hit must not cancel this very job.
            searchJob = null
            _uiState.update {
                it.copy(
                    results = found.orEmpty(),
                    status = when {
                        found == null -> PlaceSearchStatus.UNAVAILABLE
                        found.isEmpty() -> PlaceSearchStatus.EMPTY
                        else -> PlaceSearchStatus.IDLE
                    },
                )
            }
            found?.singleOrNull()?.let(::select)
        }
    }

    /** Back to the pins or the list, keeping the query — the card is otherwise a dead end. */
    fun clearSelection() = _uiState.update { it.copy(selected = null, photo = null) }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.value = LocationPickerUiState()
    }

    /** The card in full, or shrunk to a strip that leaves the map to look at. */
    fun setExpanded(expanded: Boolean) = _uiState.update { it.copy(expanded = expanded) }

    /**
     * Back peels the picker's own layers off, innermost first: a full card shrinks to a
     * strip, a strip goes away, a search is cleared. False = nothing left to undo, so the
     * picker itself closes.
     */
    fun back(): Boolean {
        val state = _uiState.value
        when {
            state.selected != null && state.expanded -> setExpanded(false)
            state.selected != null -> clearSelection()
            state.canGoBack -> clearSearch()
            else -> return false
        }
        return true
    }

    /**
     * A prediction names a place but knows neither where it is nor what it is like, and
     * a pin or a tapped POI knows only the former, so choosing any of them needs a second
     * lookup. The pins stay: they are where the place was found among.
     */
    fun select(place: PlaceSuggestion) {
        // A search still in flight would otherwise land and clear the choice.
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                selected = place,
                photo = null,
                expanded = true,
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
            it.copy(
                selected = PlaceSuggestion(name = "", label = "", location = at),
                photo = null,
                expanded = true,
            )
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
