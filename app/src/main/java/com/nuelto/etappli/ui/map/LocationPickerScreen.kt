package com.nuelto.etappli.ui.map

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.domain.MapAccent
import com.nuelto.etappli.domain.MapMarker
import com.nuelto.etappli.domain.MapOverlay
import com.nuelto.etappli.domain.PlaceDetails
import com.nuelto.etappli.domain.PlaceSearchStatus
import com.nuelto.etappli.domain.PlaceSuggestion

/**
 * Search for a place and pick it, the way a maps app works — no coordinates, no aiming.
 * Typing offers predictions; the search as submitted drops pins for everything it
 * matches ("camping" for what is around here); a pin, a prediction or a POI opens a card
 * that shrinks to a strip so the map around the place can be looked at. For somewhere
 * with no name, press and hold the map to drop a pin.
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
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    // What the strip along the bottom covers; the camera centres within the rest.
    var bottomInset by remember { mutableStateOf(0.dp) }

    // Pins pull the camera over them all; a chosen place slides under it, without
    // yanking the zoom around — again whenever the card changes height, since Google
    // Maps applies new padding only on the next camera move.
    LaunchedEffect(state.results) {
        camera.apply(MapOverlay.frame(state.results.mapNotNull { it.location }))
    }
    LaunchedEffect(state.selected, bottomInset) {
        state.selected?.location?.let { camera.centerOn(it) }
    }

    // Back peels the picker's own layers off before it closes the picker.
    BackHandler(enabled = state.canGoBack) { viewModel.back() }

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
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val cardMaxHeight = maxHeight * CARD_SHARE
            provider.Canvas(
                camera = camera,
                markers = state.markers(),
                routes = emptyList(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset),
                onMarkerClick = { _, id -> state.hit(id)?.also(viewModel::select) != null },
                onLongPress = viewModel::dropPin,
                onPoiClick = viewModel::select,
            )
            SearchOverlay(
                state = state,
                onQueryChange = { query -> viewModel.setQuery(query, camera.center, prefer) },
                onSearch = {
                    keyboard?.hide()
                    viewModel.search(camera.center)
                },
                onClear = viewModel::clearSearch,
                onSelect = { place ->
                    keyboard?.hide()
                    viewModel.select(place)
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            BottomStrip(
                state = state,
                photo = rememberDecoded(state.photo),
                maxHeight = cardMaxHeight,
                onSelect = viewModel::select,
                onExpand = viewModel::setExpanded,
                onUse = { place ->
                    keyboard?.hide()
                    place.location?.let { onPicked(it, place.takeIf { p -> p.name.isNotBlank() }) }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomInset = with(density) { it.height.toDp() } },
            )
        }
    }
}

/** The pins as markers: the chosen one is filled, the rest are hollow rings. A dropped
 *  pin, a POI or a prediction is not among them, so it gets a marker of its own. */
private fun LocationPickerUiState.markers(): List<MapMarker> {
    val pins = results.mapIndexedNotNull { index, place ->
        place.location?.let {
            MapMarker(
                tripId = SEARCH_MARKERS,
                stopId = index.toString(),
                at = it,
                accent = MapAccent.PLANNED,
                hollow = !place.sameAs(selected),
            )
        }
    }
    val chosen = selected?.takeIf { chosen -> results.none { it.sameAs(chosen) } }?.location?.let {
        MapMarker(SEARCH_MARKERS, DROPPED_PIN, it, MapAccent.PLANNED, hollow = false)
    }
    return pins + listOfNotNull(chosen)
}

/** Still the same place once one of the two has been enriched: by id, when there is one. */
private fun PlaceSuggestion.sameAs(other: PlaceSuggestion?): Boolean =
    other != null && if (id.isNotBlank()) id == other.id else this == other

private fun LocationPickerUiState.hit(markerId: String): PlaceSuggestion? =
    markerId.toIntOrNull()?.let { results.getOrNull(it) }

private const val SEARCH_MARKERS = "search"
private const val DROPPED_PIN = "pin"

/** How much of the map the chosen place's card may cover at most. */
private const val CARD_SHARE = 0.6f

/** Search field, its one-line status, and the predictions the typing so far could mean. */
@Composable
private fun SearchOverlay(
    state: LocationPickerUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSelect: (PlaceSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                leadingIcon = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().testTag("place-search"),
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
        // A list you can read; picking one locates it. It closes behind the card.
        if (state.selected == null && state.predictions.isNotEmpty()) {
            Surface(shape = MaterialTheme.shapes.medium, shadowElevation = 2.dp) {
                Column {
                    state.predictions.forEachIndexed { index, place ->
                        if (index > 0) HorizontalDivider()
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(place) }
                                .padding(12.dp),
                        ) {
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
            }
        }
    }
}

/** Along the bottom: the pins as a row of cards to read, or the chosen place's card. */
@Composable
private fun BottomStrip(
    state: LocationPickerUiState,
    photo: ImageBitmap?,
    maxHeight: Dp,
    onSelect: (PlaceSuggestion) -> Unit,
    onExpand: (Boolean) -> Unit,
    onUse: (PlaceSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        val selected = state.selected
        when {
            selected != null -> ChosenPlaceCard(
                place = selected,
                photo = photo,
                expanded = state.expanded,
                maxHeight = maxHeight,
                onExpand = onExpand,
                onUse = { onUse(selected) },
                modifier = Modifier.padding(12.dp),
            )
            state.results.isNotEmpty() -> LazyRow(
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.results) { place ->
                    Card(onClick = { onSelect(place) }, modifier = Modifier.width(220.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                place.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (place.label.isNotBlank()) {
                                Text(
                                    place.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
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

/**
 * What the place is actually like, so the choice can be made without leaving the app —
 * in full, or as a strip that leaves the map around the place to look at.
 */
@Composable
private fun ChosenPlaceCard(
    place: PlaceSuggestion,
    photo: ImageBitmap?,
    expanded: Boolean,
    maxHeight: Dp,
    onExpand: (Boolean) -> Unit,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val details = place.details
    // A dropped pin has no name yet; the editor geocodes one.
    val name = place.name.ifBlank { "Dropped pin" }
    val label = place.label.takeIf { it.isNotBlank() }
    Card(modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Column(
            Modifier
                .clickable(enabled = !expanded) { onExpand(true) }
                .verticalScroll(rememberScrollState()),
        ) {
            if (expanded) {
                photo?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!expanded) {
                        photo?.let {
                            Image(
                                bitmap = it,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = if (expanded) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!expanded) {
                            (ratingLine(details) ?: label)?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (!expanded) {
                        Button(onClick = onUse, enabled = place.location != null) { Text("Use") }
                    }
                    IconButton(onClick = { onExpand(!expanded) }) {
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = if (expanded) "Show less" else "Show more",
                        )
                    }
                }
                if (expanded) {
                    label?.let {
                        Text(
                            it,
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
                            "“$it”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(onClick = onUse, enabled = place.location != null) {
                        Text("Use this place")
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
        "$rating ★$count"
    }
    return listOfNotNull(details.kind.takeIf { it.isNotBlank() }, stars)
        .joinToString(" · ")
        .takeIf { it.isNotBlank() }
}

/** The list and the pins speak for themselves, so a hit needs no status line. */
private fun searchStatusText(state: LocationPickerUiState): String? = when (state.status) {
    PlaceSearchStatus.IDLE -> HINT.takeIf {
        state.predictions.isEmpty() && state.results.isEmpty() && state.selected == null
    }
    PlaceSearchStatus.SEARCHING -> "Searching…"
    PlaceSearchStatus.EMPTY -> "Nothing found."
    PlaceSearchStatus.UNAVAILABLE -> "Search unavailable — press and hold the map instead."
}

private const val HINT =
    "Type a name, or search “camping” for what is around here. Press and hold the map to drop a pin."
