package com.nuelto.camperexperience.location

import com.nuelto.camperexperience.BuildConfig
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.Elevation
import com.nuelto.camperexperience.domain.GoogleRoutes
import com.nuelto.camperexperience.domain.RoutedLeg
import java.net.HttpURLConnection
import java.net.URL
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
        val body = post(
            url = GoogleRoutes.COMPUTE_ROUTES_URL,
            body = GoogleRoutes.computeRoutesBody(points),
            headers = mapOf(
                "X-Goog-Api-Key" to apiKey,
                "X-Goog-FieldMask" to GoogleRoutes.FIELD_MASK,
            ),
        )
        body?.let(GoogleRoutes::parseLegs)
    }
}

/**
 * Open-Meteo's elevation service (Copernicus DEM GLO-90). Not Google's Elevation API,
 * whose policies forbid storing the result — see `domain/Elevation`.
 */
class ElevationService {

    /** One height per point, in order, or null if the call did not come back. */
    suspend fun heights(points: List<LatLng>): List<Double>? = withContext(Dispatchers.IO) {
        get(Elevation.url(points))?.let(Elevation::parse)?.takeIf { it.isNotEmpty() }
    }
}

// Fail-soft like the Places path: every failure mode — offline, timeout, 4xx, junk body
// — collapses to null, and the caller falls back to straight lines.

private fun post(url: String, body: String, headers: Map<String, String>): String? = runCatching {
    val connection = open(url, headers).apply {
        requestMethod = "POST"
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
    }
    try {
        connection.outputStream.use { it.write(body.toByteArray()) }
        connection.readBodyIfOk()
    } finally {
        connection.disconnect()
    }
}.getOrNull()

private fun get(url: String): String? = runCatching {
    val connection = open(url, emptyMap())
    try {
        connection.readBodyIfOk()
    } finally {
        connection.disconnect()
    }
}.getOrNull()

private fun open(url: String, headers: Map<String, String>) =
    (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 5_000
        readTimeout = 10_000
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
        setRequestProperty("User-Agent", "CamperExperience/${BuildConfig.VERSION_NAME}")
    }

private fun HttpURLConnection.readBodyIfOk(): String? =
    if (responseCode != HttpURLConnection.HTTP_OK) null
    else inputStream.bufferedReader().use { it.readText() }
