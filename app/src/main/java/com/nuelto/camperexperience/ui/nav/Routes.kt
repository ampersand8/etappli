package com.nuelto.camperexperience.ui.nav

import kotlinx.serialization.Serializable

@Serializable
object TripListRoute

@Serializable
data class TripDetailRoute(val tripId: String)

@Serializable
data class TripEditRoute(val tripId: String? = null, val planned: Boolean = false)

@Serializable
data class StopEditRoute(val tripId: String, val stopId: String? = null)

@Serializable
data class AllTripsMapRoute(val tripId: String? = null)

/** [kind] is what the stop is meant to be, so the search can put those places first. */
@Serializable
data class LocationPickerRoute(val lat: Double?, val lon: Double?, val kind: String?)

@Serializable
object SettingsRoute
