package com.nuelto.camperexperience.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.domain.CostCalculator
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * Firestore-backed repository. Layout: users/{uid}/trips/{tripId}/stops|expenses.
 * Offline persistence is Firestore's built-in cache; all reads are snapshot
 * listeners and writes are fire-and-forget (queued while offline).
 *
 * Only used once Firebase is configured and the user is signed in — the UI's
 * auth gate guarantees a current user before any of this runs.
 */
class FirestoreTripRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : TripRepository {

    private val uid: String get() = requireNotNull(auth.currentUser).uid
    private fun trips(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("trips")

    // --- mapping -----------------------------------------------------------------

    private fun Trip.toMap() = mapOf(
        "name" to name,
        "startDate" to startDate.toEpochDay(),
        "endDate" to endDate?.toEpochDay(),
        "notes" to notes,
        "totalCost" to totalCost,
        "nights" to nights,
    )

    private fun DocumentSnapshot.toTrip(): Trip? {
        if (!exists()) return null
        return Trip(
            id = id,
            name = getString("name") ?: "",
            startDate = LocalDate.ofEpochDay(getLong("startDate") ?: 0L),
            endDate = getLong("endDate")?.let { LocalDate.ofEpochDay(it) },
            notes = getString("notes") ?: "",
            totalCost = getDouble("totalCost") ?: 0.0,
            nights = (getLong("nights") ?: 0L).toInt(),
        )
    }

    private fun Stop.toMap() = mapOf(
        "name" to name,
        "location" to location?.let { GeoPoint(it.latitude, it.longitude) },
        "arrivalDate" to arrivalDate.toEpochDay(),
        "nights" to nights,
        "campingCostTotal" to campingCostTotal,
        "orderIndex" to orderIndex,
        "notes" to notes,
    )

    private fun DocumentSnapshot.toStop(tripId: String): Stop = Stop(
        id = id,
        tripId = tripId,
        name = getString("name") ?: "",
        location = getGeoPoint("location")?.let { LatLng(it.latitude, it.longitude) },
        arrivalDate = LocalDate.ofEpochDay(getLong("arrivalDate") ?: 0L),
        nights = (getLong("nights") ?: 1L).toInt(),
        campingCostTotal = getDouble("campingCostTotal") ?: 0.0,
        orderIndex = (getLong("orderIndex") ?: 0L).toInt(),
        notes = getString("notes") ?: "",
    )

    private fun Expense.toMap() = mapOf(
        "type" to type.name,
        "amount" to amount,
        "date" to date.toEpochDay(),
        "label" to label,
        "stopId" to stopId,
        "isEstimate" to isEstimate,
    )

    private fun DocumentSnapshot.toExpense(tripId: String): Expense = Expense(
        id = id,
        tripId = tripId,
        type = getString("type")?.let { runCatching { ExpenseType.valueOf(it) }.getOrNull() }
            ?: ExpenseType.OTHER,
        amount = getDouble("amount") ?: 0.0,
        date = LocalDate.ofEpochDay(getLong("date") ?: 0L),
        label = getString("label") ?: "",
        stopId = getString("stopId"),
        isEstimate = getBoolean("isEstimate") ?: false,
    )

    private fun Query.asSnapshotFlow(): Flow<List<DocumentSnapshot>> = callbackFlow {
        val registration = addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(snapshot.documents)
            }
        }
        awaitClose { registration.remove() }
    }

    // --- reads -------------------------------------------------------------------

    override fun trips(): Flow<List<Trip>> =
        trips(uid).orderBy("startDate", Query.Direction.DESCENDING).asSnapshotFlow()
            .map { docs -> docs.mapNotNull { it.toTrip() } }

    override fun trip(tripId: String): Flow<Trip?> = callbackFlow {
        val registration = trips(uid).document(tripId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.toTrip())
        }
        awaitClose { registration.remove() }
    }

    override fun stops(tripId: String): Flow<List<Stop>> =
        trips(uid).document(tripId).collection("stops").orderBy("orderIndex").asSnapshotFlow()
            .map { docs -> docs.map { it.toStop(tripId) } }

    override fun expenses(tripId: String): Flow<List<Expense>> =
        trips(uid).document(tripId).collection("expenses").orderBy("date").asSnapshotFlow()
            .map { docs -> docs.map { it.toExpense(tripId) } }

    // A collection-group query on "stops" would be rejected by the security rules
    // (they are path-scoped, and rules don't filter queries — the query itself must
    // be provably allowed), so combine the per-trip stops listeners instead.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun allStops(): Flow<List<Stop>> =
        trips().flatMapLatest { trips ->
            if (trips.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(trips.map { trip -> stops(trip.id) }) { perTrip -> perTrip.toList().flatten() }
            }
        }

    // Same path-scoped-rules constraint as allStops(): no collection-group query.
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun allExpenses(): Flow<List<Expense>> =
        trips().flatMapLatest { trips ->
            if (trips.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(trips.map { trip -> expenses(trip.id) }) { perTrip -> perTrip.toList().flatten() }
            }
        }

    // --- writes ------------------------------------------------------------------

    override suspend fun upsertTrip(trip: Trip): String {
        val doc = if (trip.id.isBlank()) trips(uid).document() else trips(uid).document(trip.id)
        doc.set(trip.toMap())
        return doc.id
    }

    override suspend fun deleteTrip(tripId: String) {
        val tripDoc = trips(uid).document(tripId)
        // Subcollection docs must be deleted explicitly; cached docs cover offline use.
        for (sub in listOf("stops", "expenses")) {
            val docs = runCatching { tripDoc.collection(sub).get(Source.CACHE).await() }
                .getOrNull() ?: runCatching { tripDoc.collection(sub).get().await() }.getOrNull()
            docs?.documents?.forEach { it.reference.delete() }
        }
        tripDoc.delete()
    }

    override suspend fun upsertStop(stop: Stop) {
        val col = trips(uid).document(stop.tripId).collection("stops")
        val doc = if (stop.id.isBlank()) col.document() else col.document(stop.id)
        doc.set(stop.toMap())
        recomputeTotals(stop.tripId)
    }

    override suspend fun deleteStop(tripId: String, stopId: String) {
        trips(uid).document(tripId).collection("stops").document(stopId).delete()
        recomputeTotals(tripId)
    }

    override suspend fun upsertExpense(expense: Expense) {
        val col = trips(uid).document(expense.tripId).collection("expenses")
        val doc = if (expense.id.isBlank()) col.document() else col.document(expense.id)
        doc.set(expense.toMap())
        recomputeTotals(expense.tripId)
    }

    override suspend fun deleteExpense(tripId: String, expenseId: String) {
        trips(uid).document(tripId).collection("expenses").document(expenseId).delete()
        recomputeTotals(tripId)
    }

    /**
     * Denormalizes totalCost/nights onto the trip document. Reads from cache, which
     * already includes the pending local writes, so this works offline too.
     */
    private suspend fun recomputeTotals(tripId: String) {
        val tripDoc = trips(uid).document(tripId)
        val stops = runCatching {
            tripDoc.collection("stops").get(Source.CACHE).await()
                .documents.map { it.toStop(tripId) }
        }.getOrNull() ?: return
        val expenses = runCatching {
            tripDoc.collection("expenses").get(Source.CACHE).await()
                .documents.map { it.toExpense(tripId) }
        }.getOrNull() ?: return
        tripDoc.update(
            mapOf(
                "totalCost" to CostCalculator.tripTotal(stops, expenses),
                "nights" to CostCalculator.tripNights(stops),
            ),
        )
    }
}
