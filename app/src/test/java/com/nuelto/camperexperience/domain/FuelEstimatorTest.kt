package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopLeg
import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.data.model.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelEstimatorTest {

    @Test
    fun `estimate is distance times consumption times price`() {
        // 500 km at 10 l/100km and 2.00/l -> 50 l -> 100.00
        assertEquals(100.0, FuelEstimator.estimateCost(500.0, 10.0, 2.0), 1e-9)
    }

    @Test
    fun `zero distance estimates zero`() {
        assertEquals(0.0, FuelEstimator.estimateCost(0.0, 11.5, 1.85), 1e-9)
    }

    @Test
    fun `default trip distance scales haversine legs by road factor`() {
        val stops = listOf(
            Stop(id = "a", location = LatLng(47.0, 8.0), orderIndex = 0),
            Stop(id = "b", location = LatLng(48.0, 8.0), orderIndex = 1),
        )
        val settings = UserSettings(roadDistanceFactor = 1.25)
        val straight = GeoUtils.haversineKm(LatLng(47.0, 8.0), LatLng(48.0, 8.0))
        assertEquals(straight * 1.25, FuelEstimator.defaultTripDistanceKm(stops, settings), 1e-9)
    }

    @Test
    fun `auto trip fuel cost derives from driving distance between stops`() {
        val stops = listOf(
            Stop(id = "a", location = LatLng(47.0, 8.0), orderIndex = 0),
            Stop(id = "b", location = LatLng(48.0, 8.0), orderIndex = 1),
        )
        val settings = UserSettings(
            roadDistanceFactor = 1.25,
            fuelConsumptionL100km = 10.0,
            fuelPricePerLiter = 2.0,
        )
        val distance = GeoUtils.haversineKm(LatLng(47.0, 8.0), LatLng(48.0, 8.0)) * 1.25
        assertEquals(
            distance * 10.0 / 100.0 * 2.0,
            FuelEstimator.autoTripFuelCost(stops, emptyList(), settings)!!,
            1e-9,
        )
    }

    @Test
    fun `auto trip fuel cost is replaced by recorded fuel expenses`() {
        val stops = listOf(
            Stop(id = "a", location = LatLng(47.0, 8.0), orderIndex = 0),
            Stop(id = "b", location = LatLng(48.0, 8.0), orderIndex = 1),
        )
        val fuel = Expense(id = "e1", type = ExpenseType.FUEL, amount = 80.0)
        assertNull(FuelEstimator.autoTripFuelCost(stops, listOf(fuel), UserSettings()))
        // Fuel logged after other expense types must suppress the estimate too.
        val tax = Expense(id = "e2", type = ExpenseType.ROAD_TAX, amount = 10.0)
        assertNull(FuelEstimator.autoTripFuelCost(stops, listOf(tax, fuel), UserSettings()))
    }

    @Test
    fun `non-fuel expenses do not suppress the auto estimate`() {
        val stops = listOf(
            Stop(id = "a", location = LatLng(47.0, 8.0), orderIndex = 0),
            Stop(id = "b", location = LatLng(48.0, 8.0), orderIndex = 1),
        )
        val other = Expense(id = "e1", type = ExpenseType.ROAD_TAX, amount = 40.0)
        val estimate = FuelEstimator.autoTripFuelCost(stops, listOf(other), UserSettings())
        assertEquals(true, estimate != null && estimate > 0.0)
    }

    @Test
    fun `auto trip fuel cost is null without a route`() {
        val stops = listOf(Stop(id = "a", location = LatLng(47.0, 8.0), orderIndex = 0))
        assertNull(FuelEstimator.autoTripFuelCost(stops, emptyList(), UserSettings()))
        assertNull(FuelEstimator.autoTripFuelCost(emptyList(), emptyList(), UserSettings()))
    }

    @Test
    fun `stops are routed by order index, not list order`() {
        val a = Stop(id = "a", location = LatLng(47.0, 8.0), orderIndex = 0)
        val b = Stop(id = "b", location = LatLng(47.0, 9.0), orderIndex = 1)
        val c = Stop(id = "c", location = LatLng(48.0, 8.0), orderIndex = 2)
        val ordered = GeoUtils.routeLengthKm(listOf(a.location!!, b.location!!, c.location!!))
        // Shuffled input must still be routed a -> b -> c.
        assertEquals(
            ordered * 1.25,
            FuelEstimator.defaultTripDistanceKm(listOf(c, a, b), UserSettings(roadDistanceFactor = 1.25)),
            1e-9,
        )
    }

    @Test
    fun `stops without location are skipped`() {
        val stops = listOf(
            Stop(id = "a", location = LatLng(47.0, 8.0), orderIndex = 0),
            Stop(id = "b", location = null, orderIndex = 1),
        )
        assertEquals(0.0, FuelEstimator.defaultTripDistanceKm(stops, UserSettings()), 1e-9)
    }

    @Test
    fun `skipped stops do not contribute route distance`() {
        val stops = listOf(
            Stop(id = "a", location = LatLng(47.0, 8.0), orderIndex = 0),
            Stop(id = "detour", location = LatLng(50.0, 12.0), orderIndex = 1, state = StopState.SKIPPED),
            Stop(id = "b", location = LatLng(48.0, 8.0), orderIndex = 2),
        )
        val direct = GeoUtils.haversineKm(LatLng(47.0, 8.0), LatLng(48.0, 8.0))
        assertEquals(
            direct * 1.25,
            FuelEstimator.defaultTripDistanceKm(stops, UserSettings(roadDistanceFactor = 1.25)),
            1e-9,
        )
    }

    @Test
    fun `routed legs beat the road factor, and mix with legs that have none`() {
        val a = LatLng(47.0, 8.0)
        val b = LatLng(48.0, 8.0)
        val c = LatLng(48.0, 9.0)
        val stops = listOf(
            Stop(id = "a", location = a, orderIndex = 0),
            Stop(id = "b", location = b, orderIndex = 1, leg = routeLeg(a, b, 150_000)),
            Stop(id = "c", location = c, orderIndex = 2),
        )
        val settings = UserSettings(roadDistanceFactor = 1.25)
        val km = FuelEstimator.defaultTripDistanceKm(stops, settings)
        assertEquals(150.0 + GeoUtils.haversineKm(b, c) * 1.25, km, 1e-9)
        assertEquals(243.0, km, 0.01)
    }

    @Test
    fun `a leg that no longer matches its stops falls back to the road factor`() {
        val a = LatLng(47.0, 8.0)
        val moved = LatLng(48.0, 9.0)
        val settings = UserSettings(roadDistanceFactor = 1.25)
        val fallback = GeoUtils.haversineKm(a, moved) * 1.25
        // Routed to (48,8); the stop has since been dragged east.
        val movedTo = listOf(
            Stop(id = "a", location = a, orderIndex = 0),
            Stop(id = "b", location = moved, orderIndex = 1, leg = routeLeg(a, LatLng(48.0, 8.0), 999_000)),
        )
        assertEquals(fallback, FuelEstimator.defaultTripDistanceKm(movedTo, settings), 1e-9)
        assertTrue(FuelEstimator.defaultTripDistanceKm(movedTo, settings) < 200.0)
        // Same the other way round: the stop it was routed from is not the one before it now.
        val movedFrom = listOf(
            Stop(id = "a", location = a, orderIndex = 0),
            Stop(id = "b", location = moved, orderIndex = 1, leg = routeLeg(LatLng(46.0, 8.0), moved, 999_000)),
        )
        assertEquals(fallback, FuelEstimator.defaultTripDistanceKm(movedFrom, settings), 1e-9)
    }

    @Test
    fun `lifting one tonne 100 m burns about 86 millilitres`() {
        assertEquals(0.086, FuelEstimator.liftLiters(1000.0, 100.0), 5e-4)
        assertEquals(0.0, FuelEstimator.liftLiters(3500.0, 0.0), 1e-12)
        assertEquals(
            2.0 * FuelEstimator.liftLiters(1000.0, 100.0),
            FuelEstimator.liftLiters(2000.0, 100.0),
            1e-12,
        )
    }

    @Test
    fun `climbing costs nothing without an elevation figure, or on the flat`() {
        val a = LatLng(47.0, 8.0)
        val b = LatLng(48.0, 8.0)
        val unrouted = listOf(
            Stop(id = "a", location = a, orderIndex = 0),
            Stop(id = "b", location = b, orderIndex = 1),
        )
        assertEquals(0.0, FuelEstimator.climbLiters(unrouted, UserSettings()), 1e-12)
        // Routed, but the DEM never answered.
        val noElevation = listOf(
            Stop(id = "a", location = a, orderIndex = 0),
            Stop(id = "b", location = b, orderIndex = 1, leg = routeLeg(a, b, 120_000)),
        )
        assertEquals(0.0, FuelEstimator.climbLiters(noElevation, UserSettings()), 1e-12)
        val flat = listOf(
            Stop(id = "a", location = a, orderIndex = 0),
            Stop(id = "b", location = b, orderIndex = 1, leg = routeLeg(a, b, 120_000, 0, 0)),
        )
        assertEquals(0.0, FuelEstimator.climbLiters(flat, UserSettings()), 1e-12)
    }

    @Test
    fun `a pure climb costs the lift, and a descent gives part of it back`() {
        val climb = FuelEstimator.climbLiters(hillyTrip(1000, 0, 90_000), UserSettings())
        assertEquals(2.99712, climb, 1e-5)

        // 90 km split 60/30 by the heights: recovery is 0.85 x lifting 3.5 t over 500 m.
        val rolling = FuelEstimator.climbLiters(hillyTrip(1000, 500, 90_000), UserSettings())
        assertEquals(1.72334, rolling, 1e-5)
        assertTrue(rolling > 0.0 && rolling < climb)
    }

    @Test
    fun `descent recovery is capped by the fuel that stretch would have burned`() {
        // 2 km of road losing 2000 m: the potential energy dwarfs the fuel on offer.
        val settings = UserSettings(fuelConsumptionL100km = 10.0, vehicleMassKg = 3500.0)
        val recovered = -FuelEstimator.climbLiters(hillyTrip(0, 2000, 2_000), settings)
        assertEquals(0.17, recovered, 1e-9)
        assertTrue(recovered < 0.85 * FuelEstimator.liftLiters(3500.0, 2000.0))
    }

    @Test
    fun `auto trip fuel cost pays for the climb as well as the distance`() {
        val settings = UserSettings(fuelConsumptionL100km = 10.0, fuelPricePerLiter = 1.8)
        val flat = FuelEstimator.autoTripFuelCost(hillyTrip(null, 0, 100_000), emptyList(), settings)!!
        val climbing = FuelEstimator.autoTripFuelCost(hillyTrip(1000, 0, 100_000), emptyList(), settings)!!
        assertEquals(18.0, flat, 1e-9)
        assertEquals(23.394815, climbing, 1e-5)
        assertTrue(climbing > flat)

        val fuel = Expense(id = "e1", type = ExpenseType.FUEL, amount = 90.0)
        assertNull(FuelEstimator.autoTripFuelCost(hillyTrip(1000, 0, 100_000), listOf(fuel), settings))
    }

    private fun routeLeg(
        from: LatLng,
        to: LatLng,
        distanceMeters: Int,
        ascent: Int? = null,
        descent: Int? = null,
    ) = StopLeg(
        from = from,
        to = to,
        distanceMeters = distanceMeters,
        ascentMeters = ascent,
        descentMeters = descent,
    )

    /** Two stops 47N-48N apart, joined by one routed leg of the given shape. */
    private fun hillyTrip(ascent: Int?, descent: Int?, distanceMeters: Int): List<Stop> {
        val a = LatLng(47.0, 8.0)
        val b = LatLng(48.0, 8.0)
        return listOf(
            Stop(id = "a", location = a, orderIndex = 0),
            Stop(id = "b", location = b, orderIndex = 1, leg = routeLeg(a, b, distanceMeters, ascent, descent)),
        )
    }
}
