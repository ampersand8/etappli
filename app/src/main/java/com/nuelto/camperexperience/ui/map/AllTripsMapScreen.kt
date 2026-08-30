package com.nuelto.camperexperience.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuelto.camperexperience.containerViewModelFactory
import com.nuelto.camperexperience.data.SettingsRepository
import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.domain.TripMapData
import com.nuelto.camperexperience.domain.CurrentStop
import com.nuelto.camperexperience.domain.EstimateBreakdown
import com.nuelto.camperexperience.domain.TripEstimator
import com.nuelto.camperexperience.ui.formatCurrency
import com.nuelto.camperexperience.ui.formatDate
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AllTripsMapUiState(
    val data: List<TripMapData> = emptyList(),
    // Trip id -> live ≈ estimate for planned/active trips (recorded totals mislead there).
    val estimates: Map<String, EstimateBreakdown> = emptyMap(),
    val settings: UserSettings = UserSettings(),
)

class AllTripsMapViewModel(
    tripRepository: TripRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AllTripsMapUiState> =
        combine(
            tripRepository.trips(),
            tripRepository.allStops(),
            tripRepository.allExpenses(),
            settingsRepository.settings(),
        ) { trips, stops, expenses, settings ->
            AllTripsMapUiState(
                data = trips.map { trip ->
                    val tripStops = stops.filter { it.tripId == trip.id }
                    TripMapData(
                        trip = trip,
                        stops = tripStops,
                        currentStopId = if (trip.status == TripStatus.ACTIVE) {
                            CurrentStop.of(tripStops, LocalDate.now())
                        } else {
                            null
                        },
                    )
                },
                estimates = trips.filterNot { it.status == TripStatus.DONE }.associate { trip ->
                    trip.id to TripEstimator.estimate(
                        stops.filter { it.tripId == trip.id },
                        expenses.filter { it.tripId == trip.id },
                        settings,
                    )
                },
                settings = settings,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AllTripsMapUiState())

    companion object {
        val Factory = containerViewModelFactory { container ->
            AllTripsMapViewModel(container.tripRepository, container.settingsRepository)
        }
    }
}

/** PLANNED/ACTIVE trips render the live ≈ estimate; done trips their actuals. */
private fun tripTotalText(trip: Trip, state: AllTripsMapUiState): String {
    val estimate = state.estimates[trip.id] ?: return formatCurrency(trip.totalCost, state.settings.currency)
    val amount = formatCurrency(estimate.total, state.settings.currency)
    return if (estimate.hasEstimates) "≈ $amount" else amount
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllTripsMapScreen(
    tripId: String?,
    onBack: () -> Unit,
    onOpenTrip: (String) -> Unit,
    viewModel: AllTripsMapViewModel = viewModel(factory = AllTripsMapViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val shown = if (tripId == null) state.data else state.data.filter { it.trip.id == tripId }
    var selected by remember { mutableStateOf<Pair<TripMapData, Stop>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tripId == null) "All trips" else shown.firstOrNull()?.trip?.name ?: "Trip") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            TripMap(
                data = shown,
                modifier = Modifier.fillMaxSize(),
                onStopClick = { clickedTripId, stopId ->
                    val entry = shown.find { it.trip.id == clickedTripId } ?: return@TripMap
                    val stop = entry.stops.find { it.id == stopId } ?: return@TripMap
                    selected = entry to stop
                },
            )

            selected?.let { (entry, stop) ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.trip.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${stop.name} · ${formatDate(stop.arrivalDate)} · " +
                                (if (stop.nights == 1) "1 night" else "${stop.nights} nights"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Trip total: ${tripTotalText(entry.trip, state)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Row {
                                TextButton(onClick = { selected = null }) { Text("Close") }
                                if (tripId == null) {
                                    TextButton(onClick = { onOpenTrip(entry.trip.id) }) { Text("Open trip") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
