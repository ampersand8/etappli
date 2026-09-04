package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.StopKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Google Places API (New): Autocomplete for the hits, Place Details for the one that is
 * chosen. Autocomplete because Text Search resolves a query to a single place — "grimsel"
 * should offer the pass, the lake and the hospice, not pick one. Pure request building
 * and lenient parsing; the HTTP calls are location/GooglePlacesSearch.
 */
object GooglePlaces {

    const val AUTOCOMPLETE_URL = "https://places.googleapis.com/v1/places:autocomplete"
    const val MAX_SUGGESTIONS = 8
    const val SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"
    const val MAX_RESULTS = 20

    // Pro tier: what a pin and its card need. Rating or photos would move every search
    // into Enterprise; Place Details fetches them for the one hit that is chosen.
    const val SEARCH_FIELD_MASK = "places.id,places.displayName,places.formattedAddress,places.location"

    /** Place Details for one id, used to refresh an expired coordinate. */
    fun detailsUrl(placeId: String, sessionToken: String? = null): String =
        "https://places.googleapis.com/v1/places/${placeId.encodePathSegment()}" +
            sessionToken?.let { "?sessionToken=${it.encodePathSegment()}" }.orEmpty()

    // Billing is per requested field and these push Place Details into a higher tier,
    // but they are the difference between a name and knowing whether to stay there.
    const val DETAILS_FIELD_MASK = "id,displayName,formattedAddress,location,rating," +
        "userRatingCount,editorialSummary,primaryTypeDisplayName,photos,reviews"

    /** The media URL for a photo handle, which already carries the places/ prefix. */
    fun photoUrl(photo: String, apiKey: String, maxPx: Int = 640): String =
        "https://places.googleapis.com/v1/$photo/media?maxHeightPx=$maxPx&maxWidthPx=$maxPx" +
            "&key=${apiKey.encodePathSegment()}"

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
        val filter = types.takeIf { it.isNotEmpty() }
            ?.joinToString(",", ",\"includedPrimaryTypes\":[", "]") { it.jsonString() }
            .orEmpty()
        return "{\"input\":${input.jsonString()},\"sessionToken\":${sessionToken.jsonString()}" +
            "${bias(near)}$filter}"
    }

    /**
     * Text Search, for the query as submitted: it locates what it finds, so the hits can
     * be pins. It answers a name with the one place and a kind of place with up to
     * [MAX_RESULTS] of them around [near] — Google Maps' own two-sided behaviour, which
     * is why the typing still goes through autocomplete. No session: it is not billed as one.
     */
    fun searchBody(query: String, near: LatLng?): String =
        "{\"textQuery\":${query.jsonString()},\"pageSize\":$MAX_RESULTS${bias(near)}}"

    private fun bias(near: LatLng?): String = near?.let {
        ",\"locationBias\":{\"circle\":{\"center\":{\"latitude\":${it.latitude}," +
            "\"longitude\":${it.longitude}},\"radius\":$BIAS_RADIUS_M}}"
    }.orEmpty()

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
        // Home is an address, not a category Google filters on.
        StopKind.HOME -> emptyList()
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

    /** A Text Search body: the located places, in Google's order. Lenient like the rest. */
    fun parseSearch(body: String): List<PlaceSuggestion> {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        val places = root?.get("places") as? JsonArray ?: return emptyList()
        return places.mapNotNull(::suggestion).take(MAX_RESULTS)
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
            details = details(place),
        )
    }

    /** Everything beyond a pin. Absent throughout for a place Google knows little about. */
    private fun details(place: JsonObject): PlaceDetails? {
        val rating = (place["rating"] as? JsonPrimitive)
            ?.takeIf { !it.isString }?.content?.toDoubleOrNull()
        val count = (place["userRatingCount"] as? JsonPrimitive)
            ?.takeIf { !it.isString }?.content?.toIntOrNull() ?: 0
        val details = PlaceDetails(
            kind = (place["primaryTypeDisplayName"] as? JsonObject)?.string("text").orEmpty(),
            rating = rating,
            ratingCount = count,
            summary = (place["editorialSummary"] as? JsonObject)?.string("text").orEmpty(),
            review = firstReview(place),
            photo = ((place["photos"] as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.string("name").orEmpty(),
        )
        return details.takeIf { !it.isEmpty }
    }

    private fun firstReview(place: JsonObject): String =
        (place["reviews"] as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .firstNotNullOfOrNull { (it["text"] as? JsonObject)?.string("text") }
            .orEmpty()

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
