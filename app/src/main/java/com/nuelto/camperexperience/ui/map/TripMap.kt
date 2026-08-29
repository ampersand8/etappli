package com.nuelto.camperexperience.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.Trip
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

/**
 * Test seam: MapLibre needs a native GL surface that doesn't exist under Robolectric,
 * so UI tests provide `false` to swap the map for an empty placeholder.
 */
val LocalMapEnabled = staticCompositionLocalOf { true }

/** Distinct marker/route color per trip, cycling through a fixed palette. */
val tripColorPalette = listOf(
    Color(0xFF2E5D3E), // forest green
    Color(0xFFC0392B), // brick red
    Color(0xFF2963A8), // lake blue
    Color(0xFFB8860B), // ochre
    Color(0xFF6A3FA0), // plum
    Color(0xFF167F7A), // teal
    Color(0xFFB44E9C), // heather
    Color(0xFF7A5230), // bark brown
)

fun tripColor(index: Int): Color = tripColorPalette[index.mod(tripColorPalette.size)]

data class TripMapData(
    val trip: Trip,
    val stops: List<Stop>,
    val color: Color,
)

private fun Stop.position(): Position? =
    location?.let { Position(longitude = it.longitude, latitude = it.latitude) }

/**
 * Shared map: one marker layer + route line per trip. Straight lines between stops in
 * order — a trip log, not turn-by-turn routing.
 */
@Composable
fun TripMap(
    data: List<TripMapData>,
    modifier: Modifier = Modifier,
    cameraState: CameraState? = null,
    fitToStops: Boolean = true,
    onStopClick: ((tripId: String, stopId: String) -> Unit)? = null,
) {
    if (!LocalMapEnabled.current) {
        Box(modifier.testTag("map-placeholder"))
        return
    }
    @Suppress("NAME_SHADOWING")
    val cameraState = cameraState ?: rememberCameraState(
        firstPosition = CameraPosition(target = Position(longitude = 8.2, latitude = 46.8), zoom = 6.0),
    )
    val allPositions = data.flatMap { it.stops.sortedBy { s -> s.orderIndex }.mapNotNull { s -> s.position() } }

    if (fitToStops) {
        LaunchedEffect(allPositions) {
            if (allPositions.isEmpty()) return@LaunchedEffect
            if (allPositions.size == 1) {
                cameraState.animateTo(
                    finalPosition = CameraPosition(target = allPositions.first(), zoom = 11.0),
                )
            } else {
                cameraState.animateTo(
                    boundingBox = BoundingBox(
                        west = allPositions.minOf { it.longitude },
                        south = allPositions.minOf { it.latitude },
                        east = allPositions.maxOf { it.longitude },
                        north = allPositions.maxOf { it.latitude },
                    ),
                    padding = PaddingValues(48.dp),
                )
            }
        }
    }

    MaplibreMap(
        modifier = modifier,
        baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
        cameraState = cameraState,
    ) {
        data.forEach { entry ->
            val orderedPositions = entry.stops.sortedBy { it.orderIndex }.mapNotNull { it.position() }

            if (orderedPositions.size >= 2) {
                val routeSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(
                        FeatureCollection(
                            listOf(Feature(geometry = LineString(orderedPositions), properties = null)),
                        ),
                    ),
                )
                LineLayer(
                    id = "route-${entry.trip.id}",
                    source = routeSource,
                    color = const(entry.color.copy(alpha = 0.7f)),
                    width = const(3.dp),
                )
            }

            if (orderedPositions.isNotEmpty()) {
                val stopsSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(
                        FeatureCollection(
                            entry.stops.mapNotNull { stop ->
                                stop.position()?.let { pos ->
                                    Feature(
                                        geometry = Point(pos),
                                        properties = buildJsonObject {
                                            put("tripId", entry.trip.id)
                                            put("stopId", stop.id)
                                        },
                                    )
                                }
                            },
                        ),
                    ),
                )
                CircleLayer(
                    id = "stops-${entry.trip.id}",
                    source = stopsSource,
                    radius = const(8.dp),
                    color = const(entry.color),
                    strokeWidth = const(2.dp),
                    strokeColor = const(Color.White),
                    onClick = { features ->
                        val props = features.firstOrNull()?.properties
                        val tripId = props?.get("tripId")?.jsonPrimitive?.content
                        val stopId = props?.get("stopId")?.jsonPrimitive?.content
                        if (tripId != null && stopId != null && onStopClick != null) {
                            onStopClick(tripId, stopId)
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
