package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.StopKind
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.data.model.isStay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class HomeStopTest {

    private val start = LocalDate.of(2027, 6, 10)
    private val home = LatLng(47.0502, 8.3093)

    @Test
    fun `no home set means a plan starts wherever you add first`() {
        assertNull(HomeStop.forNewPlan("t1", UserSettings(), start))
        // A name on its own is not somewhere to start from.
        assertNull(HomeStop.forNewPlan("t1", UserSettings(homeName = "Luzern"), start))
    }

    @Test
    fun `the home stop is the plan's first row, on its start date`() {
        val stop = HomeStop.forNewPlan(
            "t1",
            UserSettings(homeName = "Luzern", homeLocation = home),
            start,
        )!!
        assertEquals("t1", stop.tripId)
        assertEquals("Luzern", stop.name)
        assertEquals(home, stop.location)
        assertEquals(start, stop.arrivalDate)
        assertEquals(0, stop.orderIndex)
        assertEquals(StopKind.HOME, stop.kind)
    }

    @Test
    fun `home is somewhere you leave, not somewhere you stay`() {
        val stop = HomeStop.forNewPlan("t1", UserSettings(homeLocation = home), start)!!
        assertEquals(0, stop.nights)
        assertEquals(0.0, stop.campingCostTotal, 1e-9)
        assertFalse(stop.kind.isStay)
    }

    @Test
    fun `an unnamed home is called Home`() {
        val stop = HomeStop.forNewPlan("t1", UserSettings(homeLocation = home), start)!!
        assertEquals(HomeStop.DEFAULT_NAME, stop.name)
        assertEquals("Home", HomeStop.DEFAULT_NAME)
        // Blank is not a name either.
        assertEquals(
            "Home",
            HomeStop.forNewPlan("t1", UserSettings(homeName = "  ", homeLocation = home), start)!!.name,
        )
    }
}
