package com.nuelto.etappli.ui.tripedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nuelto.etappli.containerViewModelFactory
import com.nuelto.etappli.data.TripRepository
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.domain.title
import com.nuelto.etappli.ui.nav.TripEditRoute
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
    val isPlan: Boolean = false,
    val loaded: Boolean = false,
    // What the trip is called while [name] is blank (domain/TripName); empty until it exists.
    val autoName: String = "",
) {
    val nameHint: String
        get() = if (autoName.isBlank()) "Optional — named after its stops" else "Leave blank to call it \"$autoName\""
}

/** Edits a trip's own fields, and logs a past trip; tours are planned without a form (NewPlan). */
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
                        isPlan = trip.status == TripStatus.PLANNED,
                        loaded = true,
                        autoName = trip.copy(name = "").title,
                    )
                }
            }
        }
    }

    fun setName(value: String) = _uiState.update { it.copy(name = value) }
    fun setStartDate(value: LocalDate) = _uiState.update { it.copy(startDate = value) }
    fun setEndDate(value: LocalDate?) = _uiState.update { it.copy(endDate = value) }
    fun setNotes(value: String) = _uiState.update { it.copy(notes = value) }

    private var saving = false

    fun save(onSaved: (tripId: String, isNew: Boolean) -> Unit) {
        if (saving) return
        saving = true
        val state = _uiState.value
        viewModelScope.launch {
            try {
                val base = existing ?: Trip()
                val endDate = state.endDate
                // Logging/editing a trip that already ended finishes it right away.
                val ended = endDate != null && endDate.isBefore(LocalDate.now())
                val status = if (base.status != TripStatus.PLANNED && ended) TripStatus.DONE else base.status
                val savedId = tripRepository.upsertTrip(
                    base.copy(
                        name = state.name.trim(),
                        startDate = state.startDate,
                        endDate = endDate,
                        notes = state.notes.trim(),
                        status = status,
                    ),
                )
                // Moving a plan's start moves its whole itinerary along.
                if (base.status == TripStatus.PLANNED) {
                    val days = ChronoUnit.DAYS.between(base.startDate, state.startDate)
                    if (days != 0L) {
                        tripRepository.stops(savedId).first().forEach {
                            tripRepository.upsertStop(it.copy(arrivalDate = it.arrivalDate.plusDays(days)))
                        }
                    }
                }
                onSaved(savedId, state.isNew)
            } finally {
                saving = false
            }
        }
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            TripEditViewModel(createSavedStateHandle(), container.tripRepository)
        }
    }
}
