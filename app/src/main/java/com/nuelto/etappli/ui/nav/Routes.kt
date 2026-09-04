package com.nuelto.etappli.ui.nav

import kotlinx.serialization.Serializable

@Serializable
object TripListRoute

@Serializable
data class TripDetailRoute(val tripId: String)

@Serializable
data class TripEditRoute(val tripId: String? = null)

/**
 * [insertBefore] is the timeline row key a new stop goes in front of; null appends.
 * The place arguments are how a shared location arrives — the same scalars the map
 * picker hands back, minus the Places clock (see StopEditViewModel.applyShared).
 */
@Serializable
data class StopEditRoute(
    val tripId: String,
    val stopId: String? = null,
    val insertBefore: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val placeName: String? = null,
    val placeId: String? = null,
    // Sharing a place says you are not standing at it: no GPS fix on top, even when the
    // share turned out to carry neither a name nor a coordinate.
    val fromShare: Boolean = false,
    // The shared coordinate was looked up on the Places API, not lifted from the link, so
    // it carries the §14.3 clock like a picked place (SharedPlace.fromPlaces).
    val fromPlaces: Boolean = false,
)

/**
 * A place shared into the app, waiting for a trip to belong to. Parsed scalars rather
 * than the raw share text: they are already host-checked and range-checked here, and
 * they survive process death on the back stack.
 */
@Serializable
data class AddToTripRoute(
    val name: String?,
    val lat: Double?,
    val lon: Double?,
    val placeId: String?,
    val link: String?,
    val approximate: Boolean,
)

@Serializable
data class AllTripsMapRoute(val tripId: String? = null)

/** [kind] is what the stop is meant to be, so the search can put those places first. */
@Serializable
data class LocationPickerRoute(val lat: Double?, val lon: Double?, val kind: String?)

@Serializable
object SettingsRoute
