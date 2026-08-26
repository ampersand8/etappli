package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.Stop

object CostCalculator {

    fun tripTotal(stops: List<Stop>, expenses: List<Expense>): Double =
        stops.sumOf { it.campingCostTotal } + expenses.sumOf { it.amount }

    fun tripNights(stops: List<Stop>): Int = stops.sumOf { it.nights }

    /**
     * Cost per category. Camping costs recorded on stops count toward CAMPING,
     * alongside any extra CAMPING-typed expenses. Categories with zero cost are omitted.
     */
    fun breakdown(stops: List<Stop>, expenses: List<Expense>): Map<ExpenseType, Double> {
        val result = linkedMapOf<ExpenseType, Double>()
        val camping = stops.sumOf { it.campingCostTotal } +
            expenses.filter { it.type == ExpenseType.CAMPING }.sumOf { it.amount }
        if (camping > 0) result[ExpenseType.CAMPING] = camping
        for (type in listOf(ExpenseType.FUEL, ExpenseType.ROAD_TAX, ExpenseType.OTHER)) {
            val sum = expenses.filter { it.type == type }.sumOf { it.amount }
            if (sum > 0) result[type] = sum
        }
        return result
    }
}
