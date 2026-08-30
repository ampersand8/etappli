package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.StopKind
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Photon (photon.komoot.io): free OpenStreetMap geocoder, no key, no account, fair-use
 * only — hence the caller's debounce. Pure URL building and lenient GeoJSON parsing;
 * the HTTP call itself is location/PhotonPlaceSearch.
 */
object Photon {

    /** Suggestions shown. More are requested so near-duplicate OSM objects can be dropped. */
    const val MAX_SUGGESTIONS = 5
    private const val REQUEST_LIMIT = 8

    // The public instance answers 400 for anything else — never pass a raw device language.
    private val SUPPORTED_LANGUAGES = setOf("de", "en", "fr")

    fun language(deviceLanguage: String): String =
        deviceLanguage.lowercase().takeIf { it in SUPPORTED_LANGUAGES } ?: "en"

    /**
     * [near] biases results towards a point; zoom 8 makes that a day's drive rather than
     * the default neighbourhood, so a destination two countries away still wins on rank.
     */
    fun searchUrl(
        query: String,
        near: LatLng?,
        language: String,
        tags: List<String> = emptyList(),
    ): String {
        val url = "https://photon.komoot.io/api?q=${URLEncoder.encode(query, "UTF-8")}" +
            "&limit=$REQUEST_LIMIT&lang=$language"
        val bias = if (near == null) "" else "&lat=${near.latitude}&lon=${near.longitude}&zoom=8"
        val filter = tags.joinToString("") { "&osm_tag=${URLEncoder.encode(it, "UTF-8")}" }
        return "$url$bias$filter"
    }

    /** The OSM tags behind each kind of stop, for privileging what the user is after. */
    fun preferredTags(kind: StopKind): List<String> = when (kind) {
        StopKind.CAMPSITE -> listOf("tourism:camp_site")
        StopKind.STELLPLATZ -> listOf("tourism:caravan_site")
        StopKind.FREE_CAMP -> listOf("highway:rest_area", "amenity:parking")
        StopKind.VISIT -> listOf("tourism:attraction")
    }

    /** Lenient by design: an error body or a surprise shape yields no suggestions, never a throw. */
    fun parse(body: String): List<PlaceSuggestion> {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        val features = root?.get("features") as? JsonArray ?: return emptyList()
        // One place is often several OSM objects (village, station, the building at it);
        // same name at the same address is one row, in Photon's relevance order.
        return features.mapNotNull(::suggestion)
            .distinctBy { it.name to it.label }
            .take(MAX_SUGGESTIONS)
    }

    private fun suggestion(element: JsonElement): PlaceSuggestion? {
        val feature = element as? JsonObject ?: return null
        val properties = feature["properties"] as? JsonObject ?: return null
        val geometry = feature["geometry"] as? JsonObject ?: return null
        if (geometry.string("type") != "Point") return null
        val coordinates = geometry["coordinates"] as? JsonArray ?: return null
        // GeoJSON order is [lon, lat] — the same trap as `adb emu geo fix`.
        val longitude = coordinates.getOrNull(0)?.double() ?: return null
        val latitude = coordinates.getOrNull(1)?.double() ?: return null
        val name = properties.string("name")
            ?: properties.string("street")
            ?: properties.string("city")
            ?: return null
        // City-states repeat themselves (Zurich's city and state are both "Zurich").
        val label = listOfNotNull(
            properties.string("city"),
            properties.string("state"),
            properties.string("country"),
        ).distinct().filterNot { it == name }.joinToString(", ")
        return PlaceSuggestion(name, label, LatLng(latitude, longitude))
    }

    /** Every property is optional: Photon emits a key only when it has a value. */
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonElement.double(): Double? = (this as? JsonPrimitive)?.content?.toDoubleOrNull()
}
