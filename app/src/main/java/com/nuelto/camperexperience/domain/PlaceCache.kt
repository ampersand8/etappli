package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import java.time.LocalDate

/**
 * Google's Places Service Specific Terms §14.3 allow caching a coordinate from the
 * Places API for 30 consecutive calendar days, after which it must be deleted. Place
 * IDs carry no such limit, so an expired stop keeps its id and loses its coordinate
 * until a refresh puts one back.
 *
 * Coordinates from anywhere else — a GPS fix, the crosshair, an OSM search — have
 * `locationCachedAt == null` and never expire.
 */
object PlaceCache {

    const val RETENTION_DAYS = 30L

    fun isExpired(stop: Stop, today: LocalDate): Boolean {
        val cachedAt = stop.locationCachedAt ?: return false
        return !today.isBefore(cachedAt.plusDays(RETENTION_DAYS))
    }

    /** Stops whose coordinate is past its retention and needs refreshing or deleting. */
    fun expired(stops: List<Stop>, today: LocalDate): List<Stop> =
        stops.filter { isExpired(it, today) }

    /** Drops the coordinate, keeping the place id so it can be fetched again. */
    fun forget(stop: Stop): Stop = stop.copy(location = null, locationCachedAt = null)

    /** Records a freshly fetched Places coordinate against today. */
    fun refreshed(stop: Stop, place: PlaceSuggestion, today: LocalDate): Stop = stop.copy(
        location = place.location,
        placeId = place.id.ifBlank { stop.placeId },
        locationCachedAt = today,
    )
}
