package com.nuelto.camperexperience.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nuelto.camperexperience.domain.MapOverlay
import com.nuelto.camperexperience.domain.TripMapData

/**
 * Shared map: markers + route line per trip in the lifecycle color language
 * (blue = planned, green = active/current, grey = done). What to draw is decided by
 * `domain/MapOverlay`; this only hands it to whichever provider is wired up.
 */
@Composable
fun TripMap(
    data: List<TripMapData>,
    modifier: Modifier = Modifier,
    fitToStops: Boolean = true,
    onStopClick: ((tripId: String, stopId: String) -> Unit)? = null,
) {
    val provider = LocalMapProvider.current
    val camera = provider.rememberCamera(start = null, zoom = DEFAULT_ZOOM)
    // Remembered: routes() decodes a polyline per leg, which is not work for every
    // recomposition of a screen that also scrolls.
    val markers = remember(data) { MapOverlay.markers(data) }
    val routes = remember(data) { MapOverlay.routes(data) }

    if (fitToStops) {
        // Route vertices too, or a detour that bulges outside the box the stops make
        // gets framed off-screen.
        LaunchedEffect(markers, routes) {
            camera.apply(MapOverlay.frame(markers.map { it.at } + routes.flatMap { it.points }))
        }
    }

    provider.Canvas(
        camera = camera,
        markers = markers,
        routes = routes,
        modifier = modifier,
        onMarkerClick = onStopClick?.let { click -> { tripId, stopId -> click(tripId, stopId); true } },
    )
}
