package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.UserSettings
import com.nuelto.etappli.data.model.isStay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStopTest {

    private val start = LocalDate.of(2027, 6, 10)
    private val home = LatLng(47.0502, 8.3093)
    private val settings = UserSettings(homeName = "Luzern", homeLocation = home)

    @Test
    fun `no home set means a plan starts wherever you add first`() {
        assertTrue(HomeStop.forNewPlan("t1", UserSettings(), start).isEmpty())
        // A name on its own is not somewhere to start from.
        assertTrue(HomeStop.forNewPlan("t1", UserSettings(homeName = "Luzern"), start).isEmpty())
    }

    @Test
    fun `a new plan sets out from home and comes back to it`() {
        val stops = HomeStop.forNewPlan("t1", settings, start)
        assertEquals(listOf(0, 1), stops.map { it.orderIndex })
        stops.forEach { stop ->
            assertEquals("t1", stop.tripId)
            assertEquals("Luzern", stop.name)
            assertEquals(home, stop.location)
            assertEquals(start, stop.arrivalDate)
            assertEquals(StopKind.HOME, stop.kind)
        }
    }

    @Test
    fun `home is somewhere you leave, not somewhere you stay`() {
        HomeStop.forNewPlan("t1", settings, start).forEach { stop ->
            assertEquals(0, stop.nights)
            assertEquals(0.0, stop.campingCostTotal, 1e-9)
            assertFalse(stop.kind.isStay)
        }
    }

    @Test
    fun `an unnamed home is called Home`() {
        assertEquals("Home", HomeStop.DEFAULT_NAME)
        assertEquals("Home", HomeStop.name(UserSettings()))
        // Blank is not a name either.
        assertEquals("Home", HomeStop.name(UserSettings(homeName = "  ")))
        assertEquals("Luzern", HomeStop.name(settings))
        assertEquals(
            listOf("Home", "Home"),
            HomeStop.forNewPlan("t1", UserSettings(homeLocation = home), start).map { it.name },
        )
    }

    private val out = Stop(id = "out", kind = StopKind.HOME, orderIndex = 0)
    private val a = Stop(id = "a", orderIndex = 1)
    private val back = Stop(id = "back", kind = StopKind.HOME, orderIndex = 2)

    @Test
    fun `the first stop is the departure, but only when it is a home`() {
        assertNull(HomeStop.departure(emptyList()))
        // By order, not by list position.
        assertEquals("out", HomeStop.departure(listOf(a, out))!!.id)
        assertNull(HomeStop.departure(listOf(a, out.copy(orderIndex = 5))))
        assertNull(HomeStop.departure(listOf(a)))
    }

    @Test
    fun `the last stop is the drive home while it is a planned home with something in front`() {
        assertNull(HomeStop.returning(emptyList()))
        assertEquals("back", HomeStop.returning(listOf(back, out, a))!!.id)
        // Start home deleted: the plan still ends with the drive home.
        assertEquals("back", HomeStop.returning(listOf(a, back))!!.id)
        // A fresh plan: nothing between leaving and coming back yet.
        assertEquals("back", HomeStop.returning(listOf(out, back.copy(orderIndex = 1)))!!.id)
        // A lone home is where the plan starts, so stops added to it follow it.
        assertNull(HomeStop.returning(listOf(out)))
        // Ends somewhere else.
        assertNull(HomeStop.returning(listOf(out, a)))
        // Already home.
        assertNull(HomeStop.returning(listOf(out, a, back.copy(state = StopState.DONE))))
    }
}
