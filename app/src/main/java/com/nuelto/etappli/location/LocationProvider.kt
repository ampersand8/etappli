package com.nuelto.etappli.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.nuelto.etappli.data.model.LatLng
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One-shot GPS fix for "use current position" — the app never tracks continuously.
 * Caller is responsible for holding ACCESS_FINE_LOCATION before calling.
 */
class LocationProvider(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): LatLng? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val fresh = withTimeoutOrNull(15_000) {
            runCatching {
                client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token,
                ).await()
            }.getOrNull()
        }
        val location = fresh ?: runCatching { client.lastLocation.await() }.getOrNull()
        return location?.let { LatLng(it.latitude, it.longitude) }
    }
}
