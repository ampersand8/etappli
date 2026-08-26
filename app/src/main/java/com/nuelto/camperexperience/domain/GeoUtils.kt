package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {

    private const val EARTH_RADIUS_KM = 6371.0

    fun haversineKm(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h))
    }

    /** Sum of the legs between consecutive points. */
    fun routeLengthKm(points: List<LatLng>): Double =
        points.zipWithNext().sumOf { (a, b) -> haversineKm(a, b) }
}
