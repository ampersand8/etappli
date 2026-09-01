package com.nuelto.etappli

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.testutil.TestCamperApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun `launches into the trip list with seeded demo trips`() {
        compose.onNodeWithText("Trips").assertIsDisplayed()
        compose.onNodeWithText("Provence").assertIsDisplayed()
        compose.onNodeWithText("Schwarzwald").assertIsDisplayed()
    }
}
