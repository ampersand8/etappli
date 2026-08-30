package com.nuelto.camperexperience.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.StopKind
import com.nuelto.camperexperience.domain.MapAccent
import com.nuelto.camperexperience.domain.MapMarker
import com.nuelto.camperexperience.domain.MapOverlay
import com.nuelto.camperexperience.domain.PlaceSearchStatus
import com.nuelto.camperexperience.domain.PlaceSuggestion

/**
 * Search for a place and pick it from the results, the way a maps app works — no
 * coordinates, no aiming. The hits also show on the map, so a tap there picks the same
 * thing. For somewhere with no name, press and hold the map to drop a pin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    initial: LatLng?,
    onPicked: (LatLng, PlaceSuggestion?) -> Unit,
    onCancel: () -> Unit,
    prefer: StopKind? = null,
    viewModel: LocationPickerViewModel = viewModel(factory = LocationPickerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val provider = LocalMapProvider.current
    val camera = provider.rememberCamera(start = initial, zoom = CLOSE_ZOOM)

    // New hits pull the camera over them; tapping one slides the crosshair onto it so
    // both ways of confirming agree, without yanking the zoom around.
    LaunchedEffect(state.results) {
        camera.apply(MapOverlay.frame(state.results.mapNotNull { it.location }))
    }
    LaunchedEffect(state.selected) { state.selected?.location?.let { camera.centerOn(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pick location") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            provider.Canvas(
                camera = camera,
                markers = state.markers(),
                routes = emptyList(),
                modifier = Modifier.fillMaxSize(),
                onMarkerClick = { _, id -> state.hit(id)?.also(viewModel::select) != null },
                onLongPress = viewModel::dropPin,
                onPoiClick = viewModel::select,
            )
            SearchOverlay(
                state = state,
                onQueryChange = { query -> viewModel.setQuery(query, camera.center, prefer) },
                onClear = viewModel::clearSearch,
                onSelect = viewModel::select,
                onUseSelected = { place ->
                    place.location?.let { onPicked(it, place.takeIf { p -> p.name.isNotBlank() }) }
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/** Hits as markers: the chosen one is filled, the rest are hollow rings. A pin dropped
 *  by hand is not in the results, so it gets a marker of its own. */
private fun LocationPickerUiState.markers(): List<MapMarker> {
    // Predictions have no coordinate until they are chosen, so only some hits show.
    val hits = results.mapIndexedNotNull { index, place ->
        place.location?.let {
            MapMarker(
                tripId = SEARCH_MARKERS,
                stopId = index.toString(),
                at = it,
                accent = MapAccent.PLANNED,
                hollow = place != selected,
            )
        }
    }
    val chosen = selected?.takeIf { it !in results }?.location?.let {
        MapMarker(SEARCH_MARKERS, DROPPED_PIN, it, MapAccent.PLANNED, hollow = false)
    }
    return hits + listOfNotNull(chosen)
}

private fun LocationPickerUiState.hit(markerId: String): PlaceSuggestion? =
    markerId.toIntOrNull()?.let { results.getOrNull(it) }

private const val SEARCH_MARKERS = "search"
private const val DROPPED_PIN = "pin"

/** Search field, its one-line status, and the card for whichever marker is tapped. */
@Composable
private fun SearchOverlay(
    state: LocationPickerUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelect: (PlaceSuggestion) -> Unit,
    onUseSelected: (PlaceSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(shadowElevation = 4.dp, shape = MaterialTheme.shapes.large) {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search a place") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        searchStatusText(state)?.let {
            Surface(shape = MaterialTheme.shapes.medium, shadowElevation = 2.dp) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        val selected = state.selected
        if (selected == null) {
            // The hits, as a list you can read — the markers are only there to show where.
            state.results.forEach { place ->
                Card(
                    onClick = {
                        keyboard?.hide()
                        onSelect(place)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(place.name, style = MaterialTheme.typography.titleSmall)
                        if (place.label.isNotBlank()) {
                            Text(
                                place.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        // A dropped pin has no name yet; the editor geocodes one.
                        Text(
                            selected.name.ifBlank { "Dropped pin" },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (selected.label.isNotBlank()) {
                            Text(
                                selected.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(
                        onClick = {
                            keyboard?.hide()
                            onUseSelected(selected)
                        },
                        // A prediction is still being looked up until it has a coordinate.
                        enabled = selected.location != null,
                    ) {
                        Text("Use this place")
                    }
                }
            }
        }
    }
}

/** The results list speaks for itself, so a hit needs no status line. */
private fun searchStatusText(state: LocationPickerUiState): String? = when (state.status) {
    PlaceSearchStatus.IDLE ->
        "Search, or press and hold the map to drop a pin.".takeIf {
            state.results.isEmpty() && state.selected == null
        }
    PlaceSearchStatus.SEARCHING -> "Searching…"
    PlaceSearchStatus.EMPTY -> "Nothing found."
    PlaceSearchStatus.UNAVAILABLE -> "Search unavailable — press and hold the map instead."
}
