package dev.simon.camperexperience.ui.nav

import kotlinx.serialization.Serializable

@Serializable
object TripListRoute

@Serializable
data class TripDetailRoute(val tripId: String)

@Serializable
data class TripEditRoute(val tripId: String? = null)

@Serializable
data class StopEditRoute(val tripId: String, val stopId: String? = null)

@Serializable
object AllTripsMapRoute

@Serializable
object SettingsRoute
