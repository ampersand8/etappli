package com.nuelto.camperexperience.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.PlaceSearch
import com.nuelto.camperexperience.domain.PlaceSearchStatus
import com.nuelto.camperexperience.domain.PlaceSuggestion
import com.nuelto.camperexperience.location.PhotonPlaceSearch
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
)

/**
 * Debounced place search for the picker map: hits land as markers so you can pick the
 * one in the right spot. Search is additive — the crosshair keeps working without it.
 */
class LocationPickerViewModel(private val placeSearch: PlaceSearch? = null) : ViewModel() {

    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow(LocationPickerUiState())
    val uiState: StateFlow<LocationPickerUiState> = _uiState

    /** One lookup per pause, never for a fragment. [near] is the current map center, so
     *  hits rank around what you are looking at. */
    fun setQuery(value: String, near: LatLng?) {
        searchJob?.cancel()
        _uiState.update { it.copy(query = value) }
        val query = value.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            _uiState.update {
                it.copy(results = emptyList(), selected = null, status = PlaceSearchStatus.IDLE)
            }
            return
        }
        val search = placeSearch ?: return
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _uiState.update { it.copy(status = PlaceSearchStatus.SEARCHING) }
            val found = search.search(query, near)
            _uiState.update {
                it.copy(
                    results = found.orEmpty(),
                    selected = null,
                    status = when {
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
        _uiState.value = LocationPickerUiState()
    }

    fun select(place: PlaceSuggestion) = _uiState.update { it.copy(selected = place) }

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val MIN_QUERY_LENGTH = 3

        val Factory = viewModelFactory {
            initializer { LocationPickerViewModel(PhotonPlaceSearch()) }
        }
    }
}
