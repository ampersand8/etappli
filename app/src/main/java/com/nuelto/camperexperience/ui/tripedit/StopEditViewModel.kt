package com.nuelto.camperexperience.ui.tripedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nuelto.camperexperience.containerViewModelFactory
import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.location.PlaceNameResolver
import com.nuelto.camperexperience.ui.components.parseDecimal
import com.nuelto.camperexperience.ui.nav.StopEditRoute
import java.time.LocalDate
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
    val isNew: Boolean = true,
    val autoLocatePending: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()

    val locationLabel: String
        get() = locationName
            ?: location?.let { "${it.latitude}, ${it.longitude}" }
            ?: "Not set"
}

class StopEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val placeNameResolver: PlaceNameResolver? = null,
) : ViewModel() {

    private val route: StopEditRoute = savedStateHandle.toRoute<StopEditRoute>()
    val tripId: String get() = route.tripId
    private var existing: Stop? = null

    // Last name we auto-filled from reverse geocoding; anything else was typed by the
    // user and must never be overwritten.
    private var autoFilledName: String? = null

    private val _uiState = MutableStateFlow(StopEditUiState())
    val uiState: StateFlow<StopEditUiState> = _uiState

    init {
        if (route.stopId == null) {
            // New stop: the location section should immediately try a GPS fix.
            _uiState.update { it.copy(autoLocatePending = true) }
        }
        viewModelScope.launch {
            if (route.stopId != null) {
                existing = tripRepository.stops(route.tripId).first().find { it.id == route.stopId }
            }
            existing?.let { stop ->
                _uiState.value = StopEditUiState(
                    name = stop.name,
                    arrivalDate = stop.arrivalDate,
                    nights = stop.nights,
                    campingCost = if (stop.campingCostTotal == 0.0) "" else stop.campingCostTotal.toString(),
                    location = stop.location,
                    notes = stop.notes,
                    isNew = false,
                )
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

    fun setLocation(location: LatLng?) {
        // ~1 m precision is plenty for a campsite; keeps the label readable.
        val rounded = location?.let {
            LatLng(
                Math.round(location.latitude * 100_000.0) / 100_000.0,
                Math.round(location.longitude * 100_000.0) / 100_000.0,
            )
        }
        _uiState.update { it.copy(location = rounded, locationName = null) }
        rounded?.let { resolvePlaceName(it, autoFillName = true) }
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

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val base = existing ?: Stop(
                tripId = route.tripId,
                orderIndex = tripRepository.stops(route.tripId).first().size,
            )
            tripRepository.upsertStop(
                base.copy(
                    name = state.name.trim(),
                    arrivalDate = state.arrivalDate,
                    nights = state.nights,
                    campingCostTotal = parseDecimal(state.campingCost) ?: 0.0,
                    location = state.location,
                    notes = state.notes.trim(),
                ),
            )
            onSaved()
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
                this[APPLICATION_KEY]?.let(::PlaceNameResolver),
            )
        }
    }
}
