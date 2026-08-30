package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.StopKind
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

    const val AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete"
    const val MAX_SUGGESTIONS = 8

    /** Place Details for one id, used to refresh an expired coordinate. */
    fun detailsUrl(placeId: String, sessionToken: String? = null): String =
        "https://places.googleapis.com/v1/places/${placeId.encodePathSegment()}" +
            sessionToken?.let { "?sessionToken=${it.encodePathSegment()}" }.orEmpty()

    const val DETAILS_FIELD_MASK = "id,displayName,formattedAddress,location"

    // The API caps the bias radius at 50 km; a day's drive is further, but bias only
    // reorders — a destination outside it still ranks on its own merits.
    private const val BIAS_RADIUS_M = 50_000.0

    /**
     * Autocomplete rather than Text Search: typing "grimsel" should offer the pass, the
     * lake, the hospice and the hotel, not resolve to one of them. Predictions carry no
     * coordinate, so [parseDetails] fills it in once a hit is chosen. [sessionToken]
     * groups the keystrokes and that final lookup into one billable session.
     */
    fun autocompleteBody(
        input: String,
        near: LatLng?,
        sessionToken: String,
        types: List<String> = emptyList(),
    ): String {
        val bias = near?.let {
            ",\"locationBias\":{\"circle\":{\"center\":{\"latitude\":${it.latitude}," +
                "\"longitude\":${it.longitude}},\"radius\":$BIAS_RADIUS_M}}"
        }.orEmpty()
        val filter = types.takeIf { it.isNotEmpty() }
            ?.joinToString(",", ",\"includedPrimaryTypes\":[", "]") { it.jsonString() }
            .orEmpty()
        return "{\"input\":${input.jsonString()},\"sessionToken\":${sessionToken.jsonString()}" +
            "$bias$filter}"
    }

    /**
     * Google's own categories for what the user says they are after. Free camping is not
     * a category Google has, so it aims at the car parks and rest areas that usually are
     * one. An unsupported type is a hard 400, so this list is deliberate.
     */
    fun preferredTypes(kind: StopKind): List<String> = when (kind) {
        StopKind.CAMPSITE -> listOf("campground")
        StopKind.STELLPLATZ -> listOf("rv_park", "campground")
        // Not "park": a municipal park is nowhere to overnight, and having few local
        // matches it pulled in weak ones from the other side of the continent.
        StopKind.FREE_CAMP -> listOf("parking", "rest_stop")
        StopKind.VISIT -> listOf("tourist_attraction")
    }

    /** Lenient by design: an error body or a surprise shape yields no hits, never a throw. */
    fun parseAutocomplete(body: String): List<PlaceSuggestion> {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        val suggestions = root?.get("suggestions") as? JsonArray ?: return emptyList()
        return suggestions.mapNotNull(::prediction)
            .distinctBy { it.name to it.label }
            .take(MAX_SUGGESTIONS)
    }

    /** A query prediction ("pizza near me") names no place, so it is skipped. */
    private fun prediction(element: JsonElement): PlaceSuggestion? {
        val prediction = (element as? JsonObject)?.get("placePrediction") as? JsonObject ?: return null
        val id = prediction.string("placeId") ?: return null
        val format = prediction["structuredFormat"] as? JsonObject
        val name = (format?.get("mainText") as? JsonObject)?.string("text")
            ?: (prediction["text"] as? JsonObject)?.string("text")
            ?: return null
        val label = (format?.get("secondaryText") as? JsonObject)?.string("text").orEmpty()
        return PlaceSuggestion(name = name, label = label, location = null, id = id)
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
