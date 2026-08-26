package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun `haversine of identical points is zero`() {
        val p = LatLng(47.3769, 8.5417)
        assertEquals(0.0, GeoUtils.haversineKm(p, p), 1e-9)
    }

    @Test
    fun `haversine zurich to bern is about 95 km`() {
        val zurich = LatLng(47.3769, 8.5417)
        val bern = LatLng(46.9480, 7.4474)
        assertEquals(95.0, GeoUtils.haversineKm(zurich, bern), 3.0)
    }

    @Test
    fun `route length sums consecutive legs`() {
        val a = LatLng(47.0, 8.0)
        val b = LatLng(47.5, 8.0)
        val c = LatLng(48.0, 8.0)
        val expected = GeoUtils.haversineKm(a, b) + GeoUtils.haversineKm(b, c)
        assertEquals(expected, GeoUtils.routeLengthKm(listOf(a, b, c)), 1e-9)
    }

    @Test
    fun `route length of single point is zero`() {
        assertEquals(0.0, GeoUtils.routeLengthKm(listOf(LatLng(47.0, 8.0))), 1e-9)
    }
}
