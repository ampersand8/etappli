package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveDriveTest {

    private val here = LatLng(46.8709, 8.2492)
    private val there = LatLng(46.1591, 8.7853)

    /** [meters] north of [from], close enough that a degree of latitude is a straight ruler. */
    private fun north(from: LatLng, meters: Double) =
        LatLng(from.latitude + meters / 111_195.0, from.longitude)

    private fun drive(from: LatLng, to: LatLng) =
        DriveFromHere(from, to, distanceMeters = 90_000, durationSeconds = 5_400)

    @Test
    fun `you have arrived just inside the threshold, and not just outside it`() {
        assertTrue(LiveDrive.arrived(there, north(there, 149.0)))
        assertFalse(LiveDrive.arrived(there, north(there, 151.0)))
        assertEquals(150.0, LiveDrive.ARRIVED_WITHIN_METERS, 1e-9)
    }

    @Test
    fun `standing on the stop counts as arrived`() {
        assertTrue(LiveDrive.arrived(there, there))
    }

    @Test
    fun `nothing held means fetch`() {
        assertTrue(LiveDrive.needsFetch(null, here, there))
    }

    @Test
    fun `a different destination is fetched however little you moved`() {
        val held = drive(here, there)
        // One metre from the recorded fix, but heading somewhere else now.
        assertTrue(LiveDrive.needsFetch(held, north(here, 1.0), LatLng(47.0, 8.0)))
    }

    @Test
    fun `staying put does not spend a route`() {
        val held = drive(here, there)
        assertFalse(LiveDrive.needsFetch(held, here, there))
        assertFalse(LiveDrive.needsFetch(held, north(here, 1_999.0), there))
    }

    @Test
    fun `moving the refresh distance fetches again`() {
        val held = drive(here, there)
        assertTrue(LiveDrive.needsFetch(held, north(here, 2_001.0), there))
        assertEquals(2_000.0, LiveDrive.REFRESH_AFTER_METERS, 1e-9)
    }
}
