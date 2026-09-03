package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One drive between two consecutive stops, as Google routed it. */
data class RoutedLeg(
    val polyline: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
)

/** One step of a transit route: a walk, or a ride on one vehicle from where it is boarded. */
data class TransitStep(
    val polyline: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    // Google's vehicle type ("GONDOLA_LIFT"); null for a walk.
    val vehicle: String? = null,
    // Where the ride is boarded — where a vehicle could be left. Null for a walk.
    val departure: LatLng? = null,
)

/**
 * Google Routes API (computeRoutes): the road-following geometry, distance and driving
 * time between stops. Pure request building and lenient parsing; the HTTP call is
 * location/GoogleRoutesService.
 *
 * Note this is a web service, not the Android SDK — an API key restricted to Android
 * apps cannot call it. See GOOGLE_MAPS_SETUP.md.
 */
object GoogleRoutes {

    const val COMPUTE_ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"

    /**
     * Mandatory — the API has no default field set and rejects a request without a mask.
     * Per-leg only: the route totals are the sum of the legs, and every extra field is
     * billed. `*` would work and is what the docs warn against.
     */
    const val FIELD_MASK = "routes.legs.distanceMeters,routes.legs.duration," +
        "routes.legs.polyline.encodedPolyline"

    /** Per step: the way, the numbers, where each ride boards and on what. */
    const val TRANSIT_FIELD_MASK = "routes.legs.steps.travelMode," +
        "routes.legs.steps.polyline.encodedPolyline,routes.legs.steps.distanceMeters," +
        "routes.legs.steps.staticDuration," +
        "routes.legs.steps.transitDetails.stopDetails.departureStop.location," +
        "routes.legs.steps.transitDetails.transitLine.vehicle.type"

    /**
     * Origin, destination and at most 10 intermediates. Eleven intermediates is one of
     * the four things that move the call from the Compute Routes Essentials SKU to Pro
     * at twice the price, so a longer trip is split into overlapping windows instead of
     * paying for one big request. (The API's own hard cap is 25.)
     */
    const val MAX_POINTS_PER_REQUEST = 12

    /**
     * The trip's points split into requests, each sharing its first point with the
     * previous window's last so the legs join up. Fewer than two points is not a drive.
     */
    fun windows(points: List<LatLng>): List<List<LatLng>> {
        if (points.size < 2) return emptyList()
        val windows = mutableListOf<List<LatLng>>()
        var start = 0
        while (start < points.size - 1) {
            val end = minOf(start + MAX_POINTS_PER_REQUEST, points.size)
            windows += points.subList(start, end)
            start = end - 1
        }
        return windows
    }

    /**
     * Intermediates are plain (non-`via`) waypoints, which is what makes Google return
     * one leg per pair of stops — a `via` waypoint would collapse the whole window into
     * a single leg and lose the per-stop numbers.
     *
     * TRAFFIC_UNAWARE deliberately: live traffic is meaningless for a trip planned weeks
     * out, and asking for it is another jump to the Pro SKU.
     */
    fun computeRoutesBody(points: List<LatLng>): String {
        val middle = points.subList(1, points.size - 1)
        val intermediates =
            if (middle.isEmpty()) {
                ""
            } else {
                middle.joinToString(",", ",\"intermediates\":[", "]") { waypoint(it) }
            }
        return "{\"origin\":${waypoint(points.first())}," +
            "\"destination\":${waypoint(points.last())}$intermediates," +
            "\"travelMode\":\"DRIVE\",\"routingPreference\":\"TRAFFIC_UNAWARE\"," +
            "\"polylineQuality\":\"HIGH_QUALITY\"}"
    }

    /**
     * Public transport from [from] to [to]: how a stop no road reaches is still reached
     * (ParkAndRide). No intermediates and no routing preference — the mode allows neither.
     */
    fun transitBody(from: LatLng, to: LatLng): String =
        "{\"origin\":${waypoint(from)},\"destination\":${waypoint(to)}," +
            "\"travelMode\":\"TRANSIT\",\"polylineQuality\":\"HIGH_QUALITY\"}"

    private fun waypoint(point: LatLng): String =
        "{\"location\":{\"latLng\":{\"latitude\":${point.latitude}," +
            "\"longitude\":${point.longitude}}}}"

    /**
     * The legs of the first route, in order. Order is the whole contract — leg *i* is
     * the drive to point *i+1* — so a malformed entry discards the response rather than
     * silently shifting every leg onto the wrong stop.
     */
    fun parseLegs(body: String): List<RoutedLeg> {
        val legs = firstRouteLegs(body) ?: return emptyList()
        return legs.map { element ->
            val leg = element as? JsonObject ?: return emptyList()
            RoutedLeg(
                polyline = leg.obj("polyline")?.string("encodedPolyline").orEmpty(),
                // Proto3 JSON omits zero-valued fields, so a zero-length leg arrives
                // with neither number present.
                distanceMeters = leg.int("distanceMeters") ?: 0,
                durationSeconds = leg.string("duration")?.let(::parseDuration) ?: 0,
            )
        }
    }

    /**
     * The steps of a transit route — walks and rides, in order. Such a route has one leg,
     * the mode allowing no intermediates. As with [parseLegs], one malformed step discards
     * the whole response.
     */
    fun parseTransitSteps(body: String): List<TransitStep> {
        val leg = firstRouteLegs(body)?.firstOrNull() as? JsonObject ?: return emptyList()
        val steps = leg["steps"] as? JsonArray ?: return emptyList()
        return steps.map { element ->
            val step = element as? JsonObject ?: return emptyList()
            val details = step.obj("transitDetails")
            val ride = step.string("travelMode") == "TRANSIT"
            TransitStep(
                polyline = step.obj("polyline")?.string("encodedPolyline").orEmpty(),
                distanceMeters = step.int("distanceMeters") ?: 0,
                durationSeconds = step.string("staticDuration")?.let(::parseDuration) ?: 0,
                // A ride is a ride even when Google does not say on what.
                vehicle = if (ride) (details?.obj("transitLine")?.obj("vehicle")?.string("type") ?: "OTHER") else null,
                departure = details?.obj("stopDetails")?.obj("departureStop")?.obj("location")?.latLng(),
            )
        }
    }

    /** Durations are protobuf strings — seconds with optional fraction, then "s". */
    fun parseDuration(text: String): Int? =
        text.removeSuffix("s").toDoubleOrNull()?.roundToInt()

    private fun firstRouteLegs(body: String): JsonArray? {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        val routes = root?.get("routes") as? JsonArray ?: return null
        return (routes.firstOrNull() as? JsonObject)?.get("legs") as? JsonArray
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toIntOrNull()

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()

    private fun JsonObject.latLng(): LatLng? {
        val latLng = obj("latLng") ?: return null
        val lat = latLng.double("latitude") ?: return null
        val lng = latLng.double("longitude") ?: return null
        return LatLng(lat, lng)
    }
}
