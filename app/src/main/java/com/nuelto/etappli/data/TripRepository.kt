package com.nuelto.etappli.data

import com.nuelto.etappli.data.model.Expense
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.Trip
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

    /**
     * Writes several stops as one change (one totals recompute), so nothing ever sees
     * the plan half-moved: an edit and the shift it causes behind it land together.
     */
    suspend fun upsertStops(stops: List<Stop>)
    suspend fun deleteStop(tripId: String, stopId: String)
    suspend fun upsertExpense(expense: Expense)
    suspend fun deleteExpense(tripId: String, expenseId: String)
}
