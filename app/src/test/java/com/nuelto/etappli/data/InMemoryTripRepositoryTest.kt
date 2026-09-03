package com.nuelto.etappli.data

import com.nuelto.etappli.data.model.Expense
import com.nuelto.etappli.data.model.ExpenseType
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopRegion
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryTripRepositoryTest {

    private val repo = InMemoryTripRepository(seed = false)

    private suspend fun addTrip(id: String = "", name: String = "Trip"): String =
        repo.upsertTrip(Trip(id = id, name = name, startDate = LocalDate.of(2026, 6, 1)))

    @Test
    fun `upsert generates an id for a blank one and keeps a given one`() = runTest {
        val generated = addTrip()
        assertTrue(generated.isNotBlank())
        assertEquals("fixed", repo.upsertTrip(Trip(id = "fixed", name = "Kept")))
    }

    @Test
    fun `upsert with existing id replaces instead of duplicating`() = runTest {
        val id = addTrip(name = "Old")
        repo.upsertTrip(repo.trip(id).first()!!.copy(name = "New"))
        assertEquals(listOf("New"), repo.trips().first().map { it.name })
    }

    @Test
    fun `trips are sorted by start date descending`() = runTest {
        repo.upsertTrip(Trip(id = "older", name = "Older", startDate = LocalDate.of(2025, 1, 1)))
        repo.upsertTrip(Trip(id = "newer", name = "Newer", startDate = LocalDate.of(2026, 1, 1)))
        assertEquals(listOf("newer", "older"), repo.trips().first().map { it.id })
    }

    @Test
    fun `trip flow finds by id and emits null for unknown`() = runTest {
        val id = addTrip(name = "Found")
        assertEquals("Found", repo.trip(id).first()!!.name)
        assertNull(repo.trip("nope").first())
    }

    @Test
    fun `stops are filtered by trip and sorted by order index`() = runTest {
        val id = addTrip()
        repo.upsertStop(Stop(id = "b", tripId = id, name = "Second", orderIndex = 1))
        repo.upsertStop(Stop(id = "a", tripId = id, name = "First", orderIndex = 0))
        repo.upsertStop(Stop(id = "x", tripId = "other-trip", name = "Elsewhere"))
        assertEquals(listOf("First", "Second"), repo.stops(id).first().map { it.name })
    }

    @Test
    fun `expenses are filtered by trip and sorted by date`() = runTest {
        val id = addTrip()
        repo.upsertExpense(Expense(id = "late", tripId = id, amount = 2.0, date = LocalDate.of(2026, 6, 5)))
        repo.upsertExpense(Expense(id = "early", tripId = id, amount = 1.0, date = LocalDate.of(2026, 6, 2)))
        repo.upsertExpense(Expense(id = "other", tripId = "other-trip", amount = 9.0))
        assertEquals(listOf("early", "late"), repo.expenses(id).first().map { it.id })
    }

    @Test
    fun `upsert stop and expense generate ids and replace on same id`() = runTest {
        val id = addTrip()
        repo.upsertStop(Stop(tripId = id, name = "Generated"))
        val stopId = repo.stops(id).first().single().id
        assertTrue(stopId.isNotBlank())
        repo.upsertStop(Stop(id = stopId, tripId = id, name = "Replaced"))
        assertEquals(listOf("Replaced"), repo.stops(id).first().map { it.name })

        repo.upsertExpense(Expense(tripId = id, amount = 5.0))
        val expenseId = repo.expenses(id).first().single().id
        assertTrue(expenseId.isNotBlank())
        repo.upsertExpense(Expense(id = expenseId, tripId = id, amount = 6.0))
        assertEquals(6.0, repo.expenses(id).first().single().amount, 1e-9)
    }

    @Test
    fun `totals are recomputed on stop and expense mutations`() = runTest {
        val id = addTrip()
        repo.upsertStop(Stop(id = "s1", tripId = id, nights = 3, campingCostTotal = 90.0))
        repo.upsertExpense(Expense(id = "e1", tripId = id, type = ExpenseType.FUEL, amount = 50.0))
        assertEquals(140.0, repo.trip(id).first()!!.totalCost, 1e-9)
        assertEquals(3, repo.trip(id).first()!!.nights)

        repo.deleteExpense(id, "e1")
        assertEquals(90.0, repo.trip(id).first()!!.totalCost, 1e-9)

        repo.deleteStop(id, "s1")
        assertEquals(0.0, repo.trip(id).first()!!.totalCost, 1e-9)
        assertEquals(0, repo.trip(id).first()!!.nights)
    }

    @Test
    fun `upserting a trip recomputes totals from its stops and expenses`() = runTest {
        val id = addTrip()
        repo.upsertStop(Stop(id = "s1", tripId = id, nights = 2, campingCostTotal = 50.0))
        // A fresh Trip object with stale zero totals must come back recomputed.
        repo.upsertTrip(Trip(id = id, name = "Renamed", startDate = LocalDate.of(2026, 6, 1)))
        val trip = repo.trip(id).first()!!
        assertEquals(50.0, trip.totalCost, 1e-9)
        assertEquals(2, trip.nights)
    }

    @Test
    fun `several stops land together, ids given out and totals recomputed`() = runTest {
        val id = addTrip()
        val other = addTrip(id = "other")
        val start = LocalDate.of(2026, 6, 1)
        repo.upsertStop(Stop(id = "a", tripId = id, name = "A", orderIndex = 0, nights = 1, campingCostTotal = 10.0, arrivalDate = start))
        repo.upsertStops(
            listOf(
                Stop(id = "a", tripId = id, name = "A", orderIndex = 1, nights = 2, campingCostTotal = 10.0, arrivalDate = start.plusDays(1)),
                Stop(tripId = id, name = "New", orderIndex = 0, nights = 1, campingCostTotal = 5.0, arrivalDate = start),
                Stop(tripId = other, name = "X", orderIndex = 0, nights = 3),
            ),
        )
        val stops = repo.stops(id).first()
        assertEquals(listOf("New", "A"), stops.map { it.name })
        assertTrue(stops.first().id.isNotBlank())
        assertEquals(3, repo.trip(id).first()!!.nights)
        assertEquals(15.0, repo.trip(id).first()!!.totalCost, 1e-9)
        assertEquals(3, repo.trip(other).first()!!.nights)

        repo.upsertStops(emptyList())
        assertEquals(listOf("New", "A"), repo.stops(id).first().map { it.name })
    }

    @Test
    fun `no stop starts before the one in front of it leaves`() = runTest {
        val start = LocalDate.of(2026, 9, 4)
        val id = repo.upsertTrip(Trip(id = "plan", name = "Plan", startDate = start, status = TripStatus.PLANNED))
        repo.upsertStop(Stop(id = "a", tripId = id, orderIndex = 0, arrivalDate = start, nights = 2))
        repo.upsertStop(Stop(id = "b", tripId = id, orderIndex = 1, arrivalDate = start.plusDays(1), nights = 2))
        repo.upsertStop(Stop(id = "home", tripId = id, orderIndex = 2, arrivalDate = start.plusDays(3), nights = 0))
        assertEquals(
            listOf(start, start.plusDays(2), start.plusDays(4)),
            repo.stops(id).first().map { it.arrivalDate },
        )
    }

    @Test
    fun `on a tour the planned stops settle too, but a done stop's departure binds nothing`() = runTest {
        val start = LocalDate.of(2026, 9, 4)
        val id = repo.upsertTrip(Trip(id = "tour", name = "Tour", startDate = start, status = TripStatus.ACTIVE))
        repo.upsertStop(Stop(id = "a", tripId = id, orderIndex = 0, arrivalDate = start, nights = 3, state = StopState.DONE))
        repo.upsertStop(Stop(id = "b", tripId = id, orderIndex = 1, arrivalDate = start.plusDays(1), nights = 2)) // left a early
        repo.upsertStop(Stop(id = "c", tripId = id, orderIndex = 2, arrivalDate = start.plusDays(2)))
        assertEquals(
            listOf(start, start.plusDays(1), start.plusDays(3)),
            repo.stops(id).first().map { it.arrivalDate },
        )
    }

    @Test
    fun `a finished trip is a record, never re-dated`() = runTest {
        val start = LocalDate.of(2026, 9, 4)
        val id = repo.upsertTrip(Trip(id = "done", name = "Done", startDate = start, status = TripStatus.DONE))
        repo.upsertStop(Stop(id = "a", tripId = id, orderIndex = 0, arrivalDate = start, nights = 2))
        repo.upsertStop(Stop(id = "b", tripId = id, orderIndex = 1, arrivalDate = start.plusDays(1)))
        assertEquals(start.plusDays(1), repo.stops(id).first().last().arrivalDate)
    }

    @Test
    fun `skipped stops are excluded from totals`() = runTest {
        val id = addTrip()
        repo.upsertStop(Stop(id = "kept", tripId = id, nights = 2, campingCostTotal = 50.0))
        repo.upsertStop(
            Stop(id = "skipped", tripId = id, nights = 3, campingCostTotal = 90.0, state = StopState.SKIPPED),
        )
        assertEquals(50.0, repo.trip(id).first()!!.totalCost, 1e-9)
        assertEquals(2, repo.trip(id).first()!!.nights)
    }

    @Test
    fun `a trip is named after its stops' regions, kept up to date like the totals`() = runTest {
        val id = addTrip()
        assertEquals("", repo.trip(id).first()!!.region)
        repo.upsertStop(Stop(id = "a", tripId = id, orderIndex = 0, region = StopRegion(name = "Ticino", country = "Switzerland")))
        repo.upsertStop(Stop(id = "b", tripId = id, orderIndex = 1, region = StopRegion(name = "Valais", country = "Switzerland")))
        assertEquals("Ticino & Valais", repo.trip(id).first()!!.region)
        repo.deleteStop(id, "b")
        assertEquals("Ticino", repo.trip(id).first()!!.region)
    }

    @Test
    fun `totals of other trips are untouched`() = runTest {
        val a = addTrip(id = "a")
        val b = addTrip(id = "b")
        repo.upsertStop(Stop(id = "sa", tripId = a, nights = 1, campingCostTotal = 10.0))
        repo.upsertStop(Stop(id = "sb", tripId = b, nights = 2, campingCostTotal = 20.0))
        assertEquals(10.0, repo.trip(a).first()!!.totalCost, 1e-9)
        assertEquals(20.0, repo.trip(b).first()!!.totalCost, 1e-9)
    }

    @Test
    fun `delete trip cascades to its stops and expenses`() = runTest {
        val id = addTrip(id = "gone")
        val keep = addTrip(id = "keep")
        repo.upsertStop(Stop(id = "s1", tripId = id))
        repo.upsertExpense(Expense(id = "e1", tripId = id, amount = 1.0))
        repo.upsertStop(Stop(id = "s2", tripId = keep))
        repo.deleteTrip(id)
        assertNull(repo.trip(id).first())
        assertTrue(repo.allStops().first().none { it.tripId == id })
        assertTrue(repo.allExpenses().first().none { it.tripId == id })
        assertEquals(1, repo.allStops().first().size)
    }

    @Test
    fun `all stops and all expenses span trips`() = runTest {
        repo.upsertStop(Stop(id = "s1", tripId = "t1"))
        repo.upsertStop(Stop(id = "s2", tripId = "t2"))
        repo.upsertExpense(Expense(id = "e1", tripId = "t1", amount = 1.0))
        repo.upsertExpense(Expense(id = "e2", tripId = "t2", amount = 2.0))
        assertEquals(setOf("s1", "s2"), repo.allStops().first().map { it.id }.toSet())
        assertEquals(setOf("e1", "e2"), repo.allExpenses().first().map { it.id }.toSet())
    }

    @Test
    fun `seeded repository has consistent demo data`() = runTest {
        val seeded = InMemoryTripRepository(seed = true)
        val trips = seeded.trips().first()
        assertEquals(4, trips.size)
        // Sorted by start date descending: Jura (2027, planned), Vierwaldstättersee
        // (active, dated around today), Schwarzwald, Provence.
        assertEquals("Jura–Ticino Runde", trips[0].name)
        assertEquals("Vierwaldstättersee", trips[1].name)
        assertEquals("Schwarzwald", trips[2].name)
        assertEquals("Provence", trips[3].name)
        assertEquals(TripStatus.PLANNED, trips[0].status)
        assertEquals(TripStatus.ACTIVE, trips[1].status)
        assertEquals(TripStatus.DONE, trips[2].status)
        assertEquals(TripStatus.DONE, trips[3].status)
        // Exact seeded totals: dropping seeded stops or expenses must be caught.
        assertEquals(84.0 + 128.0 + 30.0 + 145.30 + 98.60 + 61.40, trips[3].totalCost, 1e-9)
        assertEquals(9, trips[3].nights)
        // Active demo trip: one checked-in stop, a visit, and unpriced upcoming stops.
        assertEquals(84.0 + 40.0, trips[1].totalCost, 1e-9)
        assertEquals(5, trips[1].nights)
        val activeStops = seeded.stops(trips[1].id).first()
        assertEquals(StopState.DONE, activeStops.first().state)
        assertTrue(activeStops.any { it.kind == StopKind.VISIT })
        // Planned trip: recorded numbers only (entered price + vignette estimate).
        assertEquals(186.0 + 40.0, trips[0].totalCost, 1e-9)
        assertEquals(6, trips[0].nights)
        val juraStops = seeded.stops(trips[0].id).first()
        assertEquals(listOf(StopKind.CAMPSITE, StopKind.VISIT, StopKind.STELLPLATZ, StopKind.CAMPSITE), juraStops.map { it.kind })
        assertEquals(listOf(false, true, false, true), juraStops.map { it.costKnown })
        for (trip in trips) {
            val stops = seeded.stops(trip.id).first()
            val expenses = seeded.expenses(trip.id).first()
            assertTrue(stops.isNotEmpty())
            assertTrue(expenses.isNotEmpty())
            assertEquals(stops.sumOf { it.nights }, trip.nights)
            assertEquals(
                stops.sumOf { it.campingCostTotal } + expenses.sumOf { it.amount },
                trip.totalCost,
                1e-9,
            )
            assertTrue(stops.all { it.location != null })
        }
        assertNotEquals(LatLng(0.0, 0.0), seeded.stops(trips[0].id).first().first().location)
    }


    @Test
    fun `a plan starts the day its first stop does`() = runTest {
        val start = LocalDate.of(2026, 9, 2)
        val id = repo.upsertTrip(Trip(id = "plan", name = "Plan", startDate = start, status = TripStatus.PLANNED))
        val s2 = Stop(id = "s2", tripId = id, orderIndex = 1, arrivalDate = start.plusDays(6))
        repo.upsertStop(s2)
        assertEquals(start.plusDays(6), repo.trip(id).first()!!.startDate)

        val s1 = Stop(id = "s1", tripId = id, orderIndex = 0, arrivalDate = start.plusDays(4))
        repo.upsertStop(s1)
        assertEquals(start.plusDays(4), repo.trip(id).first()!!.startDate)

        repo.upsertStops(listOf(s2.copy(orderIndex = 0, arrivalDate = start.plusDays(2)), s1.copy(orderIndex = 1)))
        assertEquals(start.plusDays(2), repo.trip(id).first()!!.startDate)

        repo.upsertStop(Stop(id = "s2", tripId = id, orderIndex = 0, arrivalDate = start, state = StopState.SKIPPED))
        assertEquals(start.plusDays(4), repo.trip(id).first()!!.startDate)

        repo.deleteStop(id, "s1")
        assertEquals(start.plusDays(4), repo.trip(id).first()!!.startDate)
    }

    @Test
    fun `only a plan follows its stops`() = runTest {
        val start = LocalDate.of(2026, 9, 2)
        val active = repo.upsertTrip(Trip(id = "active", name = "Active", startDate = start, status = TripStatus.ACTIVE))
        repo.upsertStop(Stop(id = "a1", tripId = active, orderIndex = 0, arrivalDate = start.plusDays(2)))
        assertEquals(start, repo.trip(active).first()!!.startDate)

        val plan = repo.upsertTrip(Trip(id = "plan", name = "Plan", startDate = start, status = TripStatus.PLANNED))
        repo.upsertStop(Stop(id = "other", tripId = active, orderIndex = 1, arrivalDate = start.plusDays(3)))
        assertEquals(start, repo.trip(plan).first()!!.startDate)
    }
}
