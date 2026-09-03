package com.nuelto.etappli.ui.components

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.data.model.TravelMode
import com.nuelto.etappli.testutil.TestCamperApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class TravelIconsTest {

    @Test
    fun `every way of travelling has an icon of its own`() {
        val icons = TravelMode.entries.associateWith { modeIcon(it) }
        assertEquals(TravelMode.entries.size, icons.values.distinct().size)
        assertEquals(CableCar, icons[TravelMode.CABLE_CAR])
        assertNotEquals(icons[TravelMode.CAR], icons[TravelMode.CABLE_CAR])
    }

    @Test
    fun `the hand-drawn cable car sits on the same canvas as the set's icons`() {
        assertEquals("CableCar", CableCar.name)
        assertEquals(24f, CableCar.viewportWidth)
        assertEquals(24f, CableCar.viewportHeight)
        assertEquals(modeIcon(TravelMode.CAR).viewportWidth, CableCar.viewportWidth)
    }
}
