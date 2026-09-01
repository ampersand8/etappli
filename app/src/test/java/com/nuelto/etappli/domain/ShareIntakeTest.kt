package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareIntakeTest {

    private val place = SharedPlace("Camping Grimselblick", LatLng(46.56, 8.33))

    @Test
    fun `a shared place waits in the intake until it is consumed`() {
        val intake = ShareIntake()
        assertNull(intake.pending.value)

        intake.offer(place)
        assertEquals(place, intake.pending.value)

        intake.consume()
        assertNull(intake.pending.value)
    }

    @Test
    fun `the same place shared twice lands twice`() {
        val intake = ShareIntake()
        intake.offer(place)
        intake.consume()
        intake.offer(place)
        assertEquals(place, intake.pending.value)
    }

    @Test
    fun `an unreadable share offers nothing`() {
        val intake = ShareIntake()
        intake.offer(place)
        intake.offer(null)
        assertNull(intake.pending.value)
    }
}
