package com.nuelto.camperexperience.ui.triplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuelto.camperexperience.containerViewModelFactory
import com.nuelto.camperexperience.data.SettingsRepository
import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.domain.EstimateBreakdown
import com.nuelto.camperexperience.domain.FuelEstimator
import com.nuelto.camperexperience.domain.TripEstimator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TripListUiState(
    val active: List<Trip> = emptyList(),
    // Planned tours sorted soonest-first; done trips newest-first.
    val planned: List<Trip> = emptyList(),
    val done: List<Trip> = emptyList(),
    // Trip id -> automatic fuel estimate for done trips with no recorded fuel expense.
    val fuelEstimates: Map<String, Double> = emptyMap(),
    // Trip id -> live ≈ estimate for planned/active trips.
    val estimates: Map<String, EstimateBreakdown> = emptyMap(),
    val settings: UserSettings = UserSettings(),
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = active.isEmpty() && planned.isEmpty() && done.isEmpty()
}

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
            val done = trips.filter { it.status == TripStatus.DONE }
            val estimates = trips.filterNot { it.status == TripStatus.DONE }.associate { trip ->
                trip.id to TripEstimator.estimate(
                    stops.filter { it.tripId == trip.id },
                    expenses.filter { it.tripId == trip.id },
                    settings,
                )
            }
            val fuelEstimates = done.mapNotNull { trip ->
                FuelEstimator.autoTripFuelCost(
                    stops.filter { it.tripId == trip.id },
                    expenses.filter { it.tripId == trip.id },
                    settings,
                )?.let { trip.id to it }
            }.toMap()
            TripListUiState(
                active = trips.filter { it.status == TripStatus.ACTIVE },
                planned = trips.filter { it.status == TripStatus.PLANNED }.sortedBy { it.startDate },
                done = done,
                fuelEstimates = fuelEstimates,
                estimates = estimates,
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
