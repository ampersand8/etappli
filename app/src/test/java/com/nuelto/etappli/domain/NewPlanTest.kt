package com.nuelto.etappli.domain

import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.data.model.UserSettings
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewPlanTest {

    private val repo = InMemoryTripRepository(seed = false)
    private val today = LocalDate.of(2027, 6, 10)

    @Test
    fun `a new plan is unnamed, planned, dated today and empty`() = runTest {
        val id = NewPlan.create(repo, UserSettings(), today)
        val trip = repo.trip(id).first()!!
        assertEquals("", trip.name)
        assertEquals(TripStatus.PLANNED, trip.status)
        assertEquals(today, trip.startDate)
        assertNull(trip.endDate)
        assertTrue(repo.stops(id).first().isEmpty())
        assertEquals(LocalDate.now(), repo.trip(NewPlan.create(repo, UserSettings())).first()!!.startDate)
    }

    @Test
    fun `with a home set it sets out from home and comes back`() = runTest {
        val settings = UserSettings(homeName = "Luzern", homeLocation = LatLng(47.0502, 8.3093))
        val id = NewPlan.create(repo, settings, today)
        val stops = repo.stops(id).first()
        assertEquals(listOf(0, 1), stops.map { it.orderIndex })
        assertEquals(setOf(StopKind.HOME), stops.map { it.kind }.toSet())
        assertEquals(setOf(today), stops.map { it.arrivalDate }.toSet())
        assertEquals(setOf(id), stops.map { it.tripId }.toSet())
    }

    @Test
    fun `every call is another plan`() = runTest {
        assertNotEquals(NewPlan.create(repo, UserSettings(), today), NewPlan.create(repo, UserSettings(), today))
        assertEquals(2, repo.trips().first().size)
    }
}
