package com.nuelto.camperexperience.data.model

import java.time.LocalDate

data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

/** Trip lifecycle: a plan is a trip that hasn't happened yet. */
enum class TripStatus { PLANNED, ACTIVE, DONE }

/** Status for legacy Firestore docs that predate the field — never PLANNED. */
fun legacyTripStatus(endDate: LocalDate?): TripStatus =
    if (endDate != null && endDate.isBefore(LocalDate.now())) TripStatus.DONE else TripStatus.ACTIVE

data class Trip(
    val id: String = "",
    val name: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val notes: String = "",
    // Denormalized, recomputed by the repository on every stop/expense mutation.
    // Holds recorded numbers only (never display-time estimates); PLANNED/ACTIVE
    // trips always render totals via TripEstimator with an ≈ prefix.
    val totalCost: Double = 0.0,
    val nights: Int = 0,
    val status: TripStatus = TripStatus.ACTIVE,
    // Estimate snapshot taken when the tour is started, for planned-vs-actual.
    val plannedCost: Double? = null,
    val plannedNights: Int? = null,
)

/** Site type for pricing defaults; VISIT = drive-through waypoint (no nights, no cost). */
enum class StopKind { CAMPSITE, STELLPLATZ, FREE_CAMP, VISIT }

/** The stop's own position in the timeline. SKIPPED stops stay in the record but
 *  are excluded from route distance, nights, and cost math. */
enum class StopState { PLANNED, DONE, SKIPPED }

data class Stop(
    val id: String = "",
    val tripId: String = "",
    val name: String = "",
    val location: LatLng? = null,
    val arrivalDate: LocalDate = LocalDate.now(),
    val nights: Int = 1,
    val campingCostTotal: Double = 0.0,
    val orderIndex: Int = 0,
    val notes: String = "",
    val kind: StopKind = StopKind.CAMPSITE,
    val state: StopState = StopState.PLANNED,
    // false = not priced yet: estimates use nights × the kind's default rate.
    // true covers "free, price known: 0" — never write estimates into campingCostTotal.
    val costKnown: Boolean = true,
)

enum class ExpenseType {
    CAMPING,
    FUEL,
    ROAD_TAX,
    OTHER,
}

data class Expense(
    val id: String = "",
    val tripId: String = "",
    val type: ExpenseType = ExpenseType.OTHER,
    val amount: Double = 0.0,
    val date: LocalDate = LocalDate.now(),
    val label: String = "",
    val stopId: String? = null,
    val isEstimate: Boolean = false,
)

data class UserSettings(
    val currency: String = "CHF",
    val fuelConsumptionL100km: Double = 10.0,
    val fuelPricePerLiter: Double = 1.80,
    // Multiplier on straight-line (haversine) distance to approximate road distance.
    val roadDistanceFactor: Double = 1.25,
    // Default per-night camping estimates, used while a stop's price isn't known yet.
    val campsitePerNight: Double = 45.0,
    val stellplatzPerNight: Double = 15.0,
)
