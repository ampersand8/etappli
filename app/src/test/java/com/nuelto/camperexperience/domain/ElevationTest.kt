package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
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

    @Test
    fun `no heights is no climb`() {
        assertEquals(Climb(0.0, 0.0), Elevation.profile(emptyList()))
    }

    @Test
    fun `a steady climb sums its steps`() {
        assertEquals(Climb(120.0, 0.0), Elevation.profile(listOf(500.0, 560.0, 620.0), 10.0))
    }

    @Test
    fun `up then down is reported separately`() {
        assertEquals(Climb(400.0, 250.0), Elevation.profile(listOf(600.0, 1000.0, 750.0), 10.0))
    }

    @Test
    fun `a wobble under minRise counts for nothing`() {
        assertEquals(
            Climb(0.0, 0.0),
            Elevation.profile(listOf(600.0, 609.9, 600.0, 609.9, 590.1), 10.0),
        )
    }

    @Test
    fun `a move of exactly minRise counts`() {
        assertEquals(Climb(10.0, 0.0), Elevation.profile(listOf(600.0, 610.0), 10.0))
        assertEquals(Climb(0.0, 10.0), Elevation.profile(listOf(600.0, 590.0), 10.0))
    }

    @Test
    fun `steps are measured from the anchor, not from the last height`() {
        assertEquals(Climb(11.0, 0.0), Elevation.profile(listOf(600.0, 605.0, 611.0), 10.0))
    }

    @Test
    fun `the default filter is MIN_RISE_M`() {
        assertEquals(Climb(0.0, 0.0), Elevation.profile(listOf(600.0, 609.99, 600.0)))
        assertEquals(Climb(50.0, 0.0), Elevation.profile(listOf(600.0, 650.0)))
    }
}
