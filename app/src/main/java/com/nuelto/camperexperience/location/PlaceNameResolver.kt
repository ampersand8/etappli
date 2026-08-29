package com.nuelto.camperexperience.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.nuelto.camperexperience.data.model.LatLng
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reverse-geocodes a coordinate to the nearest village/city name using the platform
 * Geocoder (Play-services backed, no API key). Returns null when the geocoder is
 * unavailable, offline, or finds nothing.
 */
open class PlaceNameResolver(private val context: Context) {

    open suspend fun placeName(location: LatLng): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = withTimeoutOrNull(10_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<Address?> { cont ->
                    geocoder.getFromLocation(
                        location.latitude,
                        location.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                cont.resume(addresses.firstOrNull())
                            }

                            override fun onError(errorMessage: String?) {
                                cont.resume(null)
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching {
                        geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    }.getOrNull()?.firstOrNull()
                }
            }
        } ?: return null
        // Prefer the village/city; fall back to progressively coarser admin names.
        return address.locality
            ?: address.subLocality
            ?: address.subAdminArea
            ?: address.adminArea
    }
}
