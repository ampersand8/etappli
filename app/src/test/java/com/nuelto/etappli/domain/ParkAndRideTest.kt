package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkAndRideTest {

    private val valley = LatLng(46.3566, 8.046)
    private val town = LatLng(46.32, 7.99)
    private val city = LatLng(46.95, 7.44)

    private fun walk(polyline: String = "", meters: Int = 100, seconds: Int = 80) =
        TransitStep(polyline, meters, seconds)

    private fun ride(vehicle: String, boards: LatLng?, polyline: String = "", meters: Int = 1_000, seconds: Int = 600) =
        TransitStep(polyline, meters, seconds, vehicle, boards)

    @Test
    fun `the last ride's boarding point comes first, and only rides can be parked at`() {
        val steps = listOf(
            walk(), ride("LONG_DISTANCE_TRAIN", city), walk(), ride("HEAVY_RAIL", town), ride("GONDOLA_LIFT", valley), walk(),
        )
        val candidates = ParkAndRide.candidates(steps)
        assertEquals(listOf(valley, town, city), candidates.map { it.parked })
        assertEquals(steps.subList(4, 6), candidates[0].steps)
        assertEquals(steps.subList(3, 6), candidates[1].steps)
        assertEquals(steps.subList(1, 6), candidates[2].steps)
    }

    @Test
    fun `no more than three rides back are tried, and a ride that does not say where it boards is skipped`() {
        val steps = listOf(
            ride("BUS", LatLng(1.0, 1.0)),
            ride("BUS", LatLng(2.0, 2.0)),
            ride("BUS", LatLng(3.0, 3.0)),
            ride("BUS", null),
            ride("BUS", LatLng(4.0, 4.0)),
        )
        assertEquals(
            listOf(LatLng(4.0, 4.0), LatLng(3.0, 3.0), LatLng(2.0, 2.0)),
            ParkAndRide.candidates(steps).map { it.parked },
        )
        assertTrue(ParkAndRide.candidates(listOf(walk(), walk())).isEmpty())
    }

    @Test
    fun `a ride joins its steps into one line, one sum, and the vehicle in words`() {
        val top = LatLng(46.378, 8.0332)
        val hut = LatLng(46.3797, 8.0433)
        val candidate = ParkAndRide.Candidate(
            valley,
            listOf(
                ride("GONDOLA_LIFT", valley, Polyline.encode(listOf(valley, top)), 2_572, 720),
                walk(Polyline.encode(listOf(top, hut)), 336, 246),
            ),
        )
        val ride = ParkAndRide.ride(candidate)
        assertEquals(valley, ride.parked)
        assertEquals(listOf(valley, top, top, hut), Polyline.decode(ride.polyline))
        assertEquals(2_908, ride.distanceMeters)
        assertEquals(966, ride.durationSeconds)
        assertEquals("cable car", ride.mode)
    }

    @Test
    fun `different vehicles are named once each, in riding order`() {
        val candidate = ParkAndRide.Candidate(
            town,
            listOf(ride("HEAVY_RAIL", town), walk(), ride("BUS", city), ride("INTERCITY_BUS", city)),
        )
        assertEquals("train + bus", ParkAndRide.ride(candidate).mode)
    }

    @Test
    fun `google's vehicle types in plain words`() {
        val names = mapOf(
            "GONDOLA_LIFT" to "cable car", "CABLE_CAR" to "cable car", "FUNICULAR" to "funicular",
            "FERRY" to "ferry", "TRAM" to "tram", "BUS" to "bus", "INTERCITY_BUS" to "bus",
            "TROLLEYBUS" to "bus", "SHARE_TAXI" to "bus", "HEAVY_RAIL" to "train", "RAIL" to "train",
            "COMMUTER_TRAIN" to "train", "LONG_DISTANCE_TRAIN" to "train", "HIGH_SPEED_TRAIN" to "train",
            "METRO_RAIL" to "train", "MONORAIL" to "train", "SUBWAY" to "train", "OTHER" to "transit",
        )
        names.forEach { (type, name) -> assertEquals(type, name, ParkAndRide.vehicleName(type)) }
    }
}
