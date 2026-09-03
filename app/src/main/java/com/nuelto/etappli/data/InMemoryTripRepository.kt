package com.nuelto.etappli.data

import com.nuelto.etappli.data.model.Expense
import com.nuelto.etappli.data.model.ExpenseType
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopElevation
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.domain.CostCalculator
import com.nuelto.etappli.domain.DateCascade
import com.nuelto.etappli.domain.TripName
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Local stand-in for the Firestore repository (used until M4). Holds everything in
 * StateFlows and keeps the denormalized Trip.totalCost/nights consistent the same way
 * the Firestore implementation will.
 */
class InMemoryTripRepository(seed: Boolean = true) : TripRepository {

    private val tripsFlow = MutableStateFlow<List<Trip>>(emptyList())
    private val stopsFlow = MutableStateFlow<List<Stop>>(emptyList())
    private val expensesFlow = MutableStateFlow<List<Expense>>(emptyList())

    init {
        if (seed) seedDemoData()
    }

    override fun trips(): Flow<List<Trip>> =
        tripsFlow.map { list -> list.sortedByDescending { it.startDate } }

    override fun trip(tripId: String): Flow<Trip?> =
        tripsFlow.map { list -> list.find { it.id == tripId } }

    override fun stops(tripId: String): Flow<List<Stop>> =
        stopsFlow.map { list -> list.filter { it.tripId == tripId }.sortedBy { it.orderIndex } }

    override fun expenses(tripId: String): Flow<List<Expense>> =
        expensesFlow.map { list -> list.filter { it.tripId == tripId }.sortedBy { it.date } }

    override fun allStops(): Flow<List<Stop>> = stopsFlow

    override fun allExpenses(): Flow<List<Expense>> = expensesFlow

    override suspend fun upsertTrip(trip: Trip): String {
        val withId = if (trip.id.isBlank()) trip.copy(id = UUID.randomUUID().toString()) else trip
        tripsFlow.update { list -> list.filterNot { it.id == withId.id } + withId }
        recomputeTotals(withId.id)
        return withId.id
    }

    override suspend fun deleteTrip(tripId: String) {
        tripsFlow.update { list -> list.filterNot { it.id == tripId } }
        stopsFlow.update { list -> list.filterNot { it.tripId == tripId } }
        expensesFlow.update { list -> list.filterNot { it.tripId == tripId } }
    }

    override suspend fun upsertStop(stop: Stop) {
        val withId = if (stop.id.isBlank()) stop.copy(id = UUID.randomUUID().toString()) else stop
        stopsFlow.update { list -> list.filterNot { it.id == withId.id } + withId }
        recomputeTotals(withId.tripId)
        redatePlan(withId.tripId)
    }

    override suspend fun deleteStop(tripId: String, stopId: String) {
        stopsFlow.update { list -> list.filterNot { it.id == stopId } }
        recomputeTotals(tripId)
        redatePlan(tripId)
    }

    override suspend fun reorderStops(tripId: String, orderedStopIds: List<String>) {
        stopsFlow.update { list ->
            list.map { stop ->
                val index = orderedStopIds.indexOf(stop.id)
                if (stop.tripId == tripId && index >= 0) stop.copy(orderIndex = index) else stop
            }
        }
        recomputeTotals(tripId)
        redatePlan(tripId)
    }

    override suspend fun upsertExpense(expense: Expense) {
        val withId = if (expense.id.isBlank()) expense.copy(id = UUID.randomUUID().toString()) else expense
        expensesFlow.update { list -> list.filterNot { it.id == withId.id } + withId }
        recomputeTotals(withId.tripId)
    }

    override suspend fun deleteExpense(tripId: String, expenseId: String) {
        expensesFlow.update { list -> list.filterNot { it.id == expenseId } }
        recomputeTotals(tripId)
    }

    /** A plan starts the day its first stop does; stop edits keep the trip saying so. */
    private fun redatePlan(tripId: String) {
        val start = DateCascade.start(stopsFlow.value.filter { it.tripId == tripId }) ?: return
        tripsFlow.update { list ->
            list.map { trip ->
                if (trip.id == tripId && trip.status == TripStatus.PLANNED) {
                    trip.copy(startDate = start)
                } else {
                    trip
                }
            }
        }
    }

    private fun recomputeTotals(tripId: String) {
        val stops = stopsFlow.value.filter { it.tripId == tripId }
        val expenses = expensesFlow.value.filter { it.tripId == tripId }
        tripsFlow.update { list ->
            list.map { trip ->
                if (trip.id == tripId) {
                    trip.copy(
                        totalCost = CostCalculator.tripTotal(stops, expenses),
                        nights = CostCalculator.tripNights(stops),
                        region = TripName.region(stops),
                    )
                } else {
                    trip
                }
            }
        }
    }

    private fun seedDemoData() {
        val provence = Trip(
            id = "demo-provence",
            name = "Provence",
            startDate = LocalDate.of(2025, 9, 12),
            endDate = LocalDate.of(2025, 9, 21),
            notes = "Lavender was long gone, gorges were empty. Perfect.",
            status = TripStatus.DONE,
        )
        val blackForest = Trip(
            id = "demo-blackforest",
            name = "Schwarzwald",
            startDate = LocalDate.of(2026, 5, 14),
            endDate = LocalDate.of(2026, 5, 18),
            status = TripStatus.DONE,
        )
        val jura = Trip(
            id = "demo-jura",
            name = "Jura–Ticino Runde",
            startDate = LocalDate.of(2027, 6, 10),
            notes = "Über den Gotthard, zurück am Lago Maggiore entlang.",
            status = TripStatus.PLANNED,
        )
        // Dated relative to today so the in-progress color coding always shows.
        val vierwald = Trip(
            id = "demo-vierwald",
            name = "Vierwaldstättersee",
            startDate = LocalDate.now().minusDays(1),
            status = TripStatus.ACTIVE,
        )
        tripsFlow.value = listOf(provence, blackForest, jura, vierwald)
        stopsFlow.value = listOf(
            Stop(
                id = "demo-p1", tripId = provence.id, name = "Camping de la Durance",
                location = LatLng(44.0966, 6.2358), arrivalDate = LocalDate.of(2025, 9, 12),
                nights = 3, campingCostTotal = 84.0, orderIndex = 0,
                elevation = StopElevation(LatLng(44.0966, 6.2358), 449),
            ),
            Stop(
                id = "demo-p2", tripId = provence.id, name = "Gorges du Verdon",
                location = LatLng(43.7497, 6.3286), arrivalDate = LocalDate.of(2025, 9, 15),
                nights = 4, campingCostTotal = 128.0, orderIndex = 1,
                elevation = StopElevation(LatLng(43.7497, 6.3286), 782),
            ),
            Stop(
                id = "demo-p3", tripId = provence.id, name = "Aire de Cassis",
                location = LatLng(43.2140, 5.5396), arrivalDate = LocalDate.of(2025, 9, 19),
                nights = 2, campingCostTotal = 30.0, orderIndex = 2,
                elevation = StopElevation(LatLng(43.214, 5.5396), 17),
            ),
            Stop(
                id = "demo-b1", tripId = blackForest.id, name = "Camping Kirnbergsee",
                location = LatLng(47.9203, 8.3752), arrivalDate = LocalDate.of(2026, 5, 14),
                nights = 2, campingCostTotal = 56.0, orderIndex = 0,
                elevation = StopElevation(LatLng(47.9203, 8.3752), 780),
            ),
            Stop(
                id = "demo-b2", tripId = blackForest.id, name = "Titisee",
                location = LatLng(47.8990, 8.1580), arrivalDate = LocalDate.of(2026, 5, 16),
                nights = 2, campingCostTotal = 64.0, orderIndex = 1,
                elevation = StopElevation(LatLng(47.899, 8.158), 858),
            ),
            Stop(
                id = "demo-j1", tripId = jura.id, name = "Camping Lido Luzern",
                location = LatLng(47.0410, 8.3370), arrivalDate = LocalDate.of(2027, 6, 10),
                nights = 2, orderIndex = 0, costKnown = false,
                elevation = StopElevation(LatLng(47.041, 8.337), 434),
            ),
            Stop(
                id = "demo-j2", tripId = jura.id, name = "Aareschlucht",
                location = LatLng(46.7226, 8.2231), arrivalDate = LocalDate.of(2027, 6, 12),
                nights = 0, orderIndex = 1, kind = StopKind.VISIT,
                elevation = StopElevation(LatLng(46.7226, 8.2231), 600),
            ),
            Stop(
                id = "demo-j3", tripId = jura.id, name = "Stellplatz Airolo",
                location = LatLng(46.5285, 8.6120), arrivalDate = LocalDate.of(2027, 6, 12),
                nights = 1, orderIndex = 2, kind = StopKind.STELLPLATZ, costKnown = false,
                elevation = StopElevation(LatLng(46.5285, 8.612), 1175),
            ),
            Stop(
                id = "demo-j4", tripId = jura.id, name = "Camping Delta Locarno",
                location = LatLng(46.1591, 8.7853), arrivalDate = LocalDate.of(2027, 6, 13),
                nights = 3, campingCostTotal = 186.0, orderIndex = 3,
                elevation = StopElevation(LatLng(46.1591, 8.7853), 199),
            ),
            Stop(
                id = "demo-v1", tripId = vierwald.id, name = "Camping Seefeld Sarnen",
                location = LatLng(46.8709, 8.2492), arrivalDate = LocalDate.now().minusDays(1),
                nights = 2, campingCostTotal = 84.0, orderIndex = 0, state = StopState.DONE,
                elevation = StopElevation(LatLng(46.8709, 8.2492), 473),
            ),
            Stop(
                id = "demo-v2", tripId = vierwald.id, name = "Rütli",
                location = LatLng(46.9690, 8.5990), arrivalDate = LocalDate.now().plusDays(1),
                nights = 0, orderIndex = 1, kind = StopKind.VISIT,
                elevation = StopElevation(LatLng(46.969, 8.599), 451),
            ),
            Stop(
                id = "demo-v3", tripId = vierwald.id, name = "Stellplatz Brunnen",
                location = LatLng(46.9930, 8.6060), arrivalDate = LocalDate.now().plusDays(1),
                nights = 1, orderIndex = 2, kind = StopKind.STELLPLATZ, costKnown = false,
                elevation = StopElevation(LatLng(46.993, 8.606), 439),
            ),
            Stop(
                id = "demo-v4", tripId = vierwald.id, name = "Camping Buochs",
                location = LatLng(46.9740, 8.4230), arrivalDate = LocalDate.now().plusDays(2),
                nights = 2, orderIndex = 3, costKnown = false,
                elevation = StopElevation(LatLng(46.974, 8.423), 435),
            ),
        )
        expensesFlow.value = listOf(
            Expense(
                id = "demo-e1", tripId = provence.id, type = ExpenseType.FUEL, amount = 145.30,
                date = LocalDate.of(2025, 9, 12), label = "Diesel A7",
            ),
            Expense(
                id = "demo-e2", tripId = provence.id, type = ExpenseType.FUEL, amount = 98.60,
                date = LocalDate.of(2025, 9, 20), label = "Diesel Rückweg",
            ),
            Expense(
                id = "demo-e3", tripId = provence.id, type = ExpenseType.ROAD_TAX, amount = 61.40,
                date = LocalDate.of(2025, 9, 12), label = "Péage A7/A51",
            ),
            Expense(
                id = "demo-e4", tripId = blackForest.id, type = ExpenseType.FUEL, amount = 87.20,
                date = LocalDate.of(2026, 5, 14), label = "Diesel",
            ),
            Expense(
                id = "demo-e5", tripId = blackForest.id, type = ExpenseType.OTHER, amount = 24.0,
                date = LocalDate.of(2026, 5, 16), label = "Seilbahn",
            ),
            Expense(
                id = "demo-e6", tripId = jura.id, type = ExpenseType.ROAD_TAX, amount = 40.0,
                date = LocalDate.of(2027, 6, 10), label = "CH Vignette", isEstimate = true,
            ),
            Expense(
                id = "demo-e7", tripId = vierwald.id, type = ExpenseType.ROAD_TAX, amount = 40.0,
                date = LocalDate.now().minusDays(1), label = "CH Vignette", isEstimate = true,
            ),
        )
        recomputeTotals(provence.id)
        recomputeTotals(blackForest.id)
        recomputeTotals(jura.id)
        recomputeTotals(vierwald.id)
    }
}
