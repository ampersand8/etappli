package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.UserSettings

object FuelEstimator {

    fun estimateCost(distanceKm: Double, consumptionL100km: Double, pricePerLiter: Double): Double =
        distanceKm * consumptionL100km / 100.0 * pricePerLiter

    /**
     * Default distance to prefill the estimator with: straight-line legs between the
     * trip's stops (in order), scaled by the road-distance factor. Always user-editable.
     */
    fun defaultTripDistanceKm(stops: List<Stop>, settings: UserSettings): Double {
        val points = stops.sortedBy { it.orderIndex }.mapNotNull { it.location }
        return GeoUtils.routeLengthKm(points) * settings.roadDistanceFactor
    }
}
