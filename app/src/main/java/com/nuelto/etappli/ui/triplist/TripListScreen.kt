package com.nuelto.etappli.ui.triplist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.domain.EstimateBreakdown
import com.nuelto.etappli.ui.formatCurrency
import com.nuelto.etappli.ui.formatTripDates
import com.nuelto.etappli.ui.theme.statusColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(
    onTripClick: (String) -> Unit,
    onAddTrip: (planned: Boolean) -> Unit,
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: TripListViewModel = viewModel(factory = TripListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var fabMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(fabMenuExpanded) { fabMenuExpanded = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trips") },
                actions = {
                    IconButton(onClick = onOpenMap) {
                        Icon(Icons.Default.Map, contentDescription = "All trips map")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnimatedVisibility(fabMenuExpanded) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                fabMenuExpanded = false
                                onAddTrip(true)
                            },
                            icon = { Icon(Icons.Default.Route, contentDescription = null) },
                            text = { Text("Plan a tour") },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                        ExtendedFloatingActionButton(
                            onClick = {
                                fabMenuExpanded = false
                                onAddTrip(false)
                            },
                            icon = { Icon(Icons.Default.Luggage, contentDescription = null) },
                            text = { Text("Log a trip") },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    }
                }
                FloatingActionButton(onClick = { fabMenuExpanded = !fabMenuExpanded }) {
                    Icon(
                        if (fabMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (fabMenuExpanded) "Close add menu" else "New trip",
                    )
                }
            }
        },
    ) { padding ->
        if (!state.loading && state.isEmpty) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No trips yet.\nTap + to plan a tour or log a trip.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tripSection("On the road", TripStatus.ACTIVE, state.active, state, onTripClick)
                tripSection("Planned tours", TripStatus.PLANNED, state.planned, state, onTripClick)
                tripSection("Done", TripStatus.DONE, state.done, state, onTripClick)
            }
        }
    }
}

private fun LazyListScope.tripSection(
    title: String,
    status: TripStatus,
    trips: List<Trip>,
    state: TripListUiState,
    onTripClick: (String) -> Unit,
) {
    if (trips.isEmpty()) return
    item(key = "header-$title") {
        // Lifecycle color language: blue = planned, green = active, grey = done.
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = statusColor(status),
        )
    }
    items(trips, key = { it.id }) { trip ->
        TripCard(
            trip = trip,
            totalText = tripTotalText(trip, state),
            onClick = { onTripClick(trip.id) },
        )
    }
}

/** Planned/active trips show the live ≈ estimate; done trips their actuals (+ auto fuel). */
private fun tripTotalText(trip: Trip, state: TripListUiState): String {
    val currency = state.settings.currency
    val estimate: EstimateBreakdown? = state.estimates[trip.id]
    if (estimate != null) {
        val amount = formatCurrency(estimate.total, currency)
        return if (estimate.hasEstimates) "≈ $amount" else amount
    }
    val fuelEstimate = state.fuelEstimates[trip.id]
    return if (fuelEstimate != null) {
        // "≈": includes the automatic fuel estimate from driving distance.
        "≈ ${formatCurrency(trip.totalCost + fuelEstimate, currency)}"
    } else {
        formatCurrency(trip.totalCost, currency)
    }
}

@Composable
private fun TripCard(trip: Trip, totalText: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    trip.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    totalText,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (trip.status == TripStatus.DONE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        statusColor(trip.status)
                    },
                )
            }
            Text(
                formatTripDates(trip),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (trip.nights == 1) "1 night" else "${trip.nights} nights",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
