package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Google Places API (New), Text Search — chosen over Autocomplete because it returns
 * coordinates with the hit, which is what the picker needs to drop markers. Pure request
 * building and lenient parsing; the HTTP call is location/GooglePlacesSearch.
 */
object GooglePlaces {

    const val SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"
    const val MAX_SUGGESTIONS = 5

    /** Billing is per requested field, so ask for exactly what a suggestion needs. */
    const val FIELD_MASK = "places.id,places.displayName,places.formattedAddress,places.location"

    /** Place Details for one id, used to refresh an expired coordinate. */
    fun detailsUrl(placeId: String): String =
        "https://places.googleapis.com/v1/places/${placeId.encodePathSegment()}"

    const val DETAILS_FIELD_MASK = "id,displayName,formattedAddress,location"

    // The API caps the bias radius at 50 km; a day's drive is further, but bias only
    // reorders — a destination outside it still ranks on its own merits.
    private const val BIAS_RADIUS_M = 50_000.0

    fun searchBody(query: String, near: LatLng?, language: String): String {
        val bias = near?.let {
            ""","locationBias":{"circle":{"center":{"latitude":${it.latitude},""" +
                """"longitude":${it.longitude}},"radius":$BIAS_RADIUS_M}}"""
        }.orEmpty()
        return """{"textQuery":${query.jsonString()},"pageSize":$MAX_SUGGESTIONS,""" +
            """"languageCode":${language.jsonString()}$bias}"""
    }

    /** Lenient by design: an error body or a surprise shape yields no hits, never a throw. */
    fun parseSearch(body: String): List<PlaceSuggestion> {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        val places = root?.get("places") as? JsonArray ?: return emptyList()
        return places.mapNotNull(::suggestion)
            .distinctBy { it.name to it.label }
            .take(MAX_SUGGESTIONS)
    }

    /** A Place Details body, for refreshing one expired coordinate. */
    fun parseDetails(body: String): PlaceSuggestion? {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return null
        return suggestion(root)
    }

    private fun suggestion(element: JsonElement): PlaceSuggestion? {
        val place = element as? JsonObject ?: return null
        val location = place["location"] as? JsonObject ?: return null
        val latitude = location.double("latitude") ?: return null
        val longitude = location.double("longitude") ?: return null
        val name = (place["displayName"] as? JsonObject)?.string("text")
            ?: place.string("formattedAddress")
            ?: return null
        val label = place.string("formattedAddress")?.takeIf { it != name }.orEmpty()
        return PlaceSuggestion(
            name = name,
            label = label,
            location = LatLng(latitude, longitude),
            id = place.string("id").orEmpty(),
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
}

/** Minimal JSON string escaping — the query is user text going into a POST body. */
internal fun String.jsonString(): String {
    val escaped = buildString {
        this@jsonString.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
    }
    return "\"$escaped\""
}

/** Place IDs are URL-safe in practice, but never trust an id into a path unescaped. */
internal fun String.encodePathSegment(): String =
    java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")
