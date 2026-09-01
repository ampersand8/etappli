package com.nuelto.etappli.ui.settings

import app.cash.turbine.test
import com.nuelto.etappli.data.InMemorySettingsRepository
import com.nuelto.etappli.data.model.LatLng
import kotlinx.coroutines.flow.first
import com.nuelto.etappli.data.model.UserSettings
import com.nuelto.etappli.testutil.FakeAuthRepository
import com.nuelto.etappli.testutil.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepository = InMemorySettingsRepository()

    @Test
    fun `starts with null until settings arrive`() = runTest {
        val vm = SettingsViewModel(settingsRepository, null)
        assertNull(vm.settings.value)
        vm.settings.test {
            assertEquals(UserSettings(), awaitItem())
        }
    }

    @Test
    fun `update writes through to the repository`() = runTest {
        val vm = SettingsViewModel(settingsRepository, null)
        vm.settings.test {
            awaitItem()
            vm.update(UserSettings(currency = "EUR"))
            assertEquals("EUR", awaitItem()!!.currency)
        }
    }

    @Test
    fun `sign out delegates to the auth repository`() {
        val auth = FakeAuthRepository()
        val vm = SettingsViewModel(settingsRepository, auth)
        assertEquals(auth, vm.authRepository)
        vm.signOut()
        assertEquals(1, auth.signOutCalls)
    }

    @Test
    fun `sign out without auth repository is a no-op`() {
        SettingsViewModel(settingsRepository, null).signOut() // must not throw
    }
    @Test
    fun `setting home keeps the name when the pick has none`() = runTest {
        val vm = SettingsViewModel(settingsRepository, null)
        vm.settings.test {
            awaitItem()
            vm.update(UserSettings(homeName = "Luzern"))
            awaitItem()
            vm.setHome(LatLng(47.05, 8.31))
            val stored = awaitItem()!!
            assertEquals(LatLng(47.05, 8.31), stored.homeLocation)
            // A dropped pin has no name; the one already there survives.
            assertEquals("Luzern", stored.homeName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a named pick renames home`() = runTest {
        val vm = SettingsViewModel(settingsRepository, null)
        vm.settings.test {
            awaitItem()
            vm.setHome(LatLng(47.05, 8.31), "Camping Lido")
            val stored = awaitItem()!!
            assertEquals("Camping Lido", stored.homeName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing home drops both the pin and the name`() = runTest {
        val vm = SettingsViewModel(settingsRepository, null)
        vm.settings.test {
            awaitItem()
            vm.setHome(LatLng(47.05, 8.31), "Luzern")
            awaitItem()
            vm.clearHome()
            val stored = awaitItem()!!
            assertNull(stored.homeLocation)
            assertEquals("", stored.homeName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `home cannot be set before the settings have loaded`() = runTest {
        val vm = SettingsViewModel(settingsRepository, null)
        vm.setHome(LatLng(47.05, 8.31))
        vm.clearHome()
        assertNull(settingsRepository.settings().first().homeLocation)
    }

}
