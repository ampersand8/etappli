package com.nuelto.camperexperience.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.testutil.TestCamperApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class StatusBadgeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `display names cover the whole lifecycle`() {
        assertEquals("Planned", TripStatus.PLANNED.displayName)
        assertEquals("On the road", TripStatus.ACTIVE.displayName)
        assertEquals("Done", TripStatus.DONE.displayName)
    }

    @Test
    fun `badge renders the status name`() {
        compose.setContent { StatusBadge(TripStatus.PLANNED) }
        compose.onNodeWithText("Planned").assertIsDisplayed()
    }
}
