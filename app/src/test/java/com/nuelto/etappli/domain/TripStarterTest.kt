package com.nuelto.etappli.domain

import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.Expense
import com.nuelto.etappli.data.model.ExpenseType
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.Trip
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

class TripStarterTest {

    private val repo = InMemoryTripRepository(seed = false)
    private val settings = UserSettings(campsitePerNight = 45.0, stellplatzPerNight = 15.0)
    private val planStart = LocalDate.of(2027, 6, 10)

    private suspend fun seedPlan(): String {
        val planId = repo.upsertTrip(
            Trip(id = "plan", name = "Jura", startDate = planStart, status = TripStatus.PLANNED),
        )
        repo.upsertStop(
            Stop(
                id = "s1", tripId = planId, name = "Luzern", arrivalDate = planStart,
                nights = 2, orderIndex = 0, costKnown = false,
            ),
        )
        repo.upsertStop(
            Stop(
                id = "s2", tripId = planId, name = "Locarno", arrivalDate = planStart.plusDays(2),
                nights = 3, campingCostTotal = 186.0, orderIndex = 1,
            ),
        )
        repo.upsertExpense(
            Expense(
                id = "e1", tripId = planId, type = ExpenseType.ROAD_TAX, amount = 40.0,
                date = planStart, label = "CH Vignette", isEstimate = true, stopId = "s1",
            ),
        )
        repo.upsertExpense(
            Expense(id = "e2", tripId = planId, type = ExpenseType.OTHER, amount = 12.0, date = planStart),
        )
        return planId
    }

    @Test
    fun `keep-template start copies the plan into a fresh active trip`() = runTest {
        val planId = seedPlan()
        val actualStart = planStart.plusDays(4)
        val newId = TripStarter.start(repo, planId, actualStart, keepPlanAsTemplate = true, settings = settings)

        assertNotEquals(planId, newId)
        val started = repo.trip(newId).first()!!
        assertEquals(TripStatus.ACTIVE, started.status)
        assertEquals(actualStart, started.startDate)
        assertNull(started.endDate)
        // Snapshot: camping 186 known + 2×45 estimated + 40 vignette + 12 other.
        assertEquals(186.0 + 90.0 + 40.0 + 12.0, started.plannedCost!!, 1e-9)
        assertEquals(5, started.plannedNights)

        val copiedStops = repo.stops(newId).first()
        assertEquals(listOf("Luzern", "Locarno"), copiedStops.map { it.name })
        assertTrue(copiedStops.none { it.id == "s1" || it.id == "s2" })
        assertEquals(actualStart, copiedStops[0].arrivalDate)
        assertEquals(actualStart.plusDays(2), copiedStops[1].arrivalDate)
        assertEquals(listOf(false, true), copiedStops.map { it.costKnown })

        // Only estimate expenses travel, with stop links dropped.
        val copiedExpenses = repo.expenses(newId).first()
        assertEquals(listOf("CH Vignette"), copiedExpenses.map { it.label })
        assertNull(copiedExpenses.single().stopId)
        assertEquals(actualStart, copiedExpenses.single().date)

        // The plan survives untouched.
        val plan = repo.trip(planId).first()!!
        assertEquals(TripStatus.PLANNED, plan.status)
        assertEquals(planStart, plan.startDate)
        assertEquals(2, repo.stops(planId).first().size)
        assertEquals(2, repo.expenses(planId).first().size)
    }

    @Test
    fun `in-place start converts the plan and shifts its dates`() = runTest {
        val planId = seedPlan()
        val actualStart = planStart.minusDays(1)
        val id = TripStarter.start(repo, planId, actualStart, keepPlanAsTemplate = false, settings = settings)

        assertEquals(planId, id)
        val started = repo.trip(planId).first()!!
        assertEquals(TripStatus.ACTIVE, started.status)
        assertEquals(actualStart, started.startDate)
        assertEquals(186.0 + 90.0 + 40.0 + 12.0, started.plannedCost!!, 1e-9)
        assertEquals(5, started.plannedNights)
        assertEquals(
            listOf(actualStart, actualStart.plusDays(2)),
            repo.stops(planId).first().map { it.arrivalDate },
        )
    }

    @Test
    fun `in-place start on the same date rewrites no stops`() = runTest {
        val planId = seedPlan()
        TripStarter.start(repo, planId, planStart, keepPlanAsTemplate = false, settings = settings)
        assertEquals(
            listOf(planStart, planStart.plusDays(2)),
            repo.stops(planId).first().map { it.arrivalDate },
        )
        assertEquals(TripStatus.ACTIVE, repo.trip(planId).first()!!.status)
    }

    @Test
    fun `plan again copies a done trip into a fresh plan without skipped stops`() = runTest {
        val doneId = repo.upsertTrip(
            Trip(
                id = "done", name = "Provence", startDate = LocalDate.of(2025, 9, 12),
                endDate = LocalDate.of(2025, 9, 21), status = TripStatus.DONE,
                plannedCost = 500.0, plannedNights = 9,
            ),
        )
        repo.upsertStop(
            Stop(
                id = "d1", tripId = doneId, name = "Durance", arrivalDate = LocalDate.of(2025, 9, 12),
                nights = 3, campingCostTotal = 84.0, orderIndex = 0, state = StopState.DONE,
            ),
        )
        repo.upsertStop(
            Stop(
                id = "d2", tripId = doneId, name = "Skipped gorge", arrivalDate = LocalDate.of(2025, 9, 15),
                nights = 1, orderIndex = 1, state = StopState.SKIPPED, kind = StopKind.FREE_CAMP,
            ),
        )
        repo.upsertExpense(
            Expense(id = "fuel", tripId = doneId, type = ExpenseType.FUEL, amount = 145.30),
        )
        repo.upsertExpense(
            Expense(
                id = "vignette", tripId = doneId, type = ExpenseType.ROAD_TAX, amount = 40.0,
                date = LocalDate.of(2025, 9, 12), label = "Vignette", isEstimate = true, stopId = "d1",
            ),
        )

        val newStart = LocalDate.of(2026, 9, 12)
        val newId = TripStarter.planAgain(repo, doneId, newStart)

        assertNotEquals(doneId, newId)
        val plan = repo.trip(newId).first()!!
        assertEquals(TripStatus.PLANNED, plan.status)
        assertEquals(newStart, plan.startDate)
        assertNull(plan.endDate)
        assertNull(plan.plannedCost)
        assertNull(plan.plannedNights)

        val stops = repo.stops(newId).first()
        assertEquals(listOf("Durance"), stops.map { it.name })
        assertEquals(StopState.PLANNED, stops.single().state)
        assertEquals(newStart, stops.single().arrivalDate)
        // Estimate expenses travel (dates shifted, stop links dropped); actuals stay behind.
        val copied = repo.expenses(newId).first().single()
        assertEquals("Vignette", copied.label)
        assertEquals(newStart, copied.date)
        assertNull(copied.stopId)
        assertEquals(2, repo.expenses(doneId).first().size)
    }
}
