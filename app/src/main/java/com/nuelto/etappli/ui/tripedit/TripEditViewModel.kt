package com.nuelto.etappli.ui.tripedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nuelto.etappli.containerViewModelFactory
import com.nuelto.etappli.data.TripRepository
import com.nuelto.etappli.domain.HomeStop
import com.nuelto.etappli.data.SettingsRepository
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
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
) {
    val canSave: Boolean get() = name.isNotBlank()
}

class TripEditViewModel(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository? = null,
) : ViewModel() {

    private val route: TripEditRoute = savedStateHandle.toRoute<TripEditRoute>()
    private val tripId: String? = route.tripId
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
                    it.copy(isPlan = route.planned, loaded = true)
                } else {
                    TripEditUiState(
                        name = trip.name,
                        startDate = trip.startDate,
                        endDate = trip.endDate,
                        notes = trip.notes,
                        isNew = false,
                        isPlan = trip.status == TripStatus.PLANNED,
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

    private var saving = false

    fun save(onSaved: (tripId: String, isNew: Boolean) -> Unit) {
        val state = _uiState.value
        if (!state.canSave || saving) return
        saving = true
        viewModelScope.launch {
            try {
                val base = existing ?: Trip()
                val endDate = state.endDate
                val status = when {
                    state.isNew && state.isPlan -> TripStatus.PLANNED
                    // Logging/editing a trip that already ended finishes it right away.
                    base.status != TripStatus.PLANNED && endDate != null && endDate.isBefore(LocalDate.now()) ->
                        TripStatus.DONE
                    else -> base.status
                }
                val savedId = tripRepository.upsertTrip(
                    base.copy(
                        name = state.name.trim(),
                        startDate = state.startDate,
                        endDate = endDate,
                        notes = state.notes.trim(),
                        status = status,
                    ),
                )
                // A fresh plan sets out from home and comes back to it, when there is one.
                if (state.isNew && status == TripStatus.PLANNED) {
                    settingsRepository?.settings()?.first()
                        ?.let { HomeStop.forNewPlan(savedId, it, state.startDate) }
                        ?.forEach { tripRepository.upsertStop(it) }
                }
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
            TripEditViewModel(
                createSavedStateHandle(),
                container.tripRepository,
                container.settingsRepository,
            )
        }
    }
}
