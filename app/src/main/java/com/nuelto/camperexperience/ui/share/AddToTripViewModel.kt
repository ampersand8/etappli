package com.nuelto.camperexperience.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nuelto.camperexperience.containerViewModelFactory
import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.domain.SharedPlace
import com.nuelto.camperexperience.ui.nav.AddToTripRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val expandFailed: Boolean = false,
    val loading: Boolean = true,
) {
    val hasTrips: Boolean get() = active.isNotEmpty() || planned.isNotEmpty() || done.isNotEmpty()

    /** A pin nobody named still needs a headline. */
    val title: String get() = place.name.ifBlank { "Shared pin" }
}

/**
 * The chooser a shared place lands on: which trip does this belong to? Everything about
 * the place is already parsed — the only work here is following a short link, whose
 * coordinate sits behind a redirect.
 */
class AddToTripViewModel(
    place: SharedPlace,
    tripRepository: TripRepository,
    private val expandLink: suspend (String) -> String? = { null },
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

    /** No-op unless the share was a short link; offered again when the fetch failed. */
    fun expand() {
        val link = _uiState.value.place.link.ifBlank { return }
        _uiState.update { it.copy(expanding = true, expandFailed = false) }
        viewModelScope.launch {
            val expanded = _uiState.value.place.expandedWith(expandLink(link))
            _uiState.update {
                it.copy(place = expanded, expanding = false, expandFailed = expanded.link.isNotBlank())
            }
        }
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            AddToTripViewModel(
                createSavedStateHandle().toRoute<AddToTripRoute>().sharedPlace(),
                container.tripRepository,
                container.expandShareLink,
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
