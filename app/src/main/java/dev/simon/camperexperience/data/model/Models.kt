package dev.simon.camperexperience.data.model

import java.time.LocalDate

data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

data class Trip(
    val id: String = "",
    val name: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    val notes: String = "",
    // Denormalized, recomputed by the repository on every stop/expense mutation.
    val totalCost: Double = 0.0,
    val nights: Int = 0,
)

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
)
