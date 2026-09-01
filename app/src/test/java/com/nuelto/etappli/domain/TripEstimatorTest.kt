package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.Expense
import com.nuelto.etappli.data.model.ExpenseType
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripEstimatorTest {

    private val settings = UserSettings(
        campsitePerNight = 45.0,
        stellplatzPerNight = 15.0,
        fuelConsumptionL100km = 10.0,
        fuelPricePerLiter = 2.0,
        roadDistanceFactor = 1.25,
    )

    @Test
    fun `nightly rate is tiered by kind`() {
        assertEquals(45.0, TripEstimator.nightlyRate(StopKind.CAMPSITE, settings), 1e-9)
        assertEquals(15.0, TripEstimator.nightlyRate(StopKind.STELLPLATZ, settings), 1e-9)
        assertEquals(0.0, TripEstimator.nightlyRate(StopKind.FREE_CAMP, settings), 1e-9)
        assertEquals(0.0, TripEstimator.nightlyRate(StopKind.VISIT, settings), 1e-9)
    }

    @Test
    fun `camping splits known prices from default-rate estimates`() {
        val stops = listOf(
            Stop(id = "known", nights = 3, campingCostTotal = 186.0),
            Stop(id = "est-camp", nights = 2, costKnown = false),
            Stop(id = "est-stell", nights = 1, kind = StopKind.STELLPLATZ, costKnown = false),
            Stop(id = "free", nights = 2, kind = StopKind.FREE_CAMP, costKnown = false),
            Stop(id = "visit", nights = 0, kind = StopKind.VISIT),
        )
        val estimate = TripEstimator.estimate(stops, emptyList(), settings)
        assertEquals(186.0, estimate.campingKnown, 1e-9)
        assertEquals(2 * 45.0 + 15.0, estimate.campingEstimated, 1e-9)
        assertEquals(186.0 + 105.0, estimate.camping, 1e-9)
        assertTrue(estimate.hasEstimates)
    }

    @Test
    fun `camping expenses count as known camping`() {
        val extraFee = Expense(id = "e", type = ExpenseType.CAMPING, amount = 10.0)
        val estimate = TripEstimator.estimate(emptyList(), listOf(extraFee), settings)
        assertEquals(10.0, estimate.campingKnown, 1e-9)
        assertFalse(estimate.hasEstimates)
    }

    @Test
    fun `skipped stops are excluded from both camping buckets`() {
        val stops = listOf(
            Stop(id = "skip-known", nights = 2, campingCostTotal = 80.0, state = StopState.SKIPPED),
            Stop(id = "skip-est", nights = 2, costKnown = false, state = StopState.SKIPPED),
            Stop(id = "kept", nights = 1, costKnown = false),
        )
        val estimate = TripEstimator.estimate(stops, emptyList(), settings)
        assertEquals(0.0, estimate.campingKnown, 1e-9)
        assertEquals(45.0, estimate.campingEstimated, 1e-9)
    }

    @Test
    fun `fuel is the auto estimate while no fuel expense exists`() {
        val stops = listOf(
            Stop(id = "a", location = LatLng(47.0, 8.0), orderIndex = 0),
            Stop(id = "b", location = LatLng(48.0, 8.0), orderIndex = 1),
        )
        val expected = FuelEstimator.autoTripFuelCost(stops, emptyList(), settings)!!
        val estimate = TripEstimator.estimate(stops, emptyList(), settings)
        assertEquals(expected, estimate.fuel, 1e-9)
        assertTrue(estimate.fuelIsEstimate)
        assertTrue(estimate.hasEstimates)
    }

    @Test
    fun `a fuel expense replaces the auto estimate entirely - no double count`() {
        val stops = listOf(
            Stop(id = "a", location = LatLng(47.0, 8.0), orderIndex = 0),
            Stop(id = "b", location = LatLng(48.0, 8.0), orderIndex = 1),
        )
        val plannedFuel = Expense(id = "f", type = ExpenseType.FUEL, amount = 120.0, isEstimate = true)
        val estimate = TripEstimator.estimate(stops, listOf(plannedFuel), settings)
        assertEquals(120.0, estimate.fuel, 1e-9)
        assertTrue(estimate.fuelIsEstimate)
        assertEquals(120.0, estimate.total, 1e-9)
    }

    @Test
    fun `recorded fuel expenses are not flagged as estimate`() {
        val real = Expense(id = "f", type = ExpenseType.FUEL, amount = 87.20)
        val estimate = TripEstimator.estimate(emptyList(), listOf(real), settings)
        assertEquals(87.20, estimate.fuel, 1e-9)
        assertFalse(estimate.fuelIsEstimate)
        assertFalse(estimate.hasEstimates)
    }

    @Test
    fun `road tax and other sum their expenses and estimate flags carry over`() {
        val expenses = listOf(
            Expense(id = "v", type = ExpenseType.ROAD_TAX, amount = 40.0, isEstimate = true),
            Expense(id = "o", type = ExpenseType.OTHER, amount = 12.0),
        )
        val estimate = TripEstimator.estimate(emptyList(), expenses, settings)
        assertEquals(40.0, estimate.roadTax, 1e-9)
        assertEquals(12.0, estimate.other, 1e-9)
        assertEquals(52.0, estimate.total, 1e-9)
        assertTrue(estimate.hasEstimates)
    }

    @Test
    fun `total adds all four buckets`() {
        val stops = listOf(
            Stop(id = "known", nights = 3, campingCostTotal = 186.0),
            Stop(id = "est", nights = 2, costKnown = false),
        )
        val expenses = listOf(
            Expense(id = "f", type = ExpenseType.FUEL, amount = 100.0, isEstimate = true),
            Expense(id = "v", type = ExpenseType.ROAD_TAX, amount = 40.0, isEstimate = true),
            Expense(id = "o", type = ExpenseType.OTHER, amount = 12.0),
        )
        val estimate = TripEstimator.estimate(stops, expenses, settings)
        assertEquals(100.0 + 186.0 + 90.0 + 40.0 + 12.0, estimate.total, 1e-9)
    }

    @Test
    fun `an empty trip has no estimates`() {
        val estimate = TripEstimator.estimate(emptyList(), emptyList(), settings)
        assertEquals(0.0, estimate.total, 1e-9)
        assertFalse(estimate.fuelIsEstimate)
        assertFalse(estimate.hasEstimates)
    }
}
