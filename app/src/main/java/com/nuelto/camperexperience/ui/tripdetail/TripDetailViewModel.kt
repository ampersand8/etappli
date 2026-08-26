package com.nuelto.camperexperience.ui.tripdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nuelto.camperexperience.containerViewModelFactory
import com.nuelto.camperexperience.data.SettingsRepository
import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.domain.CostCalculator
import com.nuelto.camperexperience.ui.nav.TripDetailRoute
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TripDetailUiState(
    val trip: Trip? = null,
    val stops: List<Stop> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val breakdown: Map<ExpenseType, Double> = emptyMap(),
    val settings: UserSettings = UserSettings(),
    val loading: Boolean = true,
)

class TripDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val tripId: String = savedStateHandle.toRoute<TripDetailRoute>().tripId

    val uiState: StateFlow<TripDetailUiState> =
        combine(
            tripRepository.trip(tripId),
            tripRepository.stops(tripId),
            tripRepository.expenses(tripId),
            settingsRepository.settings(),
        ) { trip, stops, expenses, settings ->
            TripDetailUiState(
                trip = trip,
                stops = stops,
                expenses = expenses,
                breakdown = CostCalculator.breakdown(stops, expenses),
                settings = settings,
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

    fun deleteStop(stopId: String) {
        viewModelScope.launch { tripRepository.deleteStop(tripId, stopId) }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch { tripRepository.deleteExpense(tripId, expenseId) }
    }

    fun upsertExpense(expense: Expense) {
        viewModelScope.launch { tripRepository.upsertExpense(expense.copy(tripId = tripId)) }
    }

    fun deleteTrip(onDeleted: () -> Unit) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
            onDeleted()
        }
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            TripDetailViewModel(
                createSavedStateHandle(),
                container.tripRepository,
                container.settingsRepository,
            )
        }
    }
}
