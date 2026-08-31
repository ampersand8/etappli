package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.data.model.Stop
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Cumulative climb and drop along a leg, in metres. */
data class Climb(val ascentMeters: Double, val descentMeters: Double)

/**
 * Height along a route, from Open-Meteo's elevation service (Copernicus DEM GLO-90).
 *
 * Not Google: the Elevation API's policies forbid caching or storing its results at all,
 * and a per-leg climb figure that has to be re-fetched every time the trip list is drawn
 * is no use. Copernicus is open data, so the derived numbers can simply be kept — see
 * [ATTRIBUTION], which the licence requires.
 */
object Elevation {

    const val ATTRIBUTION = "Elevation © Copernicus DEM via Open-Meteo"

    private const val BASE_URL = "https://api.open-meteo.com/v1/elevation"

    /** The service takes at most 100 coordinates per call. */
    const val MAX_SAMPLES = 100

    /**
     * Below this, a rise is road undulation rather than a climb. It has to be this large:
     * cumulative ascent does not converge — sample the same 215 km route 16× more finely
     * and a 10 m threshold grows the total by 47%. At 50 m, with [MAX_ROAD_GRADE], the
     * same range of densities moves it by 6%.
     *
     * Undulation is also already inside the user's measured l/100km, so charging for it
     * would count it twice. Only sustained climbs are new information.
     */
    const val MIN_RISE_M = 50.0

    /**
     * Steepest average grade a road sustains between two samples. Anything above it is
     * the DEM describing terrain rather than tarmac — a tunnel reports the mountain over
     * it, which on one Swiss route showed a road passing through 2876 m, several hundred
     * metres above the highest pass in the country.
     */
    const val MAX_ROAD_GRADE = 0.08

    fun url(points: List<LatLng>): String =
        "$BASE_URL?latitude=${points.joinToString(",") { it.latitude.toString() }}" +
            "&longitude=${points.joinToString(",") { it.longitude.toString() }}"

    /** Lenient: an error body or a surprise shape yields no heights, never a throw. */
    fun parse(body: String): List<Double> {
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        val elevation = root?.get("elevation") as? JsonArray ?: return emptyList()
        return elevation.map { element ->
            (element as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull()
                ?: return emptyList()
        }
    }

    /**
     * [count] points spread evenly by *distance* along the path — not by index. A decoded
     * polyline crowds its vertices into the switchbacks, so sampling by index would spend
     * most of the budget on the hairpins and skip the long straight climb between them.
     */
    fun sample(points: List<LatLng>, count: Int = MAX_SAMPLES): List<LatLng> {
        if (count <= 0) return emptyList()
        if (points.size <= count) return points
        if (count == 1) return points.take(1)
        // Distance from the start at each vertex, so a target distance can be looked up.
        val marks = DoubleArray(points.size)
        for (index in 1 until points.size) {
            marks[index] = marks[index - 1] + GeoUtils.haversineKm(points[index - 1], points[index])
        }
        val total = marks.last()
        // A closed loop or a pile of identical points has no length to spread over.
        if (total <= 0.0) return points.take(count)
        var vertex = 0
        return (0 until count).map { step ->
            val target = total * step / (count - 1)
            // `<=`, not `<`: the last target is exactly the total length, and a strict
            // test would stop one vertex short and never sample the end of the leg.
            while (vertex < points.size - 1 && marks[vertex + 1] <= target) vertex++
            points[vertex]
        }
    }

    /**
     * Cumulative ascent and descent along [points] at [heights], in two passes:
     *
     * 1. Clamp each step to what [MAX_ROAD_GRADE] allows over the distance covered, which
     *    flattens the phantom peaks the DEM puts over tunnels and deep cuttings.
     * 2. Hysteresis: a move counts only once it passes [minRise], and then becomes the new
     *    reference — so undulation does not accumulate.
     *
     * Both are needed. Neither alone gives a figure that survives a change of sampling
     * density, and this one is only stable to about 6%: treat it as an order of magnitude
     * for costing a climb, never as a measurement.
     */
    fun profile(
        points: List<LatLng>,
        heights: List<Double>,
        minRise: Double = MIN_RISE_M,
    ): Climb {
        var anchor = heights.firstOrNull() ?: return Climb(0.0, 0.0)
        var road = anchor
        var ascent = 0.0
        var descent = 0.0
        heights.forEachIndexed { index, height ->
            if (index > 0) {
                val limit = MAX_ROAD_GRADE * GeoUtils.haversineKm(points[index - 1], points[index]) * 1000
                road = height.coerceIn(road - limit, road + limit)
            }
            val delta = road - anchor
            when {
                delta >= minRise -> {
                    ascent += delta
                    anchor = road
                }
                delta <= -minRise -> {
                    descent -= delta
                    anchor = road
                }
            }
        }
        return Climb(ascent, descent)
    }

    /** A stop's own height, when it was measured where the stop currently sits. */
    fun ofStop(stop: Stop): Int? = stop.elevation?.takeIf { it.at == stop.location }?.meters
}
