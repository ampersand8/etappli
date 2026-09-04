package com.nuelto.etappli.location

import com.nuelto.etappli.BuildConfig
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.domain.GooglePlaces
import com.nuelto.etappli.domain.PlaceSearch
import com.nuelto.etappli.domain.PlaceSuggestion
import com.nuelto.etappli.domain.mergePreferred
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
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

    // Google bills autocomplete per session: every keystroke plus the one lookup that
    // resolves the chosen hit. The token is retired once that lookup happens.
    private var sessionToken: String = UUID.randomUUID().toString()

    override suspend fun search(
        query: String,
        near: LatLng?,
        prefer: StopKind?,
    ): List<PlaceSuggestion>? = withContext(Dispatchers.IO) {
        val all = autocomplete(query, near, emptyList()) ?: return@withContext null
        val types = prefer?.let(GooglePlaces::preferredTypes)?.takeIf { it.isNotEmpty() }
            ?: return@withContext all
        // A second, type-filtered pass: what the user is looking for goes to the top,
        // and it surfaces places the open query misses entirely.
        val wanted = autocomplete(query, near, types).orEmpty()
        mergePreferred(wanted, all, GooglePlaces.MAX_SUGGESTIONS)
    }

    private fun autocomplete(query: String, near: LatLng?, types: List<String>) =
        post(
            GooglePlaces.AUTOCOMPLETE_URL,
            GooglePlaces.autocompleteBody(query, near, sessionToken, types),
            null,
        )?.let(GooglePlaces::parseAutocomplete)

    override suspend fun find(query: String, near: LatLng?): List<PlaceSuggestion>? =
        withContext(Dispatchers.IO) {
            post(GooglePlaces.SEARCH_URL, GooglePlaces.searchBody(query, near), GooglePlaces.SEARCH_FIELD_MASK)
                ?.let(GooglePlaces::parseSearch)
        }

    override suspend fun resolve(suggestion: PlaceSuggestion): PlaceSuggestion? {
        // Already complete: a hit that has both a coordinate and what the place is like.
        if (suggestion.location != null && suggestion.details != null) return suggestion
        val id = suggestion.id.ifBlank { return suggestion.takeIf { it.location != null } }
        val resolved = details(id, sessionToken) ?: return suggestion.takeIf { it.location != null }
        sessionToken = UUID.randomUUID().toString()
        // Keep the name the user picked from the list, take everything else.
        return suggestion.copy(location = resolved.location, details = resolved.details)
    }

    /** Fetches one place, to fill in or renew a coordinate. */
    suspend fun details(placeId: String, token: String? = null): PlaceSuggestion? =
        withContext(Dispatchers.IO) {
            get(GooglePlaces.detailsUrl(placeId, token), GooglePlaces.DETAILS_FIELD_MASK)
                ?.let(GooglePlaces::parseDetails)
        }

    /** Photo bytes. Best-effort like everything else here. */
    suspend fun photo(handle: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(GooglePlaces.photoUrl(handle, apiKey))
            (url.openConnection() as HttpURLConnection).run {
                connectTimeout = 5_000
                readTimeout = 10_000
                instanceFollowRedirects = true
                try {
                    if (responseCode != HttpURLConnection.HTTP_OK) return@run null
                    inputStream.use { it.readBytes() }
                } finally {
                    disconnect()
                }
            }
        }.getOrNull()
    }

    private fun post(url: String, body: String, fieldMask: String?): String? = runCatching {
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

    private fun open(url: String, fieldMask: String?) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("X-Goog-Api-Key", apiKey)
            fieldMask?.let { setRequestProperty("X-Goog-FieldMask", it) }
            setRequestProperty("User-Agent", "Etappli/${BuildConfig.VERSION_NAME}")
        }

    private fun HttpURLConnection.readBodyIfOk(): String? =
        if (responseCode != HttpURLConnection.HTTP_OK) null
        else inputStream.bufferedReader().use { it.readText() }
}
