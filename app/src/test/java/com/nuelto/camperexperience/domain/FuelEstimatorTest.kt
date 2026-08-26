package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.UserSettings
import org.junit.Assert.assertEquals
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
    fun `stops without location are skipped`() {
        val stops = listOf(
            Stop(id = "a", location = LatLng(47.0, 8.0), orderIndex = 0),
            Stop(id = "b", location = null, orderIndex = 1),
        )
        assertEquals(0.0, FuelEstimator.defaultTripDistanceKm(stops, UserSettings()), 1e-9)
    }
}
