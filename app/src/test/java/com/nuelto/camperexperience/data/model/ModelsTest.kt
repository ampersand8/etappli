package com.nuelto.camperexperience.data.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {

    @Test
    fun `legacy trips with a past end date are done`() {
        assertEquals(TripStatus.DONE, legacyTripStatus(LocalDate.now().minusDays(1)))
    }

    @Test
    fun `legacy trips still open or ending today or later are active, never planned`() {
        assertEquals(TripStatus.ACTIVE, legacyTripStatus(null))
        assertEquals(TripStatus.ACTIVE, legacyTripStatus(LocalDate.now()))
        assertEquals(TripStatus.ACTIVE, legacyTripStatus(LocalDate.now().plusDays(7)))
    }
}
