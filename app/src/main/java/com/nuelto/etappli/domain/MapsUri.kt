package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng

/**
 * Links back out to Google Maps, in the documented `api=1` form. `query` is required
 * even when a place id is given — Maps falls back to it when the id will not resolve.
 * The shorter `maps/place/?q=place_id:` form is undocumented and does not reliably open
 * the place, which is exactly how it failed.
 */
object MapsUri {

    /** Null only when there is nothing at all to point at. */
    fun share(name: String, location: LatLng?, placeId: String?): String? {
        val query = location?.let { "${it.latitude},${it.longitude}" }
            ?: name.takeIf { it.isNotBlank() }
            ?: return null
        val id = placeId?.takeIf { it.isNotBlank() }
            ?.let { "&query_place_id=${it.encodePathSegment()}" }
            .orEmpty()
        return "https://www.google.com/maps/search/?api=1&query=${query.encodePathSegment()}$id"
    }
}
