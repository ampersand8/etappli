package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.TransitRide
import com.nuelto.etappli.data.model.TravelMode

/**
 * Drive as far as the road goes, then ride: how a stop no road reaches — Riederalp, up its
 * cable car — still gets a leg. Google routes by road and by public transport separately
 * and has no park-and-ride mode, so the two are composed here: the transit route to the
 * stop says where its last ride boards, and that is where the vehicle is left. The drives
 * to and from that spot are then ordinary road legs (RouteRefresher).
 */
object ParkAndRide {

    /** Rides back from the stop to try leaving the vehicle at, before giving it up as unreachable. */
    const val MAX_ATTEMPTS = 3

    /** Where the vehicle could be left, and the rest of the way from there. */
    data class Candidate(val parked: LatLng, val steps: List<TransitStep>)

    /**
     * Nearest the stop first: the last ride's boarding point is the valley station, and
     * each earlier one is only for when no road reaches the one after it — Zermatt's
     * shuttle bus boards in a town no car may enter; the train before it boards in Täsch.
     */
    fun candidates(steps: List<TransitStep>): List<Candidate> =
        steps.indices.reversed()
            .filter { steps[it].departure != null }
            .take(MAX_ATTEMPTS)
            .map { Candidate(steps[it].departure!!, steps.subList(it, steps.size)) }

    /** The rest of the way as one ride: joined geometry, summed numbers, the vehicles boarded. */
    fun ride(candidate: Candidate): TransitRide = TransitRide(
        parked = candidate.parked,
        polyline = Polyline.encode(candidate.steps.flatMap { Polyline.decode(it.polyline) }),
        distanceMeters = candidate.steps.sumOf { it.distanceMeters },
        durationSeconds = candidate.steps.sumOf { it.durationSeconds },
        modes = candidate.steps.mapNotNull { it.vehicle }.map(::mode).distinct(),
    )

    /** Google's vehicle types as what you board. */
    fun mode(type: String): TravelMode = when {
        type == "GONDOLA_LIFT" || type == "CABLE_CAR" -> TravelMode.CABLE_CAR
        type == "FUNICULAR" -> TravelMode.FUNICULAR
        type == "FERRY" -> TravelMode.FERRY
        type == "TRAM" -> TravelMode.TRAM
        "BUS" in type || type == "SHARE_TAXI" -> TravelMode.BUS
        "RAIL" in type || "TRAIN" in type || type == "SUBWAY" -> TravelMode.TRAIN
        else -> TravelMode.TRANSIT
    }
}
