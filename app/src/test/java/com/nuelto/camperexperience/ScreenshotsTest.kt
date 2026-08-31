package com.nuelto.camperexperience

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nuelto.camperexperience.data.InMemorySettingsRepository
import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.testutil.TestCamperApp
import com.nuelto.camperexperience.ui.map.LocalMapProvider
import com.nuelto.camperexperience.ui.map.PlaceholderMapProvider
import com.nuelto.camperexperience.ui.settings.SettingsScreen
import com.nuelto.camperexperience.ui.settings.SettingsViewModel
import com.nuelto.camperexperience.ui.theme.CamperTheme
import com.nuelto.camperexperience.ui.tripdetail.TripDetailScreen
import com.nuelto.camperexperience.ui.tripdetail.TripDetailViewModel
import com.nuelto.camperexperience.ui.triplist.TripListScreen
import com.nuelto.camperexperience.ui.triplist.TripListViewModel
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Not a regression test: renders the main screens with the demo seed data and writes
 * PNGs (light + dark) to app/build/screenshots/. CI uploads them as a versioned
 * artifact on every push to main.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestCamperApp::class)
class ScreenshotsTest {

    @get:Rule
    val compose = createComposeRule()

    private val tripRepository = InMemoryTripRepository() // seeded demo trips
    private val settingsRepository = InMemorySettingsRepository()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        val dark = mutableStateOf(false)
        compose.setContent {
            val isDark by dark
            CompositionLocalProvider(LocalMapProvider provides PlaceholderMapProvider) {
                CamperTheme(darkTheme = isDark) { content() }
            }
        }
        save("$name-light")
        dark.value = true
        save("$name-dark")
    }

    private fun save(fileName: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val file = File("build/screenshots/$fileName.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun tripList() = shoot("trip-list") {
        TripListScreen(
            onTripClick = {}, onAddTrip = { _ -> }, onOpenMap = {}, onOpenSettings = {},
            viewModel = TripListViewModel(tripRepository, settingsRepository),
        )
    }

    @Test
    fun tripDetail() = shoot("trip-detail") {
        TripDetailScreen(
            onBack = {}, onEditTrip = {}, onAddStop = { _, _ -> }, onEditStop = { _, _ -> },
            onOpenTripMap = {}, onOpenTrip = {},
            viewModel = TripDetailViewModel(
                SavedStateHandle(mapOf("tripId" to "demo-provence")),
                tripRepository,
                settingsRepository,
            ),
        )
    }

    @Test
    fun activeTrip() = shoot("active-trip") {
        TripDetailScreen(
            onBack = {}, onEditTrip = {}, onAddStop = { _, _ -> }, onEditStop = { _, _ -> },
            onOpenTripMap = {}, onOpenTrip = {},
            viewModel = TripDetailViewModel(
                SavedStateHandle(mapOf("tripId" to "demo-vierwald")),
                tripRepository,
                settingsRepository,
            ),
        )
    }

    @Test
    fun plannedTour() = shoot("planned-tour") {
        TripDetailScreen(
            onBack = {}, onEditTrip = {}, onAddStop = { _, _ -> }, onEditStop = { _, _ -> },
            onOpenTripMap = {}, onOpenTrip = {},
            viewModel = TripDetailViewModel(
                SavedStateHandle(mapOf("tripId" to "demo-jura")),
                tripRepository,
                settingsRepository,
            ),
        )
    }

    @Test
    fun settings() = shoot("settings") {
        SettingsScreen(
            onBack = {},
            viewModel = SettingsViewModel(settingsRepository, authRepository = null),
        )
    }
}
