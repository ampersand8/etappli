package com.nuelto.etappli.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nuelto.etappli.data.model.LatLng
import org.junit.Assert.assertNull
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.etappli.BuildConfig
import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.testutil.FakeAuthRepository
import com.nuelto.etappli.ui.map.LocalMapProvider
import com.nuelto.etappli.ui.map.MapProvider
import com.nuelto.etappli.ui.map.PlaceholderMapProvider
import com.nuelto.etappli.testutil.TestCamperApp
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
        compose.onNodeWithText("45.0").performScrollTo().assertIsDisplayed() // campsite per night
        compose.onNodeWithText("15.0").performScrollTo().assertIsDisplayed() // stellplatz per night
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
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        assertEquals(1, auth.signOutCalls)
    }

    @Test
    fun `shows the app version, and no attribution line when there is no map`() {
        setContent()
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Map data and places © Google").assertDoesNotExist()
    }

    @Test
    fun `attribution follows the map provider`() {
        setContent(provider = AttributedMapProvider)
        compose.onNodeWithText("Map data and places © Google").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `back arrow navigates back`() {
        setContent()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    fun `vehicle weight edits are committed while typing`() {
        setContent()
        compose.onNodeWithText("3500.0").performScrollTo().performTextReplacement("3200")
        runBlocking {
            assertEquals(3200.0, settingsRepository.settings().first().vehicleMassKg, 1e-9)
        }
    }

    @Test
    fun `an unusable vehicle weight is not committed`() {
        setContent()
        compose.onNodeWithText("3500.0").performScrollTo().performTextReplacement("0")
        compose.onNodeWithText("0").performTextReplacement("heavy")
        runBlocking {
            assertEquals(3500.0, settingsRepository.settings().first().vehicleMassKg, 1e-9)
        }
    }

    @Test
    fun `explains that the road factor only fills in, and what the weight is for`() {
        setContent()
        compose.onNodeWithText(
            "Distance comes from the road Google routes between stops. This factor " +
                "only fills in for legs with no route yet — no map key, or no signal " +
                "when they were added.",
        ).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Used to price the climbing on a route: lifting the van over a pass costs " +
                "fuel the flat-road consumption above does not account for.",
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `home says whether it is set, and takes a name`() {
        setContent()
        compose.onNodeWithText("Home").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not set. Pick it below and new plans will start and end there.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Clear home").assertDoesNotExist()

        runBlocking {
            settingsRepository.update(
                settingsRepository.settings().first().copy(homeLocation = LatLng(47.05, 8.31)),
            )
        }
        compose.onNodeWithText("Set — new plans start and end here.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Clear home").performScrollTo().performClick()
        runBlocking {
            assertNull(settingsRepository.settings().first().homeLocation)
        }
    }

    @Test
    fun `naming home stores it`() {
        setContent()
        compose.onNodeWithText("Name").performScrollTo().performTextReplacement("Luzern")
        runBlocking {
            assertEquals("Luzern", settingsRepository.settings().first().homeName)
        }
    }
}

/** Stands in for the Google provider, which cannot be composed under Robolectric. */
private object AttributedMapProvider : MapProvider by PlaceholderMapProvider {
    override val attribution = "Map data and places © Google"

}
