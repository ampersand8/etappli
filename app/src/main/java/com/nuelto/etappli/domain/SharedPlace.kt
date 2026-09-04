package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import java.net.URLDecoder

/**
 * A place somebody shared into the app — Google Maps' share sheet, or any app sending a
 * `geo:` link — before anything has been looked up.
 *
 * The payload is scanned as a haystack, never parsed as a URL: Maps puts the name on the
 * line above the link, and the link carries the coordinate in one of several places.
 */
data class SharedPlace(
    val name: String = "",
    val location: LatLng? = null,
    /** A real Places id, only ever from `query_place_id=` — never an ftid or a cid. A wrong
     *  id would have [PlaceCacheSweeper] delete the coordinate 30 days on. */
    val placeId: String = "",
    /** A short link whose coordinate is one redirect away; blank when there is nothing to fetch. */
    val link: String = "",
    /** The coordinate is where the map camera sat, not the place — it can be kilometres out. */
    val approximate: Boolean = false,
    /** The coordinate was looked up on the Places API rather than lifted from the link, so
     *  the §14.3 clock applies to it like to a picked place. */
    val fromPlaces: Boolean = false,
) {
    /**
     * A name and nowhere to put it — the Maps app's links carry only an ftid, which nothing
     * turns into a coordinate — or a pin that is only the map camera. Not while a link is
     * still to be followed, and not for a place id, which fetches its own coordinate
     * ([PlaceCacheSweeper]).
     */
    val needsLookup: Boolean
        get() = link.isBlank() && name.isNotBlank() && placeId.isBlank() && (location == null || approximate)

    /**
     * Folds in the one place Text Search found for [name] ([PlaceSearch.find], the way the
     * picker opens a submitted name). Unchanged for no hit, or one with no coordinate.
     */
    fun locatedBy(hit: PlaceSuggestion?): SharedPlace {
        val at = hit?.location ?: return this
        return copy(location = at, placeId = hit.id.ifBlank { placeId }, approximate = false, fromPlaces = true)
    }

    /**
     * Folds in what following [link] produced. Returns this unchanged — [link] included, so
     * the fetch can be retried — when the redirect was unreachable or named nowhere.
     */
    fun expandedWith(redirect: String?): SharedPlace {
        val expanded = redirect?.let { parse(it) } ?: return this
        if (expanded.location == null && expanded.name.isBlank()) return this
        return copy(
            // The share sheet's own name beats the URL's: it is what the user saw.
            name = name.ifBlank { expanded.name },
            location = expanded.location ?: location,
            placeId = expanded.placeId.ifBlank { placeId },
            link = "",
            approximate = if (expanded.location != null) expanded.approximate else approximate,
        )
    }

    companion object {
        /** Shared text is untrusted; every regex below runs on at most this much of it. */
        const val MAX_TEXT = 2_000

        /**
         * Null when the text names nowhere. Deliberately not handled, each being its own
         * rule set with its own mutants: Plus Codes, DMS, `/maps/dir/` legs (whose `!1d`
         * is longitude first), `?cid=`, and turning an ftid into a place id.
         */
        fun parse(text: String?, subject: String = ""): SharedPlace? {
            val clean = text.orEmpty().take(MAX_TEXT).replace(CONTROLS, "")
            // A coordinate typed in plain sight, outside the link — whose own digits are
            // somebody else's rule. It is the last resort, and fills a link's blank.
            val loose = coordinateIn(URL.replace(clean, " "))
            val place = token(clean)?.let(::fromUrl) ?: loose ?: return null
            return place.copy(
                name = place.name.ifBlank { nameIn(clean) }.ifBlank { subject.take(MAX_TEXT).trim() },
                location = place.location ?: loose?.location,
            )
        }

        /** Hosts whose redirect we are willing to follow. The exact host, never a suffix. */
        fun isShortLink(url: String): Boolean = host(url) in SHORT_HOSTS
    }
}

// 17 decimals occur in the wild. COORD reads a whole value (a query, a path segment);
// TEXT_COORD below scans free text, where the rules have to be tighter.
private const val LAT = """([+-]?\d{1,2}(?:\.\d{1,17})?)"""
private const val LON = """([+-]?\d{1,3}(?:\.\d{1,17})?)"""

private val CONTROLS = Regex("[\\u200e\\u200f\\u202a-\\u202e]")
private val URL = Regex("""https?://\S+""")
private val COORD = Regex("""$LAT,\s*\+?$LON""")
// Free text is scanned, so whole numbers are out: "Flat 2, 14 Baker Street" is an
// address, and reading it as a coordinate would drop a pin in Gabon.
private val TEXT_COORD =
    Regex("""(?<![\d.])([+-]?\d{1,2}\.\d{1,17}),\s*([+-]?\d{1,3}\.\d{1,17})(?![\d.])""")
// The place itself, as Maps writes it into the data segment; "@" is only the camera.
private val DATA_PAIR = Regex("""!3d$LAT!4d$LON""")
private val CAMERA = Regex("""/@$LAT,$LON""")
private val PATH_COORD = Regex("""/maps/(?:search|place)/$LAT,\+?$LON""")
private val PLACE_SEGMENT = Regex("""/maps/place/([^/@?]+)""")
private val LABEL = Regex("""\(([^)]+)\)""")
private val PLACE_ID = Regex("""[A-Za-z0-9_-]{10,255}""")
private val GOOGLE = Regex("""(?:www\.|maps\.)?google(?:\.[a-z]{2,3}){1,2}""")
// A saved list, a My Maps drawing, somebody's live location: not a place to camp.
private val NOT_A_PLACE = Regex("""/maps/placelists/|/maps/d/|!2e2""")

private val SHORT_HOSTS = setOf("maps.app.goo.gl", "goo.gl", "app.goo.gl", "g.co")
// Where the place is named or pinned. Not "ll": that is the map camera, below.
private val QUERY_KEYS = listOf("q", "query", "daddr", "destination")
private val JUNK = charArrayOf('.', ',', ';', ':', '!', '?', ']', '}', '>', '"', '\'')

private fun token(text: String): String? {
    val trimmed = text.trim()
    // A geo: URI is the whole payload, label and all — "\S+" would cut it at the space.
    if (trimmed.startsWith("geo:")) return trimmed
    val url = URL.find(text)?.value?.substringBefore("%3C")?.trimEnd(*JUNK) ?: return null
    // A trailing ")" closes a "(Label)" as often as it closes a sentence.
    return if (url.endsWith(")") && !url.contains("(")) url.dropLast(1) else url
}

private fun fromUrl(url: String): SharedPlace? = when {
    url.startsWith("geo:") -> fromGeo(url)
    SharedPlace.isShortLink(url) -> fromShortLink(url)
    host(url).matches(GOOGLE) -> fromMaps(url)
    else -> null
}

/** RFC 5870, and Android's `geo:0,0?q=…` form where the path is only a placeholder. */
private fun fromGeo(uri: String): SharedPlace? {
    val query = params(uri.substringAfter('?', ""))["q"].orEmpty()
    val path = uri.removePrefix("geo:").substringBefore('?').substringBefore(';')
    val located = coordinateOf(query) ?: coordinateOf(path)
    // An address nobody geocoded is still worth handing to the stop editor.
    val name = label(query).ifBlank { if (located == null) nameLike(query.substringBefore('(')) else "" }
    return place(name, located)
}

private fun fromShortLink(url: String): SharedPlace {
    // The dynamic-link form carries the real URL in link=, so that one needs no network.
    val inner = params(url.substringAfter('?', ""))["link"]?.let(::fromUrl)
    return inner?.takeIf { it.location != null } ?: SharedPlace(link = url.replaceFirst("http://", "https://"))
}

private fun fromMaps(url: String): SharedPlace? {
    if (NOT_A_PLACE.containsMatchIn(url)) return null
    val path = url.substringBefore('?')
    val params = params(url.substringAfter('?', ""))
    val query = QUERY_KEYS.firstNotNullOfOrNull { params[it] }.orEmpty().removePrefix("loc:")
    // Where Maps writes the place's own coordinate, in the order it can be trusted.
    val exact = DATA_PAIR.findAll(url).lastOrNull()?.let(::coordinate)
        ?: coordinateOf(query)
        ?: PATH_COORD.find(path)?.let(::coordinate)
    val camera = CAMERA.find(url)?.let(::coordinate) ?: coordinateOf(params["ll"].orEmpty())
    val name = placeSegment(path)
        .ifBlank { label(query) }
        // A query that is not a coordinate is the place's name — or its address.
        .ifBlank { if (exact == null) nameLike(query.substringBefore('(')) else "" }
    return place(
        name = name,
        location = exact ?: camera,
        placeId = params["query_place_id"]?.takeIf { PLACE_ID.matches(it) }.orEmpty(),
        approximate = exact == null && camera != null,
    )
}

/** The name Maps puts in the path, when there is one. */
private fun placeSegment(path: String): String =
    nameLike(PLACE_SEGMENT.find(path)?.groupValues?.get(1)?.let(::decode).orEmpty())

/** A query value is a coordinate or it is a name — the whole of it, either way. */
private fun coordinateOf(value: String): LatLng? =
    COORD.matchEntire(value.substringBefore('(').trim())?.let(::coordinate)

/** Text that is really a coordinate names nothing — in the path or in the query. */
private fun nameLike(text: String): String =
    text.trim().takeIf { COORD.matchEntire(it) == null }.orEmpty()

/** The first line that is neither a link nor a coordinate: the share sheet's own name. */
private fun nameIn(text: String): String = text.lineSequence()
    .map { it.trim() }
    .firstOrNull {
        it.isNotBlank() && !it.startsWith("geo:") &&
            !URL.containsMatchIn(it) && !TEXT_COORD.containsMatchIn(it)
    }
    .orEmpty()

private fun coordinateIn(text: String): SharedPlace? =
    TEXT_COORD.find(text)?.let(::coordinate)?.let { SharedPlace(location = it) }

/** Rejects anything off the globe, and the 0,0 sentinel `geo:` uses for "no coordinate". */
private fun coordinate(match: MatchResult): LatLng? {
    val latitude = match.groupValues[1].toDouble()
    val longitude = match.groupValues[2].toDouble()
    val nowhere = latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
        (latitude == 0.0 && longitude == 0.0)
    return if (nowhere) null else LatLng(latitude, longitude)
}

private fun place(
    name: String,
    location: LatLng?,
    placeId: String = "",
    approximate: Boolean = false,
): SharedPlace? =
    if (name.isBlank() && location == null) null
    else SharedPlace(name, location, placeId, approximate = approximate)

private fun label(query: String): String = LABEL.find(query)?.groupValues?.get(1)?.trim().orEmpty()

private fun params(query: String): Map<String, String> = query.split('&')
    .filter { it.contains('=') }
    .associate { it.substringBefore('=') to decode(it.substringAfter('=')) }

private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

/** Userinfo and port stripped: `https://maps.app.goo.gl@evil.com/x` is evil.com's. */
private fun host(url: String): String = url.substringAfter("://", "")
    .substringBefore('/').substringBefore('?').substringBefore('#')
    .substringAfterLast('@').substringBefore(':')
    .lowercase()
