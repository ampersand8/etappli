package com.nuelto.camperexperience.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.MapFrame
import com.nuelto.camperexperience.domain.MapMarker
import com.nuelto.camperexperience.domain.MapRoute
import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.domain.PlaceCacheSweeper
import com.nuelto.camperexperience.domain.PlaceSearch

/**
 * Camera the map screens drive, in the app's own types — no SDK class ever reaches a
 * caller, which is what lets the picker read the crosshair back under Robolectric.
 */
interface MapCamera {
    val center: LatLng
    suspend fun apply(frame: MapFrame)
    suspend fun centerOn(point: LatLng)
}

/**
 * The map surface, behind one seam so the app can be pointed at a different provider.
 * MapLibre/OpenFreeMap is the default; Google Maps takes over when an API key is
 * configured. Everything provider-specific — SDK types, style URLs, attribution, and
 * the matching place search — lives behind here.
 */
interface MapProvider {
    /** Shown in Settings; the data licence follows the provider. */
    val attribution: String

    /** Search that is licensed to run alongside this map. */
    fun placeSearch(): PlaceSearch?

    /**
     * Enforcement for providers whose coordinates expire. Null means this provider's
     * results are ours to keep, so nothing ever has to be swept.
     */
    fun placeCacheSweeper(tripRepository: TripRepository): PlaceCacheSweeper? = null

    @Composable
    fun rememberCamera(start: LatLng?, zoom: Double): MapCamera

    /**
     * Draws [markers] and [routes]. [onMarkerClick] gets (tripId, stopId) and returns
     * true when it consumed the tap.
     */
    @Composable
    fun Canvas(
        camera: MapCamera,
        markers: List<MapMarker>,
        routes: List<MapRoute>,
        modifier: Modifier,
        onMarkerClick: ((tripId: String, stopId: String) -> Boolean)?,
    )
}

/** The provider every map screen renders through. */
val LocalMapProvider = staticCompositionLocalOf<MapProvider> { PlaceholderMapProvider }

/**
 * Stand-in for JVM tests: no GL surface, no Play services, but a camera that remembers
 * what it was told so screens can still be driven end to end.
 */
object PlaceholderMapProvider : MapProvider {
    override val attribution = ""

    override fun placeSearch(): PlaceSearch? = null

    @Composable
    override fun rememberCamera(start: LatLng?, zoom: Double): MapCamera =
        androidx.compose.runtime.remember(start, zoom) { RecordingCamera(start ?: DEFAULT_CENTER) }

    @Composable
    override fun Canvas(
        camera: MapCamera,
        markers: List<MapMarker>,
        routes: List<MapRoute>,
        modifier: Modifier,
        onMarkerClick: ((tripId: String, stopId: String) -> Boolean)?,
    ) {
        Box(modifier.testTag("map-placeholder"))
    }
}

private class RecordingCamera(initial: LatLng) : MapCamera {
    override var center: LatLng = initial
        private set

    override suspend fun apply(frame: MapFrame) {
        when (frame) {
            is MapFrame.Point -> center = frame.at
            is MapFrame.Bounds -> center = LatLng(
                (frame.south + frame.north) / 2,
                (frame.west + frame.east) / 2,
            )
            MapFrame.None -> Unit
        }
    }

    override suspend fun centerOn(point: LatLng) {
        center = point
    }
}

/** Middle of Switzerland — where the map opens with nothing better to go on. */
val DEFAULT_CENTER = LatLng(46.8, 8.2)
const val DEFAULT_ZOOM = 6.0
const val CLOSE_ZOOM = 12.0
