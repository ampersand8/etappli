package com.nuelto.camperexperience

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.nuelto.camperexperience.ui.map.GoogleMapProvider
import com.nuelto.camperexperience.ui.map.MapProvider

/**
 * Google Maps is optional at build time, mirroring FirebaseBackend: without a
 * `mapsApiKey` in local.properties — or on a device without Play services — this
 * returns null and the app falls back to MapLibre/OpenStreetMap.
 */
fun googleMapProvider(context: Context): MapProvider? {
    if (BuildConfig.MAPS_API_KEY.isBlank()) return null
    val available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    if (available != ConnectionResult.SUCCESS) return null
    return GoogleMapProvider
}
