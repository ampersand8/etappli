package com.nuelto.camperexperience.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.StopKind
import com.nuelto.camperexperience.domain.MapAccent
import com.nuelto.camperexperience.domain.MapMarker
import com.nuelto.camperexperience.domain.MapOverlay
import com.nuelto.camperexperience.domain.PlaceDetails
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
                onDeselect = viewModel::clearSelection,
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
    onDeselect: () -> Unit,
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
            ChosenPlaceCard(
                place = selected,
                photo = rememberDecoded(state.photo),
                onUse = {
                    keyboard?.hide()
                    onUseSelected(selected)
                },
                onDismiss = onDeselect.takeIf { state.results.isNotEmpty() },
            )
        }
    }
}

/** Photo bytes become a bitmap once, not on every recomposition. */
@Composable
private fun rememberDecoded(bytes: ByteArray?): ImageBitmap? = remember(bytes) {
    bytes?.let {
        runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull()
    }
}

/** What the place is actually like, so the choice can be made without leaving the app. */
@Composable
private fun ChosenPlaceCard(
    place: PlaceSuggestion,
    photo: ImageBitmap?,
    onUse: () -> Unit,
    onDismiss: (() -> Unit)?,
) {
    val details = place.details
    Card(Modifier.fillMaxWidth()) {
        Column {
            photo?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
            }
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // A dropped pin has no name yet; the editor geocodes one.
                Text(place.name.ifBlank { "Dropped pin" }, style = MaterialTheme.typography.titleSmall)
                if (place.label.isNotBlank()) {
                    Text(
                        place.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ratingLine(details)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                details?.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                details?.review?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "\u201C$it\u201D",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onUse, enabled = place.location != null) {
                        Text("Use this place")
                    }
                    onDismiss?.let {
                        TextButton(onClick = it) { Text("Back to results") }
                    }
                }
            }
        }
    }
}

/** e.g. "Campground · 4.8 ★ (1787)" — whichever halves Google actually knows. */
private fun ratingLine(details: PlaceDetails?): String? {
    if (details == null) return null
    val stars = details.rating?.let { rating ->
        val count = details.ratingCount.takeIf { it > 0 }?.let { " ($it)" }.orEmpty()
        "$rating \u2605$count"
    }
    return listOfNotNull(details.kind.takeIf { it.isNotBlank() }, stars)
        .joinToString(" \u00B7 ")
        .takeIf { it.isNotBlank() }
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
