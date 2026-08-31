package com.nuelto.camperexperience.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.MapFrame
import com.nuelto.camperexperience.domain.MapMarker
import com.nuelto.camperexperience.domain.MapRoute
import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.domain.PlaceCacheSweeper
import com.nuelto.camperexperience.domain.PlaceSearch
import com.nuelto.camperexperience.domain.PlaceSuggestion
import com.nuelto.camperexperience.location.GooglePlacesSearch
import com.nuelto.camperexperience.ui.theme.accentColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.google.android.gms.maps.model.LatLng as GmsLatLng

private fun LatLng.gms() = GmsLatLng(latitude, longitude)

/**
 * The app's map, active when a `mapsApiKey` is configured (see MapsBackend). Search is
 * Google Places; the coordinates it returns are on a 30-day retention clock — see
 * domain/PlaceCache.
 */
object GoogleMapProvider : MapProvider {

    override val attribution = "Map data and places © Google"

    override fun placeSearch(): PlaceSearch = GooglePlacesSearch()

    override suspend fun photo(handle: String): ByteArray? = GooglePlacesSearch().photo(handle)

    override fun placeCacheSweeper(tripRepository: TripRepository): PlaceCacheSweeper {
        val places = GooglePlacesSearch()
        return PlaceCacheSweeper(tripRepository) { placeId -> places.details(placeId) }
    }

    @Composable
    override fun rememberCamera(start: LatLng?, zoom: Double): MapCamera {
        val state = rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(
                (start ?: DEFAULT_CENTER).gms(),
                (if (start != null) zoom else DEFAULT_ZOOM).toFloat(),
            )
        }
        return remember(state) { GoogleCamera(state) }
    }

    @Composable
    override fun Canvas(
        camera: MapCamera,
        markers: List<MapMarker>,
        routes: List<MapRoute>,
        modifier: Modifier,
        onMarkerClick: ((tripId: String, stopId: String) -> Boolean)?,
        onLongPress: ((LatLng) -> Unit)?,
        onPoiClick: ((PlaceSuggestion) -> Unit)?,
    ) {
        GoogleMap(
            modifier = modifier,
            cameraPositionState = (camera as GoogleCamera).state,
            mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
            onMapLongClick = { at -> onLongPress?.invoke(LatLng(at.latitude, at.longitude)) },
            // The aires, campsites and huts Google already draws are choosable directly.
            onPOIClick = { poi ->
                onPoiClick?.invoke(
                    PlaceSuggestion(
                        // Google wraps long POI labels with a newline; it is not a separator.
                        name = poi.name.replace('\n', ' '),
                        label = "",
                        location = LatLng(poi.latLng.latitude, poi.latLng.longitude),
                        id = poi.placeId,
                    ),
                )
            },
        ) {
            routes.forEach { route ->
                Polyline(
                    points = route.points.map { it.gms() },
                    color = accentColor(route.accent).copy(alpha = 0.7f),
                    width = 8f,
                    // Google has no data-driven dash expression; a pattern list is the equivalent.
                    pattern = if (route.dashed) listOf(Dash(20f), Gap(10f)) else null,
                )
            }
            markers.forEach { marker ->
                val color = accentColor(marker.accent)
                // Not the plain Marker: its hue wheel cannot hit the app's exact colours,
                // and AdvancedMarker would need a cloud Map ID.
                MarkerComposable(
                    keys = arrayOf<Any>(marker.stopId, marker.accent, marker.hollow),
                    state = markerState(marker),
                    onClick = { onMarkerClick?.invoke(marker.tripId, marker.stopId) == true },
                ) {
                    Dot(color, marker.hollow)
                }
            }
        }
    }
}

// rememberMarkerState saves with no inputs, so a marker would keep its first position
// even after the stop moved; rememberUpdatedMarkerState follows the data.
@Composable
private fun markerState(marker: MapMarker): MarkerState =
    rememberUpdatedMarkerState(marker.at.gms())

/** A stop: filled, or hollow for a visit. */
@Composable
private fun Dot(color: Color, hollow: Boolean) {
    Box(
        Modifier
            .size(if (hollow) 14.dp else 18.dp)
            .clip(CircleShape)
            .background(if (hollow) Color.White else color)
            .border(2.dp, if (hollow) color else Color.White, CircleShape),
    )
}

private class GoogleCamera(val state: CameraPositionState) : MapCamera {
    override val center: LatLng
        get() = state.position.target.let { LatLng(it.latitude, it.longitude) }

    override suspend fun apply(frame: MapFrame) {
        when (frame) {
            MapFrame.None -> Unit
            is MapFrame.Point -> state.animate(
                CameraUpdateFactory.newLatLngZoom(frame.at.gms(), (CLOSE_ZOOM + 1).toFloat()),
            )
            is MapFrame.Bounds -> state.animate(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds(
                        GmsLatLng(frame.south, frame.west),
                        GmsLatLng(frame.north, frame.east),
                    ),
                    PADDING_PX,
                ),
            )
        }
    }

    override suspend fun centerOn(point: LatLng) =
        state.animate(CameraUpdateFactory.newLatLngZoom(point.gms(), state.position.zoom))
}

private const val PADDING_PX = 160
