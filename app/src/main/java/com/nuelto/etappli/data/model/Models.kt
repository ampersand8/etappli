package com.nuelto.etappli.data.model

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
    // What the user called it, if anything: what it is called is domain/TripName's Trip.title.
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
    // Where it goes, denormalized from the stops' regions like the totals — the title
    // while [name] is blank.
    val region: String = "",
)

/**
 * Site type for pricing defaults. VISIT is a drive-through waypoint and HOME is where the
 * trip starts from — neither is a night, and neither costs anything.
 */
enum class StopKind { CAMPSITE, STELLPLATZ, FREE_CAMP, VISIT, HOME }

/**
 * True for the kinds you sleep and pay at. Everything nights- or price-related keys off
 * this rather than naming VISIT, so HOME could be added without hunting down the checks.
 */
val StopKind.isStay: Boolean
    get() = this == StopKind.CAMPSITE || this == StopKind.STELLPLATZ || this == StopKind.FREE_CAMP

/** The stop's own position in the timeline. SKIPPED stops stay in the record but
 *  are excluded from route distance, nights, and cost math. */
enum class StopState { PLANNED, DONE, SKIPPED }

/**
 * How high the stop sits above sea level. [at] is the coordinate it was measured for, so
 * moving the pin invalidates it by comparison. Copernicus is open data, so unlike a Places
 * coordinate this never expires.
 */
data class StopElevation(val at: LatLng = LatLng(0.0, 0.0), val meters: Int = 0)

/**
 * Which region a stop lies in — the canton, province or state, and the country — for
 * naming the tour after where it goes (domain/TripName). [at] is the coordinate it was
 * looked up for, so a moved pin invalidates it by comparison, like [StopElevation].
 */
data class StopRegion(val at: LatLng = LatLng(0.0, 0.0), val name: String = "", val country: String = "")

/**
 * A ride on public transport between where the vehicle is left and a stop no road reaches
 * — the cable car up to a car-free village, the ferry to an island — as Google routed it
 * (domain/ParkAndRide). Lives on the leg either side of the drive: see [StopLeg].
 */
data class TransitRide(
    // Where the vehicle waits: the valley station, the ferry port — the end of the road.
    val parked: LatLng = LatLng(0.0, 0.0),
    // Encoded polyline from [parked] up to the stop, rides and walks together.
    val polyline: String = "",
    val distanceMeters: Int = 0,
    val durationSeconds: Int = 0,
    // What you board, in words: "cable car", "train + bus".
    val mode: String = "",
)

/**
 * The drive from the previous stop, as Google routed it. [from]/[to] are the stop
 * coordinates the route was computed between, so a reorder or a moved pin invalidates
 * the leg by simple comparison rather than by hoping something remembered to clear it.
 *
 * Google's terms let a routed coordinate be kept for 30 days (SST §19.3, the same window
 * as the Places rule), so [fetchedAt] is not optional — see domain/RouteCache.
 */
data class StopLeg(
    val from: LatLng = LatLng(0.0, 0.0),
    val to: LatLng = LatLng(0.0, 0.0),
    // Encoded polyline, decoded for drawing by domain/Polyline.
    val polyline: String = "",
    // Zero when no road reaches the stop and no ride does either — an answer worth
    // keeping too, so the same drive is not asked about again for 30 days.
    val distanceMeters: Int = 0,
    val durationSeconds: Int = 0,
    // Cumulative climb along the leg, from an open DEM — null until it is fetched, and
    // null forever if the elevation source is unreachable. Google is not the source:
    // its Elevation API forbids storing results at all.
    val ascentMeters: Int? = null,
    val descentMeters: Int? = null,
    // Rides either side of the drive where a stop has no road (domain/ParkAndRide): down
    // from the stop before to where the vehicle was left, and up from where it is left to
    // this one. The road then runs between the parking spots; [from]/[to] stay the stops'.
    val rideBefore: TransitRide? = null,
    val rideAfter: TransitRide? = null,
    val fetchedAt: LocalDate = LocalDate.now(),
) {
    /** False for a drive Google could route neither by road nor by ride: a straight hop. */
    val hasRoad: Boolean get() = distanceMeters > 0
}

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
    // Google Place ID, which the Places terms allow keeping indefinitely — unlike the
    // coordinate, which they don't. Null for GPS fixes, map picks and OSM results.
    val placeId: String? = null,
    // When [location] was fetched from Google Places. Null means the coordinate came
    // from somewhere without a retention limit and never expires. See domain/PlaceCache.
    val locationCachedAt: LocalDate? = null,
    // The drive that arrives here, from the stop before it. Null on the first stop of a
    // trip, and until the route has been fetched.
    val leg: StopLeg? = null,
    val elevation: StopElevation? = null,
    val region: StopRegion? = null,
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
    // Fallback multiplier on straight-line (haversine) distance, used only for legs with
    // no routed distance — offline, no API key, or a fetch that failed.
    val roadDistanceFactor: Double = 1.25,
    // Laden weight, for the climb term of the fuel estimate. 3.5 t is the usual limit a
    // camper is built to sit under.
    val vehicleMassKg: Double = 3500.0,
    // Where trips start from. A new plan opens with this as its first stop; null until it
    // has been set, and then plans simply start at whatever you add first.
    val homeName: String = "",
    val homeLocation: LatLng? = null,
    // Default per-night camping estimates, used while a stop's price isn't known yet.
    val campsitePerNight: Double = 45.0,
    val stellplatzPerNight: Double = 15.0,
)
