package com.nuelto.camperexperience.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    val markers = MapOverlay.markers(data)

    if (fitToStops) {
        LaunchedEffect(markers) { camera.apply(MapOverlay.frame(markers.map { it.at })) }
    }

    provider.Canvas(
        camera = camera,
        markers = markers,
        routes = MapOverlay.routes(data),
        modifier = modifier,
        onMarkerClick = onStopClick?.let { click -> { tripId, stopId -> click(tripId, stopId); true } },
    )
}
