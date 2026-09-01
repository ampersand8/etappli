package com.nuelto.etappli

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.nuelto.etappli.ui.map.GoogleMapProvider
import com.nuelto.etappli.ui.map.MapProvider

/**
 * Google Maps is configured at build time, mirroring FirebaseBackend: without a
 * `mapsApiKey` in local.properties — or on a device without Play services — this returns
 * null and the app runs with no map at all. Everything else still works. See
 * GOOGLE_MAPS_SETUP.md.
 */
fun googleMapProvider(context: Context): MapProvider? {
    if (BuildConfig.MAPS_API_KEY.isBlank()) return null
    val available = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    if (available != ConnectionResult.SUCCESS) return null
    return GoogleMapProvider
}
