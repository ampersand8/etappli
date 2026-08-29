package com.nuelto.camperexperience.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

class TripColorTest {

    @Test
    fun `cycles through the palette`() {
        assertEquals(tripColorPalette[0], tripColor(0))
        assertEquals(tripColorPalette[1], tripColor(1))
        assertEquals(tripColorPalette[0], tripColor(tripColorPalette.size))
    }

    @Test
    fun `negative hash codes still map into the palette`() {
        assertEquals(tripColorPalette[tripColorPalette.size - 1], tripColor(-1))
    }
}
