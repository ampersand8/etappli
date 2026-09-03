package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineTest {

    /** One complete point: (38.5, -120.2). */
    private val firstPoint = "_p~iF~ps|U"

    private fun assertPoint(lat: Double, lng: Double, actual: LatLng) {
        assertEquals(lat, actual.latitude, 1e-9)
        assertEquals(lng, actual.longitude, 1e-9)
    }

    @Test
    fun `google's worked example decodes to its three points`() {
        val points = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, points.size)
        assertPoint(38.5, -120.2, points[0])
        assertPoint(40.7, -120.95, points[1])
        assertPoint(43.252, -126.453, points[2])
    }

    @Test
    fun `google's worked example encodes back to its string, and a line survives the round trip`() {
        val points = listOf(LatLng(38.5, -120.2), LatLng(40.7, -120.95), LatLng(43.252, -126.453))
        assertEquals("_p~iF~ps|U_ulLnnqC_mqNvxq`@", Polyline.encode(points))
        assertEquals("_ibE~hbE", Polyline.encode(listOf(LatLng(1.0, -1.0))))
        // A delta of exactly one continuation chunk's worth still needs its second character.
        assertEquals("_@?", Polyline.encode(listOf(LatLng(0.00016, 0.0))))
        assertPoint(0.00016, 0.0, Polyline.decode("_@?").single())
        assertEquals("", Polyline.encode(emptyList()))
        val back = Polyline.decode(Polyline.encode(points))
        assertEquals(3, back.size)
        back.forEachIndexed { index, point -> assertPoint(points[index].latitude, points[index].longitude, point) }
    }

    @Test
    fun `an empty string decodes to no points`() {
        assertEquals(emptyList<LatLng>(), Polyline.decode(""))
    }

    @Test
    fun `a string ending mid-latitude keeps the points read so far`() {
        val points = Polyline.decode(firstPoint + "_")
        assertEquals(1, points.size)
        assertPoint(38.5, -120.2, points[0])
    }

    @Test
    fun `a string ending mid-longitude drops the half-read point`() {
        val points = Polyline.decode(firstPoint + "_ulL" + "_")
        assertEquals(1, points.size)
        assertPoint(38.5, -120.2, points[0])
    }

    @Test
    fun `a lone continuation chunk decodes to nothing`() {
        assertEquals(emptyList<LatLng>(), Polyline.decode("_"))
    }

    @Test
    fun `an odd zig-zag value comes back negative`() {
        val points = Polyline.decode("_ibE~hbE")
        assertEquals(1, points.size)
        assertPoint(1.0, -1.0, points[0])
        assertTrue(points[0].longitude < 0.0)
    }

    @Test
    fun `deltas accumulate rather than restart`() {
        val points = Polyline.decode("_ibE_ibE_ibE_ibE")
        assertEquals(2, points.size)
        assertPoint(1.0, 1.0, points[0])
        assertPoint(2.0, 2.0, points[1])
    }
}
