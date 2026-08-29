package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
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
    fun `zero-cost categories are omitted, not listed as zero`() {
        assertEquals(emptyMap<ExpenseType, Double>(), CostCalculator.breakdown(emptyList(), emptyList()))
        val freeStop = Stop(id = "s1", campingCostTotal = 0.0)
        assertEquals(
            setOf(ExpenseType.FUEL),
            CostCalculator.breakdown(listOf(freeStop), listOf(Expense(id = "e", type = ExpenseType.FUEL, amount = 5.0))).keys,
        )
    }

    @Test
    fun `breakdown lists categories in fixed order`() {
        val all = listOf(
            Expense(id = "o", type = ExpenseType.OTHER, amount = 1.0),
            Expense(id = "r", type = ExpenseType.ROAD_TAX, amount = 2.0),
            Expense(id = "f", type = ExpenseType.FUEL, amount = 3.0),
            Expense(id = "c", type = ExpenseType.CAMPING, amount = 4.0),
        )
        assertEquals(
            listOf(ExpenseType.CAMPING, ExpenseType.FUEL, ExpenseType.ROAD_TAX, ExpenseType.OTHER),
            CostCalculator.breakdown(emptyList(), all).keys.toList(),
        )
    }

    @Test
    fun `empty trip totals zero`() {
        assertEquals(0.0, CostCalculator.tripTotal(emptyList(), emptyList()), 1e-9)
        assertEquals(0, CostCalculator.tripNights(emptyList()))
    }

    @Test
    fun `skipped stops count toward nothing`() {
        val withSkipped = stops + Stop(
            id = "s3",
            campingCostTotal = 55.0,
            nights = 2,
            state = StopState.SKIPPED,
        )
        assertEquals(CostCalculator.tripTotal(stops, expenses), CostCalculator.tripTotal(withSkipped, expenses), 1e-9)
        assertEquals(7, CostCalculator.tripNights(withSkipped))
        assertEquals(
            84.0 + 128.0 + 10.0,
            CostCalculator.breakdown(withSkipped, expenses).getValue(ExpenseType.CAMPING),
            1e-9,
        )
    }

    @Test
    fun `done stops still count`() {
        val done = listOf(Stop(id = "s1", campingCostTotal = 30.0, nights = 1, state = StopState.DONE))
        assertEquals(30.0, CostCalculator.tripTotal(done, emptyList()), 1e-9)
        assertEquals(1, CostCalculator.tripNights(done))
    }
}
