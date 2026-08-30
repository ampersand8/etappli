package com.nuelto.camperexperience.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.BuildConfig
import com.nuelto.camperexperience.data.InMemorySettingsRepository
import com.nuelto.camperexperience.testutil.FakeAuthRepository
import com.nuelto.camperexperience.ui.map.LocalMapProvider
import com.nuelto.camperexperience.ui.map.MapProvider
import com.nuelto.camperexperience.ui.map.PlaceholderMapProvider
import com.nuelto.camperexperience.testutil.TestCamperApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val settingsRepository = InMemorySettingsRepository()
    private val events = mutableListOf<String>()

    private fun setContent(auth: FakeAuthRepository? = null, provider: MapProvider? = null) {
        val viewModel = SettingsViewModel(settingsRepository, auth)
        compose.setContent {
            if (provider == null) {
                SettingsScreen(onBack = { events += "back" }, viewModel = viewModel)
            } else {
                CompositionLocalProvider(LocalMapProvider provides provider) {
                    SettingsScreen(onBack = { events += "back" }, viewModel = viewModel)
                }
            }
        }
    }

    @Test
    fun `shows the stored defaults`() {
        setContent()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("CHF").assertIsDisplayed()
        compose.onNodeWithText("10.0").assertIsDisplayed() // consumption
        compose.onNodeWithText("1.8").assertIsDisplayed() // fuel price
        compose.onNodeWithText("1.25").assertIsDisplayed() // road factor
        compose.onNodeWithText("45.0").assertIsDisplayed() // campsite per night
        compose.onNodeWithText("15.0").assertIsDisplayed() // stellplatz per night
    }

    @Test
    fun `camping rate edits are committed while typing`() {
        setContent()
        compose.onNodeWithText("45.0").performTextReplacement("50")
        compose.onNodeWithText("15.0").performTextReplacement("18")
        runBlocking {
            assertEquals(50.0, settingsRepository.settings().first().campsitePerNight, 1e-9)
            assertEquals(18.0, settingsRepository.settings().first().stellplatzPerNight, 1e-9)
        }
    }

    @Test
    fun `picking a currency persists it`() {
        setContent()
        compose.onNodeWithText("CHF").performClick()
        compose.onNodeWithText("EUR").performClick()
        runBlocking { assertEquals("EUR", settingsRepository.settings().first().currency) }
    }

    @Test
    fun `valid consumption edits are committed while typing`() {
        setContent()
        compose.onNodeWithText("10.0").performTextReplacement("12.5")
        runBlocking {
            assertEquals(12.5, settingsRepository.settings().first().fuelConsumptionL100km, 1e-9)
        }
    }

    @Test
    fun `invalid or non-positive numbers are not committed`() {
        setContent()
        compose.onNodeWithText("10.0").performTextReplacement("abc")
        compose.onNodeWithText("1.25").performTextReplacement("0")
        runBlocking {
            assertEquals(10.0, settingsRepository.settings().first().fuelConsumptionL100km, 1e-9)
            assertEquals(1.25, settingsRepository.settings().first().roadDistanceFactor, 1e-9)
        }
    }

    @Test
    fun `sign out is hidden in local mode`() {
        setContent()
        compose.onNodeWithText("Sign out").assertDoesNotExist()
    }

    @Test
    fun `sign out delegates to the auth repository`() {
        val auth = FakeAuthRepository()
        setContent(auth)
        compose.onNodeWithText("Sign out").performClick()
        assertEquals(1, auth.signOutCalls)
    }

    @Test
    fun `shows the app version, and no attribution line when there is no map`() {
        setContent()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithText("Map data and places © Google").assertDoesNotExist()
    }

    @Test
    fun `attribution follows the map provider`() {
        setContent(provider = AttributedMapProvider)
        compose.onNodeWithText("Map data and places © Google").assertIsDisplayed()
    }

    @Test
    fun `back arrow navigates back`() {
        setContent()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }
}

/** Stands in for the Google provider, which cannot be composed under Robolectric. */
private object AttributedMapProvider : MapProvider by PlaceholderMapProvider {
    override val attribution = "Map data and places © Google"
}
