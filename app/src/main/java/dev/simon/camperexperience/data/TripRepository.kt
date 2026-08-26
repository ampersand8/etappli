package dev.simon.camperexperience.data

import dev.simon.camperexperience.data.model.Expense
import dev.simon.camperexperience.data.model.Stop
import dev.simon.camperexperience.data.model.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun trips(): Flow<List<Trip>>
    fun trip(tripId: String): Flow<Trip?>
    fun stops(tripId: String): Flow<List<Stop>>
    fun expenses(tripId: String): Flow<List<Expense>>

    /** Every stop across all trips, for the all-trips map. */
    fun allStops(): Flow<List<Stop>>

    /** Returns the trip id (generated when trip.id is blank). */
    suspend fun upsertTrip(trip: Trip): String
    suspend fun deleteTrip(tripId: String)
    suspend fun upsertStop(stop: Stop)
    suspend fun deleteStop(tripId: String, stopId: String)
    suspend fun upsertExpense(expense: Expense)
    suspend fun deleteExpense(tripId: String, expenseId: String)
}
