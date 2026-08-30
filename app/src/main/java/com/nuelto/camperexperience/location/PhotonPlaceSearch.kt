package com.nuelto.camperexperience.location

import com.nuelto.camperexperience.BuildConfig
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.Photon
import com.nuelto.camperexperience.domain.PlaceSearch
import com.nuelto.camperexperience.domain.PlaceSuggestion
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one outbound HTTP call in the app: a GET against Photon's free public instance
 * (no key, no account). Best-effort — any failure returns null and the editor falls
 * back to "I'm here" / "Pick on map". Results are OpenStreetMap data, attributed in
 * Settings.
 */
class PhotonPlaceSearch : PlaceSearch {

    /** Photon hands back coordinates with the hit, so there is nothing left to look up. */
    override suspend fun resolve(suggestion: PlaceSuggestion): PlaceSuggestion? =
        suggestion.takeIf { it.location != null }

    override suspend fun search(query: String, near: LatLng?): List<PlaceSuggestion>? =
        withContext(Dispatchers.IO) {
            val url = Photon.searchUrl(query, near, Photon.language(Locale.getDefault().language))
            runCatching {
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
}
