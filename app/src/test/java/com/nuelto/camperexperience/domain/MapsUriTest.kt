package com.nuelto.camperexperience.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapsUriTest {

    @Test
    fun `a place id opens the real place`() {
        assertEquals(
            "https://www.google.com/maps/place/?q=place_id:ChIJCyinolJ-hUcRIQiP1AQWa4s",
            MapsUri.place("ChIJCyinolJ-hUcRIQiP1AQWa4s"),
        )
    }

    @Test
    fun `an odd place id is escaped, a missing one gives nothing`() {
        assertEquals(
            "https://www.google.com/maps/place/?q=place_id:a%2Fb%20c",
            MapsUri.place("a/b c"),
        )
        assertNull(MapsUri.place(null))
        assertNull(MapsUri.place(""))
        assertNull(MapsUri.place("   "))
    }

    @Test
    fun `a bare coordinate is a search link`() {
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=46.5939%2C7.9078",
            MapsUri.point(46.5939, 7.9078),
        )
    }

    @Test
    fun `sharing prefers the place, falls back to the point, else nothing`() {
        assertEquals(
            "https://www.google.com/maps/place/?q=place_id:ChIJ1",
            MapsUri.share("ChIJ1", 46.5, 7.9),
        )
        assertEquals(
            "https://www.google.com/maps/search/?api=1&query=46.5%2C7.9",
            MapsUri.share(null, 46.5, 7.9),
        )
        assertNull(MapsUri.share(null, null, null))
        assertNull(MapsUri.share("", 46.5, null))
        assertNull(MapsUri.share(null, null, 7.9))
    }
}
