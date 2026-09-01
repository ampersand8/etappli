package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleRoutesTest {

    private fun points(n: Int) = List(n) { LatLng(46.0 + it, 7.0 + it) }

    private fun waypoint(lat: Double, lng: Double) =
        """{"location":{"latLng":{"latitude":$lat,"longitude":$lng}}}"""

    // --- windows ------------------------------------------------------------

    @Test
    fun `fewer than two points is not a drive`() {
        assertEquals(emptyList<List<LatLng>>(), GoogleRoutes.windows(emptyList()))
        assertEquals(emptyList<List<LatLng>>(), GoogleRoutes.windows(points(1)))
    }

    @Test
    fun `two points are one request`() {
        assertEquals(listOf(points(2)), GoogleRoutes.windows(points(2)))
    }

    @Test
    fun `a full window still costs one request`() {
        val all = points(GoogleRoutes.MAX_POINTS_PER_REQUEST)
        assertEquals(listOf(all), GoogleRoutes.windows(all))
    }

    @Test
    fun `one point too many splits, and the second window starts where the first ended`() {
        val all = points(13)
        val windows = GoogleRoutes.windows(all)
        assertEquals(2, windows.size)
        assertEquals(listOf(12, 2), windows.map { it.size })
        assertEquals(all.take(12), windows[0])
        assertEquals(all.subList(11, 13), windows[1])
        // The overlap is the whole contract: the legs join up only if the point is shared.
        assertEquals(windows[0].last(), windows[1].first())
        assertEquals(all[11], windows[1].first())
    }

    @Test
    fun `two full windows overlap by exactly one point`() {
        val all = points(23)
        val windows = GoogleRoutes.windows(all)
        assertEquals(listOf(12, 12), windows.map { it.size })
        assertEquals(all[11], windows[1].first())
        assertEquals(all[22], windows[1].last())
    }

    @Test
    fun `however a trip is split, the windows cover every leg exactly once`() {
        for (n in 2..40) {
            val windows = GoogleRoutes.windows(points(n))
            assertEquals("$n points", n - 1, windows.sumOf { it.size - 1 })
            assertTrue("$n points", windows.all { it.size in 2..GoogleRoutes.MAX_POINTS_PER_REQUEST })
            windows.zipWithNext { a, b -> assertEquals("$n points", a.last(), b.first()) }
        }
    }

    // --- request ------------------------------------------------------------

    @Test
    fun `two points need no intermediates at all`() {
        val body = GoogleRoutes.computeRoutesBody(listOf(LatLng(46.0, 7.0), LatLng(47.5, 8.25)))
        assertEquals(
            """{"origin":${waypoint(46.0, 7.0)},"destination":${waypoint(47.5, 8.25)},""" +
                """"travelMode":"DRIVE","routingPreference":"TRAFFIC_UNAWARE",""" +
                """"polylineQuality":"HIGH_QUALITY"}""",
            body,
        )
        assertFalse(body.contains("intermediates"))
    }

    @Test
    fun `every middle point becomes an intermediate, in order`() {
        assertEquals(
            """{"origin":${waypoint(46.0, 7.0)},"destination":${waypoint(49.0, 10.0)},""" +
                """"intermediates":[${waypoint(47.0, 8.0)},${waypoint(48.0, 9.0)}],""" +
                """"travelMode":"DRIVE","routingPreference":"TRAFFIC_UNAWARE",""" +
                """"polylineQuality":"HIGH_QUALITY"}""",
            GoogleRoutes.computeRoutesBody(points(4)),
        )
        assertTrue(
            GoogleRoutes.computeRoutesBody(points(3))
                .contains(""","intermediates":[${waypoint(47.0, 8.0)}],"travelMode"""),
        )
    }

    @Test
    fun `the request pins driving, no live traffic and full geometry`() {
        val body = GoogleRoutes.computeRoutesBody(points(3))
        assertTrue(body.contains(""""travelMode":"DRIVE""""))
        // Live traffic is meaningless weeks out and jumps to the Pro SKU.
        assertTrue(body.contains(""""routingPreference":"TRAFFIC_UNAWARE""""))
        assertTrue(body.contains(""""polylineQuality":"HIGH_QUALITY""""))
    }

    @Test
    fun `the field mask asks for the per-leg numbers only`() {
        assertEquals(
            "routes.legs.distanceMeters,routes.legs.duration,routes.legs.polyline.encodedPolyline",
            GoogleRoutes.FIELD_MASK,
        )
        assertFalse(GoogleRoutes.FIELD_MASK.contains("*"))
        assertEquals(
            "https://routes.googleapis.com/directions/v2:computeRoutes",
            GoogleRoutes.COMPUTE_ROUTES_URL,
        )
    }

    // --- response -----------------------------------------------------------

    private fun response(vararg legs: String) =
        """{"routes":[{"legs":[${legs.joinToString(",")}]}]}"""

    @Test
    fun `a routed window comes back one leg per pair of stops`() {
        val legs = GoogleRoutes.parseLegs(
            response(
                """{"distanceMeters":12345,"duration":"1234s","polyline":{"encodedPolyline":"abc"}}""",
                """{"distanceMeters":6789,"duration":"678.6s","polyline":{"encodedPolyline":"def"}}""",
            ),
        )
        assertEquals(2, legs.size)
        assertEquals(RoutedLeg("abc", 12345, 1234), legs[0])
        assertEquals(RoutedLeg("def", 6789, 679), legs[1])
    }

    @Test
    fun `proto3 drops zeroes, so an empty leg is a zero leg, not a missing one`() {
        assertEquals(listOf(RoutedLeg("", 0, 0)), GoogleRoutes.parseLegs(response("{}")))
    }

    @Test
    fun `unusable fields fall back rather than dropping the leg`() {
        assertEquals(
            listOf(RoutedLeg("", 0, 0)),
            GoogleRoutes.parseLegs(
                response(
                    """{"distanceMeters":"12345","duration":"nope","polyline":{"encodedPolyline":""}}""",
                ),
            ),
        )
        assertEquals(
            listOf(RoutedLeg("", 0, 0)),
            GoogleRoutes.parseLegs(response("""{"duration":12,"polyline":"abc"}""")),
        )
    }

    @Test
    fun `one malformed leg discards the whole window rather than shifting the rest`() {
        // Leg i is the drive to stop i+1; a shorter list would silently misalign them all.
        assertEquals(
            emptyList<RoutedLeg>(),
            GoogleRoutes.parseLegs(
                response(
                    """{"distanceMeters":1,"duration":"1s"}""",
                    "42",
                    """{"distanceMeters":2,"duration":"2s"}""",
                ),
            ),
        )
    }

    @Test
    fun `nothing routable yields no legs`() {
        assertTrue(GoogleRoutes.parseLegs("""{"routes":[]}""").isEmpty())
        assertTrue(GoogleRoutes.parseLegs("""{"routes":[{}]}""").isEmpty())
        assertTrue(GoogleRoutes.parseLegs("""{"routes":[{"legs":{}}]}""").isEmpty())
        assertTrue(GoogleRoutes.parseLegs("""{"routes":{"a":1}}""").isEmpty())
        assertTrue(GoogleRoutes.parseLegs("{}").isEmpty())
        assertTrue(GoogleRoutes.parseLegs("[]").isEmpty())
        assertTrue(GoogleRoutes.parseLegs("<html>404</html>").isEmpty())
        assertTrue(GoogleRoutes.parseLegs("").isEmpty())
    }

    // --- durations ----------------------------------------------------------

    @Test
    fun `protobuf durations are seconds, rounded`() {
        assertEquals(120, GoogleRoutes.parseDuration("120s"))
        assertEquals(4, GoogleRoutes.parseDuration("3.5s"))
        assertEquals(3, GoogleRoutes.parseDuration("3.4s"))
        assertEquals(0, GoogleRoutes.parseDuration("0s"))
    }

    @Test
    fun `anything else is no duration`() {
        assertNull(GoogleRoutes.parseDuration("soon"))
        assertNull(GoogleRoutes.parseDuration("s"))
        assertNull(GoogleRoutes.parseDuration(""))
    }
}
