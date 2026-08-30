package com.nuelto.camperexperience.location

import com.nuelto.camperexperience.BuildConfig
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.GooglePlaces
import com.nuelto.camperexperience.domain.PlaceSearch
import com.nuelto.camperexperience.domain.PlaceSuggestion
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Places API (New) over plain HTTP — the Places SDK drags 30-odd transitive
 * dependencies for View-based widgets this app doesn't use, and hides the parsing this
 * repo would rather keep pure and mutation-tested.
 *
 * Only ever reachable in Google-map mode: the Places terms forbid showing this content
 * alongside a non-Google map.
 */
class GooglePlacesSearch(private val apiKey: String = BuildConfig.MAPS_API_KEY) : PlaceSearch {

    override suspend fun search(query: String, near: LatLng?): List<PlaceSuggestion>? =
        withContext(Dispatchers.IO) {
            val body = GooglePlaces.searchBody(query, near, Locale.getDefault().language)
            post(GooglePlaces.SEARCH_URL, body, GooglePlaces.FIELD_MASK)
                ?.let(GooglePlaces::parseSearch)
        }

    /** Re-fetches one place, to renew a coordinate whose 30-day retention ran out. */
    suspend fun details(placeId: String): PlaceSuggestion? = withContext(Dispatchers.IO) {
        get(GooglePlaces.detailsUrl(placeId), GooglePlaces.DETAILS_FIELD_MASK)
            ?.let(GooglePlaces::parseDetails)
    }

    private fun post(url: String, body: String, fieldMask: String): String? = runCatching {
        val connection = open(url, fieldMask).apply {
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

    private fun get(url: String, fieldMask: String): String? = runCatching {
        val connection = open(url, fieldMask)
        try {
            connection.readBodyIfOk()
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun open(url: String, fieldMask: String) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("X-Goog-Api-Key", apiKey)
            setRequestProperty("X-Goog-FieldMask", fieldMask)
            setRequestProperty("User-Agent", "CamperExperience/${BuildConfig.VERSION_NAME}")
        }

    private fun HttpURLConnection.readBodyIfOk(): String? =
        if (responseCode != HttpURLConnection.HTTP_OK) null
        else inputStream.bufferedReader().use { it.readText() }
}
