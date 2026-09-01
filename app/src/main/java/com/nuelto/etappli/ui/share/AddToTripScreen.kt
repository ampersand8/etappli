package com.nuelto.etappli.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.domain.SharedPlace
import com.nuelto.etappli.ui.formatTripDates
import com.nuelto.etappli.ui.theme.statusColor

/**
 * Where a place shared from Google Maps lands: name it, then pick the trip it belongs
 * to. Choosing one opens the stop editor on that trip with the place already filled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToTripScreen(
    onCancel: () -> Unit,
    onPlanTrip: () -> Unit,
    onAddTo: (tripId: String, place: SharedPlace) -> Unit,
    viewModel: AddToTripViewModel = viewModel(factory = AddToTripViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add to a trip") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "place") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(state.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        status(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.expanding) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (state.expandFailed) {
                        TextButton(onClick = viewModel::expand) { Text("Try again") }
                    }
                }
            }
            if (state.hasTrips) {
                item(key = "which") {
                    Text(
                        "Which trip?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                // Not while the link is still being followed: choosing now would file the
                // stop without the coordinate that is one redirect away.
                val ready = !state.expanding
                tripSection("On the road", TripStatus.ACTIVE, state.active, ready) { onAddTo(it, state.place) }
                tripSection("Planned tours", TripStatus.PLANNED, state.planned, ready) { onAddTo(it, state.place) }
                tripSection("Done", TripStatus.DONE, state.done, ready) { onAddTo(it, state.place) }
            } else if (!state.loading) {
                item(key = "empty") {
                    Box(Modifier.fillMaxWidth().padding(top = 24.dp), Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "No trips yet.\nPlan a tour, then add this place to it.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = onPlanTrip) { Text("Plan a tour") }
                        }
                    }
                }
            }
        }
    }
}

/** What we know about the place, in the order that matters to somebody about to camp. */
private fun status(state: AddToTripUiState): String = when {
    state.expanding -> "Opening the Google Maps link…"
    state.expandFailed -> "Couldn't open that link. Check your connection."
    state.place.approximate -> "Approximate spot — check the pin on the map after."
    state.place.location != null -> "Pin from Google Maps."
    else -> "No spot in that link — pick the place on the map after."
}

private fun LazyListScope.tripSection(
    title: String,
    status: TripStatus,
    trips: List<Trip>,
    enabled: Boolean,
    onClick: (String) -> Unit,
) {
    if (trips.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = statusColor(status),
        )
    }
    items(trips, key = { it.id }) { trip ->
        Card(
            onClick = { onClick(trip.id) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    trip.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    formatTripDates(trip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
