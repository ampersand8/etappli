package dev.simon.camperexperience.domain

import dev.simon.camperexperience.data.model.Expense
import dev.simon.camperexperience.data.model.ExpenseType
import dev.simon.camperexperience.data.model.Stop
import org.junit.Assert.assertEquals
import org.junit.Test

class CostCalculatorTest {

    private val stops = listOf(
        Stop(id = "s1", campingCostTotal = 84.0, nights = 3),
        Stop(id = "s2", campingCostTotal = 128.0, nights = 4),
    )
    private val expenses = listOf(
        Expense(id = "e1", type = ExpenseType.FUEL, amount = 145.30),
        Expense(id = "e2", type = ExpenseType.FUEL, amount = 98.60),
        Expense(id = "e3", type = ExpenseType.ROAD_TAX, amount = 61.40),
        Expense(id = "e4", type = ExpenseType.CAMPING, amount = 10.0),
    )

    @Test
    fun `trip total sums stop camping costs and all expenses`() {
        assertEquals(84.0 + 128.0 + 145.30 + 98.60 + 61.40 + 10.0, CostCalculator.tripTotal(stops, expenses), 1e-9)
    }

    @Test
    fun `trip nights sums stop nights`() {
        assertEquals(7, CostCalculator.tripNights(stops))
    }

    @Test
    fun `breakdown merges stop camping costs with camping expenses`() {
        val breakdown = CostCalculator.breakdown(stops, expenses)
        assertEquals(84.0 + 128.0 + 10.0, breakdown.getValue(ExpenseType.CAMPING), 1e-9)
        assertEquals(145.30 + 98.60, breakdown.getValue(ExpenseType.FUEL), 1e-9)
        assertEquals(61.40, breakdown.getValue(ExpenseType.ROAD_TAX), 1e-9)
    }

    @Test
    fun `breakdown omits empty categories`() {
        val breakdown = CostCalculator.breakdown(stops, emptyList())
        assertEquals(setOf(ExpenseType.CAMPING), breakdown.keys)
    }

    @Test
    fun `empty trip totals zero`() {
        assertEquals(0.0, CostCalculator.tripTotal(emptyList(), emptyList()), 1e-9)
        assertEquals(0, CostCalculator.tripNights(emptyList()))
    }
}
