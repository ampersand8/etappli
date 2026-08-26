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
    val latText: String = "",
    val lonText: String = "",
    val notes: String = "",
    val isNew: Boolean = true,
) {
    val canSave: Boolean get() = name.isNotBlank()
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
                    latText = stop.location?.latitude?.toString() ?: "",
                    lonText = stop.location?.longitude?.toString() ?: "",
                    notes = stop.notes,
                    isNew = false,
                )
            }
        }
    }

    fun setName(value: String) = _uiState.update { it.copy(name = value) }
    fun setArrivalDate(value: LocalDate) = _uiState.update { it.copy(arrivalDate = value) }
    fun setNights(value: Int) = _uiState.update { it.copy(nights = value.coerceIn(0, 365)) }
    fun setCampingCost(value: String) = _uiState.update { it.copy(campingCost = value) }
    fun setNotes(value: String) = _uiState.update { it.copy(notes = value) }

    fun setLocation(location: LatLng?) {
        // ~1 m precision is plenty for a campsite; keeps the fields readable.
        val rounded = location?.let {
            LatLng(
                Math.round(location.latitude * 100_000.0) / 100_000.0,
                Math.round(location.longitude * 100_000.0) / 100_000.0,
            )
        }
        _uiState.update {
            it.copy(
                location = rounded,
                latText = rounded?.latitude?.toString() ?: "",
                lonText = rounded?.longitude?.toString() ?: "",
            )
        }
        rounded?.let(::autoFillNameFrom)
    }

    private fun autoFillNameFrom(location: LatLng) {
        val resolver = placeNameResolver ?: return
        viewModelScope.launch {
            val place = resolver.placeName(location) ?: return@launch
            _uiState.update { state ->
                if (state.name.isBlank() || state.name == autoFilledName) {
                    autoFilledName = place
                    state.copy(name = place)
                } else {
                    state
                }
            }
        }
    }

    fun setLatText(value: String) = _uiState.update { it.copy(latText = value).withParsedLocation() }
    fun setLonText(value: String) = _uiState.update { it.copy(lonText = value).withParsedLocation() }

    private fun StopEditUiState.withParsedLocation(): StopEditUiState {
        val lat = parseDecimal(latText)
        val lon = parseDecimal(lonText)
        val loc = if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            LatLng(lat, lon)
        } else {
            null
        }
        return copy(location = loc)
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
