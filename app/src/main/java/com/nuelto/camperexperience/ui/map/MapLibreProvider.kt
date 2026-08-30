package com.nuelto.camperexperience.ui.map

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.MapFrame
import com.nuelto.camperexperience.domain.MapMarker
import com.nuelto.camperexperience.domain.MapRoute
import com.nuelto.camperexperience.domain.PlaceSearch
import com.nuelto.camperexperience.domain.PlaceSuggestion
import com.nuelto.camperexperience.location.PhotonPlaceSearch
import com.nuelto.camperexperience.ui.theme.accentColor
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position

const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

internal fun LatLng.position() = Position(longitude = longitude, latitude = latitude)

/** OpenStreetMap via MapLibre + OpenFreeMap tiles: no API key, no account, no billing. */
object MapLibreProvider : MapProvider {

    override val attribution = "Maps and place search © OpenStreetMap contributors"

    override fun placeSearch(): PlaceSearch = PhotonPlaceSearch()

    @Composable
    override fun rememberCamera(start: LatLng?, zoom: Double): MapCamera {
        val state = rememberCameraState(
            firstPosition = CameraPosition(
                target = (start ?: DEFAULT_CENTER).position(),
                zoom = if (start != null) zoom else DEFAULT_ZOOM,
            ),
        )
        return remember(state) { MapLibreCamera(state) }
    }

    @Composable
    override fun Canvas(
        camera: MapCamera,
        markers: List<MapMarker>,
        routes: List<MapRoute>,
        modifier: Modifier,
        onMarkerClick: ((tripId: String, stopId: String) -> Boolean)?,
        onLongPress: ((LatLng) -> Unit)?,
        // OpenFreeMap draws POI labels but exposes no place behind them.
        onPoiClick: ((PlaceSuggestion) -> Unit)?,
    ) {
        MaplibreMap(
            modifier = modifier,
            baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
            cameraState = (camera as MapLibreCamera).state,
            onMapLongClick = { position, _ ->
                onLongPress?.invoke(LatLng(position.latitude, position.longitude))
                ClickResult.Pass
            },
        ) {
            routes.forEachIndexed { index, route ->
                val source = rememberGeoJsonSource(
                    data = GeoJsonData.Features(
                        FeatureCollection(
                            listOf(
                                Feature(
                                    geometry = LineString(route.points.map { it.position() }),
                                    properties = null,
                                ),
                            ),
                        ),
                    ),
                )
                LineLayer(
                    id = "route-${route.tripId}-$index",
                    source = source,
                    color = const(accentColor(route.accent).copy(alpha = 0.7f)),
                    width = const(3.dp),
                    dasharray = if (route.dashed) const(listOf(2, 2)) else const(emptyList<Number>()),
                )
            }

            // Grouped by styling (colour + filled vs hollow visit markers): one layer each.
            markers.groupBy { it.accent to it.hollow }.entries.forEachIndexed { index, (style, group) ->
                val (accent, hollow) = style
                val color = accentColor(accent)
                val ids = group.map { it.stopId }.toSet()
                val source = rememberGeoJsonSource(
                    data = GeoJsonData.Features(
                        FeatureCollection(
                            group.map { marker ->
                                Feature(
                                    geometry = Point(marker.at.position()),
                                    properties = buildJsonObject {
                                        put("tripId", marker.tripId)
                                        put("stopId", marker.stopId)
                                    },
                                )
                            },
                        ),
                    ),
                )
                CircleLayer(
                    id = "stops-$index",
                    source = source,
                    radius = const(if (hollow) 6.dp else 8.dp),
                    color = const(if (hollow) Color.White else color),
                    strokeWidth = const(2.dp),
                    strokeColor = const(if (hollow) color else Color.White),
                    onClick = { features ->
                        val props = features.firstOrNull()?.properties
                        val tripId = props?.get("tripId")?.jsonPrimitive?.content
                        val stopId = props?.get("stopId")?.jsonPrimitive?.content
                        if (tripId != null && stopId != null && stopId in ids &&
                            onMarkerClick?.invoke(tripId, stopId) == true
                        ) {
                            ClickResult.Consume
                        } else {
                            ClickResult.Pass
                        }
                    },
                )
            }
        }
    }
}

private class MapLibreCamera(val state: CameraState) : MapCamera {
    override val center: LatLng
        get() = state.position.target.let { LatLng(it.latitude, it.longitude) }

    override suspend fun apply(frame: MapFrame) {
        when (frame) {
            MapFrame.None -> Unit
            is MapFrame.Point -> state.animateTo(
                finalPosition = CameraPosition(target = frame.at.position(), zoom = CLOSE_ZOOM + 1),
            )
            is MapFrame.Bounds -> state.animateTo(
                boundingBox = BoundingBox(
                    west = frame.west,
                    south = frame.south,
                    east = frame.east,
                    north = frame.north,
                ),
                padding = PaddingValues(72.dp),
            )
        }
    }

    override suspend fun centerOn(point: LatLng) = state.animateTo(
        finalPosition = CameraPosition(target = point.position(), zoom = state.position.zoom),
    )
}
