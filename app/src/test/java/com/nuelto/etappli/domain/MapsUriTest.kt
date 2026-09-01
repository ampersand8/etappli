package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapsUriTest {

    @Test
    fun `a coordinate plus a place id opens exactly that place`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=46.5606%2C8.3376" +
                "&query_place_id=ChIJCyinolJ-hUcRIQiP1AQWa4s",
            MapsUri.share("Grimsel Pass", LatLng(46.5606, 8.3376), "ChIJCyinolJ-hUcRIQiP1AQWa4s"),
        )
    }

    @Test
    fun `a coordinate alone is still a valid link`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=46.5%2C7.9",
            MapsUri.share("", LatLng(46.5, 7.9), null),
        )
    }

    @Test
    fun `with the coordinate expired the name carries the query`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=Camping%20Delta&query_place_id=ChIJ1",
            MapsUri.share("Camping Delta", null, "ChIJ1"),
        )
    }

    @Test
    fun `odd characters are escaped`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=A%20%26%20B&query_place_id=a%2Fb",
            MapsUri.share("A & B", null, "a/b"),
        )
    }

    @Test
    fun `nothing to point at gives no link`() {
        assertNull(MapsUri.share("", null, null))
        assertNull(MapsUri.share("   ", null, "  "))
        assertNull(MapsUri.share("", null, "ChIJ1"))
    }
}
