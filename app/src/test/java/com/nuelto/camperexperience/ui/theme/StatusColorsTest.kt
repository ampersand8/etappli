package com.nuelto.camperexperience.ui.theme

import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.data.model.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusColorsTest {

    @Test
    fun `blue is planned, green is active, grey is done`() {
        assertEquals(PlannedBlue, statusColor(TripStatus.PLANNED))
        assertEquals(ActiveGreen, statusColor(TripStatus.ACTIVE))
        assertEquals(DoneGrey, statusColor(TripStatus.DONE))
    }

    @Test
    fun `the current stop is green regardless of state`() {
        assertEquals(ActiveGreen, stopColor(StopState.PLANNED, isCurrent = true))
    }

    @Test
    fun `non-current stops are blue when upcoming, grey otherwise`() {
        assertEquals(PlannedBlue, stopColor(StopState.PLANNED, isCurrent = false))
        assertEquals(DoneGrey, stopColor(StopState.DONE, isCurrent = false))
        assertEquals(DoneGrey, stopColor(StopState.SKIPPED, isCurrent = false))
    }
}
