package dev.simon.camperexperience.ui.triplist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.simon.camperexperience.containerViewModelFactory
import dev.simon.camperexperience.data.SettingsRepository
import dev.simon.camperexperience.data.TripRepository
import dev.simon.camperexperience.data.model.Trip
import dev.simon.camperexperience.data.model.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TripListUiState(
    val trips: List<Trip> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val loading: Boolean = true,
)

class TripListViewModel(
    tripRepository: TripRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<TripListUiState> =
        combine(tripRepository.trips(), settingsRepository.settings()) { trips, settings ->
            TripListUiState(trips = trips, settings = settings, loading = false)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripListUiState())

    companion object {
        val Factory = containerViewModelFactory { container ->
            TripListViewModel(container.tripRepository, container.settingsRepository)
        }
    }
}
