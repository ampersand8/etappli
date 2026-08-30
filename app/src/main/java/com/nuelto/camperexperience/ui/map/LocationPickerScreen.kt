package com.nuelto.camperexperience.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.nuelto.camperexperience.domain.PlaceSearchStatus
import com.nuelto.camperexperience.domain.PlaceSuggestion
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

private fun LatLng.position() = Position(longitude = longitude, latitude = latitude)

/** Frames every hit: one point gets a close zoom, several get a bounding box. */
private suspend fun CameraState.frame(points: List<LatLng>) {
    val positions = points.map { it.position() }.distinct()
    if (positions.isEmpty()) return
    if (positions.size == 1) {
        animateTo(finalPosition = CameraPosition(target = positions.first(), zoom = 13.0))
        return
    }
    animateTo(
        boundingBox = BoundingBox(
            west = positions.minOf { it.longitude },
            south = positions.minOf { it.latitude },
            east = positions.maxOf { it.longitude },
            north = positions.maxOf { it.latitude },
        ),
        padding = PaddingValues(72.dp),
    )
}

private suspend fun CameraState.centerOn(point: LatLng) =
    animateTo(finalPosition = CameraPosition(target = point.position(), zoom = position.zoom))

/**
 * Fullscreen map with a fixed center crosshair; confirming returns the map center.
 * Searching drops the hits on the map as markers — tapping one names it and returns
 * that exact spot, which beats eyeballing the crosshair onto a village.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    initial: LatLng?,
    onPicked: (LatLng, PlaceSuggestion?) -> Unit,
    onCancel: () -> Unit,
    viewModel: LocationPickerViewModel = viewModel(factory = LocationPickerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = initial?.position() ?: Position(longitude = 8.2, latitude = 46.8),
            zoom = if (initial != null) 12.0 else 6.0,
        ),
    )

    // New hits pull the camera over them; tapping one slides the crosshair onto it so
    // both ways of confirming agree, without yanking the zoom around.
    LaunchedEffect(state.results) { cameraState.frame(state.results.map { it.location }) }
    LaunchedEffect(state.selected) { state.selected?.let { cameraState.centerOn(it.location) } }

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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val target = cameraState.position.target
                    onPicked(LatLng(target.latitude, target.longitude), null)
                },
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                text = { Text("Use this spot") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (LocalMapEnabled.current) {
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
                    cameraState = cameraState,
                ) {
                    ResultMarkers(state, onSelect = viewModel::select)
                }
            }
            Crosshair(Modifier.align(Alignment.Center))
            SearchOverlay(
                state = state,
                onQueryChange = { query ->
                    val target = cameraState.position.target
                    viewModel.setQuery(query, LatLng(target.latitude, target.longitude))
                },
                onClear = viewModel::clearSearch,
                onUseSelected = { onPicked(it.location, it) },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

/** Every hit as a marker, the selected one drawn larger on top of the rest. */
@Composable
private fun ResultMarkers(state: LocationPickerUiState, onSelect: (PlaceSuggestion) -> Unit) {
    if (state.results.isEmpty()) return
    val color = MaterialTheme.colorScheme.primary
    val source = rememberGeoJsonSource(
        data = GeoJsonData.Features(
            FeatureCollection(
                state.results.mapIndexed { index, place ->
                    Feature(
                        geometry = Point(place.location.position()),
                        properties = buildJsonObject { put("index", index) },
                    )
                },
            ),
        ),
    )
    CircleLayer(
        id = "search-results",
        source = source,
        radius = const(9.dp),
        color = const(color),
        strokeWidth = const(2.dp),
        strokeColor = const(Color.White),
        onClick = { features ->
            val index = features.firstOrNull()?.properties?.get("index")?.jsonPrimitive?.content
            val place = index?.toIntOrNull()?.let { state.results.getOrNull(it) }
            if (place != null) {
                onSelect(place)
                ClickResult.Consume
            } else {
                ClickResult.Pass
            }
        },
    )
    state.selected?.let { selected ->
        val selectedSource = rememberGeoJsonSource(
            data = GeoJsonData.Features(
                FeatureCollection(
                    listOf(Feature(geometry = Point(selected.location.position()), properties = null)),
                ),
            ),
        )
        CircleLayer(
            id = "search-selected",
            source = selectedSource,
            radius = const(13.dp),
            color = const(color),
            strokeWidth = const(3.dp),
            strokeColor = const(Color.White),
        )
    }
}

/** Search field, its one-line status, and the card for whichever marker is tapped. */
@Composable
private fun SearchOverlay(
    state: LocationPickerUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
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
        state.selected?.let { selected ->
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(selected.name, style = MaterialTheme.typography.titleSmall)
                        if (selected.label.isNotBlank()) {
                            Text(
                                selected.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(onClick = {
                        keyboard?.hide()
                        onUseSelected(selected)
                    }) {
                        Text("Use this place")
                    }
                }
            }
        }
    }
}

/** Tapping a marker answers "what is this?", so hits themselves need no line. */
private fun searchStatusText(state: LocationPickerUiState): String? = when (state.status) {
    PlaceSearchStatus.IDLE -> null
    PlaceSearchStatus.SEARCHING -> "Searching…"
    PlaceSearchStatus.EMPTY -> "Nothing found."
    PlaceSearchStatus.UNAVAILABLE -> "Search unavailable — pick the spot on the map."
}

@Composable
private fun Crosshair(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(modifier.size(36.dp)) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(color = color, radius = r / 3, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        drawCircle(color = Color.White, radius = r / 3 + 2f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        drawLine(color, start = c.copy(y = c.y - r), end = c.copy(y = c.y - r / 2), strokeWidth = 4f)
        drawLine(color, start = c.copy(y = c.y + r / 2), end = c.copy(y = c.y + r), strokeWidth = 4f)
        drawLine(color, start = c.copy(x = c.x - r), end = c.copy(x = c.x - r / 2), strokeWidth = 4f)
        drawLine(color, start = c.copy(x = c.x + r / 2), end = c.copy(x = c.x + r), strokeWidth = 4f)
    }
}
