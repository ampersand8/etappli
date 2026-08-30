package com.nuelto.camperexperience.domain

/**
 * Links back out to Google Maps. A stop chosen from Google keeps its place id, so it can
 * be shared or opened as the real place — reviews, photos, opening hours and all —
 * rather than as a bare coordinate.
 */
object MapsUri {

    /** The place itself, by id. Null when the stop was never a Google place. */
    fun place(placeId: String?): String? =
        placeId?.takeIf { it.isNotBlank() }
            ?.let { "https://www.google.com/maps/place/?q=place_id:${it.encodePathSegment()}" }

    /** A plain point, for a stop that has no place id. */
    fun point(latitude: Double, longitude: Double): String =
        "https://www.google.com/maps/search/?api=1&query=$latitude%2C$longitude"

    /** What to share for a stop: the place when known, the point otherwise, else nothing. */
    fun share(placeId: String?, latitude: Double?, longitude: Double?): String? = place(placeId)
        ?: latitude?.let { lat -> longitude?.let { lon -> point(lat, lon) } }
}
