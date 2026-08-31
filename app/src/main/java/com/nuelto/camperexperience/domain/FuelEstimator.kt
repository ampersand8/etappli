package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopLeg
import com.nuelto.camperexperience.data.model.UserSettings
import kotlin.math.min

object FuelEstimator {

    fun estimateCost(distanceKm: Double, consumptionL100km: Double, pricePerLiter: Double): Double =
        distanceKm * consumptionL100km / 100.0 * pricePerLiter

    /**
     * Automatic fuel estimate for a trip, derived from the driving distance between its
     * stops and what it costs to climb between them. Never stored — computed at display
     * time. Null when the trip already has a recorded FUEL expense (real costs replace
     * the estimate) or when fewer than two stops have a location.
     */
    fun autoTripFuelCost(stops: List<Stop>, expenses: List<Expense>, settings: UserSettings): Double? {
        if (expenses.any { it.type == ExpenseType.FUEL }) return null
        val distanceKm = defaultTripDistanceKm(stops, settings)
        if (distanceKm <= 0.0) return null
        val liters = distanceKm * settings.fuelConsumptionL100km / 100.0 + climbLiters(stops, settings)
        return liters * settings.fuelPricePerLiter
    }

    /**
     * Driving distance for the trip: the road distance Google routed for every leg that
     * has one, and straight-line × `roadDistanceFactor` for the rest. The factor is the
     * fallback now, not the rule — it covers legs that were never routed because there
     * was no key, no signal, or no route.
     *
     * Also the estimator sheet's prefill, so it stays user-editable.
     */
    fun defaultTripDistanceKm(stops: List<Stop>, settings: UserSettings): Double =
        RouteCache.drives(stops).sumOf { (from, to) -> driveDistanceKm(from, to, settings) }

    // Safe: RouteCache.drives keeps only stops that have a location.
    private fun driveDistanceKm(from: Stop, to: Stop, settings: UserSettings): Double =
        RouteCache.usable(from, to)?.let { it.distanceMeters / 1000.0 }
            ?: (GeoUtils.haversineKm(from.location!!, to.location!!) * settings.roadDistanceFactor)

    // The gravity term of the road-load equation: lower heating value of diesel, and a
    // drivetrain efficiency in the band a loaded van sits in on a long climb (0.30–0.35).
    private const val GRAVITY = 9.81
    private const val DIESEL_MJ_PER_LITER = 35.8
    private const val DRIVETRAIN_EFFICIENCY = 0.32

    /** Most of a descent's theoretical saving — never all of it, and never more than
     *  the fuel it replaces. */
    private const val DESCENT_RECOVERY = 0.85

    /**
     * Litres of diesel to lift [massKg] through [ascentMeters] — about 0.086 l per tonne
     * per 100 m of climb.
     *
     * Deliberately the gravity term alone: rolling resistance and drag are already inside
     * the user's l/100km figure, which was measured on real roads. Adding them here would
     * count them twice.
     */
    fun liftLiters(massKg: Double, ascentMeters: Double): Double =
        massKg * GRAVITY * ascentMeters / (DRIVETRAIN_EFFICIENCY * DIESEL_MJ_PER_LITER * 1e6)

    /**
     * What the trip's climbing adds over a flat route of the same length. Legs with no
     * elevation figure — never fetched, or the DEM service was unreachable — contribute
     * nothing, so the estimate degrades to distance-only rather than to nonsense.
     *
     * Treat the constants as unvalidated until they have been checked against a real
     * tankful: they are the right physics, but the efficiency term is a single number
     * standing in for a whole engine map.
     */
    fun climbLiters(stops: List<Stop>, settings: UserSettings): Double =
        RouteCache.drives(stops).sumOf { (from, to) ->
            legClimbLiters(
                RouteCache.usable(from, to),
                driveDistanceKm(from, to, settings),
                settings,
            )
        }

    private fun legClimbLiters(leg: StopLeg?, distanceKm: Double, settings: UserSettings): Double {
        if (leg == null) return 0.0
        val ascent = leg.ascentMeters?.toDouble() ?: 0.0
        val descent = leg.descentMeters?.toDouble() ?: 0.0
        if (ascent + descent <= 0.0) return 0.0
        // Split the leg's length between climbing and descending in proportion to the
        // heights, so the saving is bounded by the distance the descent actually covers.
        val descentKm = distanceKm * descent / (ascent + descent)
        // A non-hybrid cannot bank potential energy: going down, the only recovery is the
        // injectors shutting off on the overrun, which can at best cancel the fuel that
        // stretch would have burned anyway.
        val recovered = DESCENT_RECOVERY * min(
            liftLiters(settings.vehicleMassKg, descent),
            settings.fuelConsumptionL100km * descentKm / 100.0,
        )
        return liftLiters(settings.vehicleMassKg, ascent) - recovered
    }
}
