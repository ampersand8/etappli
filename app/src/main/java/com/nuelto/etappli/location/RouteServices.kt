package com.nuelto.etappli.location

import com.nuelto.etappli.BuildConfig
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.domain.Elevation
import com.nuelto.etappli.domain.GoogleRoutes
import com.nuelto.etappli.domain.RoutedLeg
import com.nuelto.etappli.domain.TransitStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Routes API over plain HTTP — there is no Android client library for it (the
 * official ones are server-side, for five other languages), and the request building and
 * parsing are better off in `domain/GoogleRoutes` where they are mutation-tested anyway.
 *
 * Only reachable in Google-map mode, on the same key as the map and Places — the Routes
 * API just has to be enabled and allowed by the key's API restrictions.
 */
class GoogleRoutesService(private val apiKey: String = BuildConfig.MAPS_API_KEY) {

    /** One leg per pair of consecutive points, or null if the call did not come back. */
    suspend fun legs(points: List<LatLng>): List<RoutedLeg>? = withContext(Dispatchers.IO) {
        Http.post(GoogleRoutes.COMPUTE_ROUTES_URL, GoogleRoutes.computeRoutesBody(points), headers(GoogleRoutes.FIELD_MASK))
            ?.let(GoogleRoutes::parseLegs)
    }

    /** The steps of a public-transport route — empty when there is none, null if the call did not come back. */
    suspend fun transit(from: LatLng, to: LatLng): List<TransitStep>? = withContext(Dispatchers.IO) {
        Http.post(GoogleRoutes.COMPUTE_ROUTES_URL, GoogleRoutes.transitBody(from, to), headers(GoogleRoutes.TRANSIT_FIELD_MASK))
            ?.let(GoogleRoutes::parseTransitSteps)
    }

    private fun headers(fieldMask: String) =
        mapOf("X-Goog-Api-Key" to apiKey, "X-Goog-FieldMask" to fieldMask) + AppIdentity.headers
}

/**
 * Open-Meteo's elevation service (Copernicus DEM GLO-90). Not Google's Elevation API,
 * whose policies forbid storing the result — see `domain/Elevation`.
 */
class ElevationService {

    /** One height per point, in order, or null if the call did not come back. */
    suspend fun heights(points: List<LatLng>): List<Double>? = withContext(Dispatchers.IO) {
        Http.get(Elevation.url(points))?.let(Elevation::parse)?.takeIf { it.isNotEmpty() }
    }
}
