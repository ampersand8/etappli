package dev.simon.camperexperience.ui.tripedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.simon.camperexperience.containerViewModelFactory
import dev.simon.camperexperience.data.TripRepository
import dev.simon.camperexperience.data.model.Trip
import dev.simon.camperexperience.ui.nav.TripEditRoute
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TripEditUiState(
    val name: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val notes: String = "",
    val isNew: Boolean = true,
    val loaded: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

class TripEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val tripId: String? = savedStateHandle.toRoute<TripEditRoute>().tripId
    private var existing: Trip? = null

    private val _uiState = MutableStateFlow(TripEditUiState())
    val uiState: StateFlow<TripEditUiState> = _uiState

    init {
        viewModelScope.launch {
            if (tripId != null) {
                existing = tripRepository.trip(tripId).first()
            }
            val trip = existing
            _uiState.update {
                if (trip == null) {
                    it.copy(loaded = true)
                } else {
                    TripEditUiState(
                        name = trip.name,
                        startDate = trip.startDate,
                        endDate = trip.endDate,
                        notes = trip.notes,
                        isNew = false,
                        loaded = true,
                    )
                }
            }
        }
    }

    fun setName(value: String) = _uiState.update { it.copy(name = value) }
    fun setStartDate(value: LocalDate) = _uiState.update { it.copy(startDate = value) }
    fun setEndDate(value: LocalDate?) = _uiState.update { it.copy(endDate = value) }
    fun setNotes(value: String) = _uiState.update { it.copy(notes = value) }

    fun save(onSaved: (tripId: String, isNew: Boolean) -> Unit) {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val base = existing ?: Trip()
            val savedId = tripRepository.upsertTrip(
                base.copy(
                    name = state.name.trim(),
                    startDate = state.startDate,
                    endDate = state.endDate,
                    notes = state.notes.trim(),
                ),
            )
            onSaved(savedId, state.isNew)
        }
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            TripEditViewModel(createSavedStateHandle(), container.tripRepository)
        }
    }
}
