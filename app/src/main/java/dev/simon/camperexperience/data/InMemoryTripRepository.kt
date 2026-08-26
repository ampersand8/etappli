package dev.simon.camperexperience.data

import dev.simon.camperexperience.data.model.Expense
import dev.simon.camperexperience.data.model.ExpenseType
import dev.simon.camperexperience.data.model.LatLng
import dev.simon.camperexperience.data.model.Stop
import dev.simon.camperexperience.data.model.Trip
import dev.simon.camperexperience.domain.CostCalculator
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
    }

    override suspend fun deleteStop(tripId: String, stopId: String) {
        stopsFlow.update { list -> list.filterNot { it.id == stopId } }
        recomputeTotals(tripId)
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

    private fun recomputeTotals(tripId: String) {
        val stops = stopsFlow.value.filter { it.tripId == tripId }
        val expenses = expensesFlow.value.filter { it.tripId == tripId }
        tripsFlow.update { list ->
            list.map { trip ->
                if (trip.id == tripId) {
                    trip.copy(
                        totalCost = CostCalculator.tripTotal(stops, expenses),
                        nights = CostCalculator.tripNights(stops),
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
        )
        val blackForest = Trip(
            id = "demo-blackforest",
            name = "Schwarzwald",
            startDate = LocalDate.of(2026, 5, 14),
            endDate = LocalDate.of(2026, 5, 18),
        )
        tripsFlow.value = listOf(provence, blackForest)
        stopsFlow.value = listOf(
            Stop(
                id = "demo-p1", tripId = provence.id, name = "Camping de la Durance",
                location = LatLng(44.0966, 6.2358), arrivalDate = LocalDate.of(2025, 9, 12),
                nights = 3, campingCostTotal = 84.0, orderIndex = 0,
            ),
            Stop(
                id = "demo-p2", tripId = provence.id, name = "Gorges du Verdon",
                location = LatLng(43.7497, 6.3286), arrivalDate = LocalDate.of(2025, 9, 15),
                nights = 4, campingCostTotal = 128.0, orderIndex = 1,
            ),
            Stop(
                id = "demo-p3", tripId = provence.id, name = "Aire de Cassis",
                location = LatLng(43.2140, 5.5396), arrivalDate = LocalDate.of(2025, 9, 19),
                nights = 2, campingCostTotal = 30.0, orderIndex = 2,
            ),
            Stop(
                id = "demo-b1", tripId = blackForest.id, name = "Camping Kirnbergsee",
                location = LatLng(47.9203, 8.3752), arrivalDate = LocalDate.of(2026, 5, 14),
                nights = 2, campingCostTotal = 56.0, orderIndex = 0,
            ),
            Stop(
                id = "demo-b2", tripId = blackForest.id, name = "Titisee",
                location = LatLng(47.8990, 8.1580), arrivalDate = LocalDate.of(2026, 5, 16),
                nights = 2, campingCostTotal = 64.0, orderIndex = 1,
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
        )
        recomputeTotals(provence.id)
        recomputeTotals(blackForest.id)
    }
}
