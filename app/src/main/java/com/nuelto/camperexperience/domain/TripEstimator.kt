package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopKind
import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.data.model.UserSettings

/**
 * Display-time cost estimate for PLANNED/ACTIVE trips. Never stored: recorded numbers
 * live in Trip.totalCost, estimates exist only here (the autoTripFuelCost convention).
 */
data class EstimateBreakdown(
    val fuel: Double,
    val fuelIsEstimate: Boolean,
    // Camping split: recorded prices (incl. CAMPING expenses) vs nights × default rate.
    val campingKnown: Double,
    val campingEstimated: Double,
    val roadTax: Double,
    val other: Double,
    val hasEstimates: Boolean,
) {
    val camping: Double get() = campingKnown + campingEstimated
    val total: Double get() = fuel + camping + roadTax + other
}

object TripEstimator {

    fun nightlyRate(kind: StopKind, settings: UserSettings): Double = when (kind) {
        StopKind.CAMPSITE -> settings.campsitePerNight
        StopKind.STELLPLATZ -> settings.stellplatzPerNight
        StopKind.FREE_CAMP, StopKind.VISIT, StopKind.HOME -> 0.0
    }

    fun estimate(stops: List<Stop>, expenses: List<Expense>, settings: UserSettings): EstimateBreakdown {
        val counted = stops.filterNot { it.state == StopState.SKIPPED }

        // Exactly one fuel source: the auto estimate fires only while there is no FUEL
        // expense at all; otherwise the recorded (possibly estimated) expenses are it.
        val autoFuel = FuelEstimator.autoTripFuelCost(stops, expenses, settings)
        val fuelExpenses = expenses.filter { it.type == ExpenseType.FUEL }
        val fuel = autoFuel ?: fuelExpenses.sumOf { it.amount }
        val fuelIsEstimate = autoFuel != null || fuelExpenses.any { it.isEstimate }

        val campingKnown = counted.filter { it.costKnown }.sumOf { it.campingCostTotal } +
            expenses.filter { it.type == ExpenseType.CAMPING }.sumOf { it.amount }
        val campingEstimated = counted.filterNot { it.costKnown }
            .sumOf { it.nights * nightlyRate(it.kind, settings) }

        return EstimateBreakdown(
            fuel = fuel,
            fuelIsEstimate = fuelIsEstimate,
            campingKnown = campingKnown,
            campingEstimated = campingEstimated,
            roadTax = expenses.filter { it.type == ExpenseType.ROAD_TAX }.sumOf { it.amount },
            other = expenses.filter { it.type == ExpenseType.OTHER }.sumOf { it.amount },
            hasEstimates = autoFuel != null || campingEstimated > 0 ||
                expenses.any { it.isEstimate && it.amount > 0 },
        )
    }
}
