package com.nuelto.camperexperience.location

import com.nuelto.camperexperience.BuildConfig
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.StopKind
import com.nuelto.camperexperience.domain.Photon
import com.nuelto.camperexperience.domain.PlaceSearch
import com.nuelto.camperexperience.domain.PlaceSuggestion
import com.nuelto.camperexperience.domain.mergePreferred
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one outbound HTTP call in MapLibre mode: a GET against Photon's free public
 * instance (no key, no account). Best-effort — any failure returns null and the picker
 * falls back to pressing and holding the map. Results are OpenStreetMap data, attributed
 * in Settings.
 */
class PhotonPlaceSearch : PlaceSearch {

    override suspend fun search(
        query: String,
        near: LatLng?,
        prefer: StopKind?,
    ): List<PlaceSuggestion>? = withContext(Dispatchers.IO) {
        val language = Photon.language(Locale.getDefault().language)
        val all = fetch(Photon.searchUrl(query, near, language)) ?: return@withContext null
        val tags = prefer?.let(Photon::preferredTags) ?: return@withContext all
        // A second, tag-filtered pass: what the user is looking for goes to the top.
        val wanted = fetch(Photon.searchUrl(query, near, language, tags)).orEmpty()
        mergePreferred(wanted, all, Photon.MAX_SUGGESTIONS)
    }

    /** Photon hands back coordinates with the hit, so there is nothing left to look up. */
    override suspend fun resolve(suggestion: PlaceSuggestion): PlaceSuggestion? =
        suggestion.takeIf { it.location != null }

    private fun fetch(url: String): List<PlaceSuggestion>? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            setRequestProperty("User-Agent", "CamperExperience/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
            Photon.parse(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
}
