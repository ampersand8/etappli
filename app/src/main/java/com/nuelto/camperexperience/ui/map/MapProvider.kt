package com.nuelto.camperexperience.ui.map

import androidx.compose.foundation.clickable
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
import com.nuelto.camperexperience.domain.PlaceSuggestion

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
 * The map surface, behind one seam. Google Maps implements it when an API key is
 * configured; PlaceholderMapProvider stands in otherwise, and in JVM tests. Everything
 * provider-specific — SDK types, attribution, the matching place search — lives behind
 * here, which is what keeps every screen testable without a GL surface.
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

    /**
     * Loads a photo handed out by [PlaceDetails.photo], as encoded bytes — decoding is
     * left to the UI so the picker's state stays free of Android graphics types.
     */
    suspend fun photo(handle: String): ByteArray? = null

    @Composable
    fun rememberCamera(start: LatLng?, zoom: Double): MapCamera

    /**
     * Draws [markers] and [routes]. [onMarkerClick] gets (tripId, stopId) and returns
     * true when it consumed the tap; [onLongPress] gets the point pressed and held,
     * which is how a spot with no name gets chosen.
     */
    @Composable
    fun Canvas(
        camera: MapCamera,
        markers: List<MapMarker>,
        routes: List<MapRoute>,
        modifier: Modifier,
        onMarkerClick: ((tripId: String, stopId: String) -> Boolean)? = null,
        onLongPress: ((LatLng) -> Unit)? = null,
        onPoiClick: ((PlaceSuggestion) -> Unit)? = null,
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
        onLongPress: ((LatLng) -> Unit)?,
        onPoiClick: ((PlaceSuggestion) -> Unit)?,
    ) {
        // Tapping the placeholder stands in for the long press, so the picker's
        // drop-a-pin flow can still be driven end to end on the JVM.
        Box(
            modifier
                .testTag("map-placeholder")
                .clickable(enabled = onLongPress != null) { onLongPress?.invoke(camera.center) },
        )
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
