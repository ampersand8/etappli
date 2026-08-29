package com.nuelto.camperexperience.ui.settings

import app.cash.turbine.test
import com.nuelto.camperexperience.data.InMemorySettingsRepository
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.testutil.FakeAuthRepository
import com.nuelto.camperexperience.testutil.MainDispatcherRule
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
}
