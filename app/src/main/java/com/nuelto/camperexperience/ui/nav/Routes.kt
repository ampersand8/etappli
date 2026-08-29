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

@Serializable
data class LocationPickerRoute(val lat: Double? = null, val lon: Double? = null)

@Serializable
object SettingsRoute
