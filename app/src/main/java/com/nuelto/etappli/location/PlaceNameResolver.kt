package com.nuelto.etappli.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.StopRegion
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reverse-geocodes a coordinate using the platform Geocoder (Play-services backed, no
 * API key). Returns null when the geocoder is unavailable, offline, or finds nothing.
 */
open class PlaceNameResolver(private val context: Context) {

    /** The nearest village/city name, falling back to progressively coarser admin names. */
    open suspend fun placeName(location: LatLng): String? {
        val address = address(location) ?: return null
        return address.locality
            ?: address.subLocality
            ?: address.subAdminArea
            ?: address.adminArea
    }

    /** The canton, province or state a coordinate lies in, and its country — what names a tour. */
    open suspend fun region(location: LatLng): StopRegion? {
        val address = address(location) ?: return null
        return StopRegion(location, address.adminArea.orEmpty(), address.countryName.orEmpty())
    }

    private suspend fun address(location: LatLng): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return withTimeoutOrNull(10_000) {
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
        }
    }
}
