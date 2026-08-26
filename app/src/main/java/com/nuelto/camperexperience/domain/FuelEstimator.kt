package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.UserSettings

object FuelEstimator {

    fun estimateCost(distanceKm: Double, consumptionL100km: Double, pricePerLiter: Double): Double =
        distanceKm * consumptionL100km / 100.0 * pricePerLiter

    /**
     * Automatic fuel estimate for a trip, derived from the driving distance between its
     * stops. Never stored — computed at display time. Null when the trip already has a
     * recorded FUEL expense (real costs replace the estimate) or when fewer than two
     * stops have a location.
     */
    fun autoTripFuelCost(stops: List<Stop>, expenses: List<Expense>, settings: UserSettings): Double? {
        if (expenses.any { it.type == ExpenseType.FUEL }) return null
        val distanceKm = defaultTripDistanceKm(stops, settings)
        if (distanceKm <= 0.0) return null
        return estimateCost(distanceKm, settings.fuelConsumptionL100km, settings.fuelPricePerLiter)
    }

    /**
     * Default distance to prefill the estimator with: straight-line legs between the
     * trip's stops (in order), scaled by the road-distance factor. Always user-editable.
     */
    fun defaultTripDistanceKm(stops: List<Stop>, settings: UserSettings): Double {
        val points = stops.sortedBy { it.orderIndex }.mapNotNull { it.location }
        return GeoUtils.routeLengthKm(points) * settings.roadDistanceFactor
    }
}
