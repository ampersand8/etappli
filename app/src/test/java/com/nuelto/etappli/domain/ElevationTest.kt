package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopElevation
import org.junit.Assert.assertEquals
import org.junit.Test

class ElevationTest {

    /** Five ~7.6 m steps, then three ~38 km legs: vertices crowded at one end. */
    private val crowded = listOf(
        LatLng(47.0, 8.0),
        LatLng(47.0, 8.0001),
        LatLng(47.0, 8.0002),
        LatLng(47.0, 8.0003),
        LatLng(47.0, 8.0004),
        LatLng(47.0, 8.5),
        LatLng(47.0, 9.0),
        LatLng(47.0, 9.5),
    )

    @Test
    fun `url lists the latitudes, then the longitudes, in point order`() {
        assertEquals(
            "https://api.open-meteo.com/v1/elevation?latitude=46.5,47.25&longitude=8.0,9.5",
            Elevation.url(listOf(LatLng(46.5, 8.0), LatLng(47.25, 9.5))),
        )
    }

    @Test
    fun `parse reads the elevation array, whole numbers included`() {
        assertEquals(
            listOf(1234.5, 1200.0, 980.0),
            Elevation.parse("""{"latitude":[46.5],"elevation":[1234.5,1200,980.0]}"""),
        )
    }

    @Test
    fun `junk, an empty object and a non-array elevation all yield no heights`() {
        assertEquals(emptyList<Double>(), Elevation.parse("<html>502 Bad Gateway</html>"))
        assertEquals(emptyList<Double>(), Elevation.parse("{}"))
        assertEquals(emptyList<Double>(), Elevation.parse("""{"elevation":1234.5}"""))
    }

    @Test
    fun `one unreadable height drops the whole array, never just that entry`() {
        assertEquals(emptyList<Double>(), Elevation.parse("""{"elevation":[100.0,"n/a",300.0]}"""))
        assertEquals(emptyList<Double>(), Elevation.parse("""{"elevation":[100.0,null]}"""))
    }

    @Test
    fun `a non-positive budget samples nothing`() {
        assertEquals(emptyList<LatLng>(), Elevation.sample(crowded, 0))
        assertEquals(emptyList<LatLng>(), Elevation.sample(crowded, -1))
    }

    @Test
    fun `a path within the budget is used as it stands`() {
        val three = crowded.take(3)
        assertEquals(three, Elevation.sample(three, 3))
        assertEquals(three, Elevation.sample(three, 4))
        assertEquals(three, Elevation.sample(three))
    }

    @Test
    fun `a budget of one keeps the start`() {
        assertEquals(listOf(crowded[0]), Elevation.sample(crowded, 1))
    }

    @Test
    fun `a path with no length falls back to the first points`() {
        val pile = List(4) { LatLng(47.0, 8.0) }
        assertEquals(pile.take(2), Elevation.sample(pile, 2))
    }

    @Test
    fun `sampling spreads by distance, not by index`() {
        val result = Elevation.sample(crowded, 5)
        assertEquals(5, result.size)
        // Total 113.75 km, so targets 0 / 28.4 / 56.9 / 85.3 / 113.75: the crowded cluster
        // collapses into one sample instead of eating four of the five slots, and the last
        // target lands exactly on the final vertex.
        assertEquals(
            listOf(crowded[0], crowded[4], crowded[5], crowded[6], crowded[7]),
            result,
        )
        assertEquals(crowded.first(), result.first())
        assertEquals(crowded.last(), result.last())
    }

    /** [count] points along a meridian, [spacingKm] apart. */
    private fun line(count: Int, spacingKm: Double) =
        List(count) { LatLng(47.0 + it * spacingKm / 111.195, 8.0) }

    /** Far enough apart that the grade clamp cannot reach the hysteresis cases. */
    private fun far(count: Int) = line(count, 10.0)

    @Test
    fun `no heights is no climb`() {
        assertEquals(Climb(0.0, 0.0), Elevation.profile(emptyList(), emptyList()))
    }

    @Test
    fun `a steady climb sums its steps`() {
        assertEquals(
            Climb(120.0, 0.0),
            Elevation.profile(far(3), listOf(500.0, 560.0, 620.0), 10.0),
        )
    }

    @Test
    fun `up then down is reported separately`() {
        assertEquals(
            Climb(400.0, 250.0),
            Elevation.profile(far(3), listOf(600.0, 1000.0, 750.0), 10.0),
        )
    }

    @Test
    fun `a wobble under minRise counts for nothing`() {
        assertEquals(
            Climb(0.0, 0.0),
            Elevation.profile(far(5), listOf(600.0, 609.9, 600.0, 609.9, 590.1), 10.0),
        )
    }

    @Test
    fun `a move of exactly minRise counts`() {
        assertEquals(Climb(10.0, 0.0), Elevation.profile(far(2), listOf(600.0, 610.0), 10.0))
        assertEquals(Climb(0.0, 10.0), Elevation.profile(far(2), listOf(600.0, 590.0), 10.0))
    }

    @Test
    fun `steps are measured from the anchor, not from the last height`() {
        assertEquals(
            Climb(11.0, 0.0),
            Elevation.profile(far(3), listOf(600.0, 605.0, 611.0), 10.0),
        )
    }

    @Test
    fun `the default filter is MIN_RISE_M`() {
        // 49 m of undulation is below the default threshold; 60 m is over it.
        assertEquals(Climb(0.0, 0.0), Elevation.profile(far(3), listOf(600.0, 649.0, 600.0)))
        assertEquals(Climb(60.0, 0.0), Elevation.profile(far(2), listOf(600.0, 660.0)))
        assertEquals(50.0, Elevation.MIN_RISE_M, 1e-9)
    }

    @Test
    fun `a tunnel's phantom mountain is clamped to what a road could climb`() {
        // 500 m apart, so MAX_ROAD_GRADE allows 40 m a step. The DEM reports the ridge
        // over the tunnel: without the clamp this is 1000 m of ascent and 1000 of descent.
        val climb = Elevation.profile(line(3, 0.5), listOf(600.0, 1600.0, 600.0), 10.0)
        assertEquals(40.0, climb.ascentMeters, 0.01)
        assertEquals(40.0, climb.descentMeters, 0.01)
    }

    @Test
    fun `a climb a road could actually make is left alone`() {
        // 500 m apart and 30 m up is 6%, inside MAX_ROAD_GRADE.
        assertEquals(
            30.0,
            Elevation.profile(line(2, 0.5), listOf(600.0, 630.0), 10.0).ascentMeters,
            0.01,
        )
        assertEquals(0.08, Elevation.MAX_ROAD_GRADE, 1e-9)
    }

    @Test
    fun `the clamp carries forward, so a long tunnel does not spike at its far end`() {
        // Four 500 m steps at 40 m each: the road creeps up, it does not jump and return.
        val climb = Elevation.profile(
            line(5, 0.5),
            listOf(600.0, 2000.0, 2000.0, 2000.0, 600.0),
            10.0,
        )
        // Three 40 m steps up; coming back down, only the last step is a real drop.
        assertEquals(120.0, climb.ascentMeters, 0.01)
        assertEquals(40.0, climb.descentMeters, 0.01)
    }

    @Test
    fun `a stop's height counts only where the stop actually sits`() {
        val at = LatLng(46.19302, 7.82714)
        val stop = Stop(id = "s", location = at, elevation = StopElevation(at, 1469))
        assertEquals(1469, Elevation.ofStop(stop))
        assertEquals(null, Elevation.ofStop(stop.copy(elevation = null)))
        // Pin moved since it was measured.
        assertEquals(null, Elevation.ofStop(stop.copy(location = LatLng(46.5, 7.9))))
        assertEquals(null, Elevation.ofStop(stop.copy(location = null)))
    }
}
