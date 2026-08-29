package com.nuelto.camperexperience.data

import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun trips(): Flow<List<Trip>>
    fun trip(tripId: String): Flow<Trip?>
    fun stops(tripId: String): Flow<List<Stop>>
    fun expenses(tripId: String): Flow<List<Expense>>

    /** Every stop across all trips, for the all-trips map. */
    fun allStops(): Flow<List<Stop>>

    /** Every expense across all trips, for per-trip fuel estimates on the trip list. */
    fun allExpenses(): Flow<List<Expense>>

    /** Returns the trip id (generated when trip.id is blank). */
    suspend fun upsertTrip(trip: Trip): String
    suspend fun deleteTrip(tripId: String)
    suspend fun upsertStop(stop: Stop)

    /** Rewrites orderIndex to match [orderedStopIds] in one pass (one totals recompute). */
    suspend fun reorderStops(tripId: String, orderedStopIds: List<String>)
    suspend fun deleteStop(tripId: String, stopId: String)
    suspend fun upsertExpense(expense: Expense)
    suspend fun deleteExpense(tripId: String, expenseId: String)
}
