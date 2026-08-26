package com.nuelto.camperexperience.ui.triplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuelto.camperexperience.containerViewModelFactory
import com.nuelto.camperexperience.data.SettingsRepository
import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.domain.FuelEstimator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TripListUiState(
    val trips: List<Trip> = emptyList(),
    // Trip id -> automatic fuel estimate, only for trips with no recorded fuel expense.
    val fuelEstimates: Map<String, Double> = emptyMap(),
    val settings: UserSettings = UserSettings(),
    val loading: Boolean = true,
)

class TripListViewModel(
    tripRepository: TripRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<TripListUiState> =
        combine(
            tripRepository.trips(),
            tripRepository.allStops(),
            tripRepository.allExpenses(),
            settingsRepository.settings(),
        ) { trips, stops, expenses, settings ->
            val estimates = trips.mapNotNull { trip ->
                FuelEstimator.autoTripFuelCost(
                    stops.filter { it.tripId == trip.id },
                    expenses.filter { it.tripId == trip.id },
                    settings,
                )?.let { trip.id to it }
            }.toMap()
            TripListUiState(
                trips = trips,
                fuelEstimates = estimates,
                settings = settings,
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripListUiState())

    companion object {
        val Factory = containerViewModelFactory { container ->
            TripListViewModel(container.tripRepository, container.settingsRepository)
        }
    }
}
