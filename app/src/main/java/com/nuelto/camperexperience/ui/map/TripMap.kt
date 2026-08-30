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
import com.nuelto.camperexperience.data.model.StopKind
import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.ui.theme.stopColor
import com.nuelto.camperexperience.ui.theme.statusColor
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

data class TripMapData(
    val trip: Trip,
    val stops: List<Stop>,
    // Tonight's stop on an active trip — rendered green, with a green current leg.
    val currentStopId: String? = null,
)

private fun Stop.position(): Position? =
    location?.let { Position(longitude = it.longitude, latitude = it.latitude) }

/**
 * Shared map: markers + route line per trip in the lifecycle color language
 * (blue = planned, green = active/current, grey = done). Active trips split the
 * route: grey behind, green current leg, dashed blue ahead; planned routes are
 * dashed blue throughout; visits render hollow, skipped stops drop out.
 * Straight lines between stops in order — a trip log, not turn-by-turn routing.
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
    // Camera fit covers every visible marker — skipped stops leave the route but keep theirs.
    val allPositions = data.flatMap { entry -> entry.stops.mapNotNull { it.position() } }

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
            entry.routeSegments().forEachIndexed { index, segment ->
                val segmentSource = rememberGeoJsonSource(
                    data = GeoJsonData.Features(
                        FeatureCollection(
                            listOf(Feature(geometry = LineString(segment.positions), properties = null)),
                        ),
                    ),
                )
                LineLayer(
                    id = "route-${entry.trip.id}-$index",
                    source = segmentSource,
                    color = const(segment.color.copy(alpha = 0.7f)),
                    width = const(3.dp),
                    dasharray = if (segment.dashed) const(listOf(2, 2)) else const(emptyList<Number>()),
                )
            }

            val markerStops = entry.stops.filter { it.location != null }
            if (markerStops.isNotEmpty()) {
                // Grouped by styling (color + filled vs hollow visit markers): one layer each.
                markerStops.groupBy { entry.markerColor(it) to (it.kind == StopKind.VISIT) }
                    .entries.forEachIndexed { index, (style, stops) ->
                        val (color, hollow) = style
                        val ids = stops.map { it.id }.toSet()
                        val groupSource = rememberGeoJsonSource(
                            data = GeoJsonData.Features(
                                FeatureCollection(
                                    stops.map { stop ->
                                        Feature(
                                            geometry = Point(stop.position()!!),
                                            properties = buildJsonObject {
                                                put("tripId", entry.trip.id)
                                                put("stopId", stop.id)
                                            },
                                        )
                                    },
                                ),
                            ),
                        )
                        CircleLayer(
                            id = "stops-${entry.trip.id}-$index",
                            source = groupSource,
                            radius = const(if (hollow) 6.dp else 8.dp),
                            color = const(if (hollow) Color.White else color),
                            strokeWidth = const(2.dp),
                            strokeColor = const(if (hollow) color else Color.White),
                            onClick = { features ->
                                val props = features.firstOrNull()?.properties
                                val tripId = props?.get("tripId")?.jsonPrimitive?.content
                                val stopId = props?.get("stopId")?.jsonPrimitive?.content
                                if (tripId != null && stopId != null && stopId in ids && onStopClick != null) {
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
}

private data class RouteSegment(val positions: List<Position>, val color: Color, val dashed: Boolean)

/** Ordered, non-skipped stops — the route as it will actually be driven. */
private fun TripMapData.routeStops(): List<Stop> =
    stops.sortedBy { it.orderIndex }.filterNot { it.state == StopState.SKIPPED }

private fun TripMapData.markerColor(stop: Stop): Color = when (trip.status) {
    TripStatus.PLANNED, TripStatus.DONE -> statusColor(trip.status)
    TripStatus.ACTIVE -> stopColor(stop.state, isCurrent = stop.id == currentStopId)
}

/**
 * DONE: one grey line. PLANNED: one dashed blue line. ACTIVE: grey behind (done
 * stops), green current leg, dashed blue ahead.
 */
private fun TripMapData.routeSegments(): List<RouteSegment> {
    val located = routeStops().filter { it.location != null }
    if (located.size < 2) return emptyList()
    val positions = { list: List<Stop> -> list.mapNotNull { it.position() } }

    return when (trip.status) {
        TripStatus.DONE -> listOf(RouteSegment(positions(located), statusColor(TripStatus.DONE), dashed = false))
        TripStatus.PLANNED -> listOf(RouteSegment(positions(located), statusColor(TripStatus.PLANNED), dashed = true))
        TripStatus.ACTIVE -> {
            val done = located.filter { it.state == StopState.DONE }
            val upcoming = located.filter { it.state == StopState.PLANNED }
            buildList {
                if (done.size >= 2) {
                    add(RouteSegment(positions(done), stopColor(StopState.DONE, false), dashed = false))
                }
                if (done.isNotEmpty() && upcoming.isNotEmpty()) {
                    add(
                        RouteSegment(
                            positions(listOf(done.last(), upcoming.first())),
                            stopColor(upcoming.first().state, isCurrent = true),
                            dashed = false,
                        ),
                    )
                }
                if (upcoming.size >= 2) {
                    add(RouteSegment(positions(upcoming), stopColor(StopState.PLANNED, false), dashed = true))
                }
            }
        }
    }
}
