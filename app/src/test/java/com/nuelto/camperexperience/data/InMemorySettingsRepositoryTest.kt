package com.nuelto.camperexperience.data

import com.nuelto.camperexperience.data.model.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemorySettingsRepositoryTest {

    @Test
    fun `starts with defaults`() = runTest {
        assertEquals(UserSettings(), InMemorySettingsRepository().settings().first())
        assertEquals("CHF", UserSettings().currency)
    }

    @Test
    fun `update replaces the settings`() = runTest {
        val repo = InMemorySettingsRepository()
        val changed = UserSettings(
            currency = "EUR",
            fuelConsumptionL100km = 12.5,
            fuelPricePerLiter = 1.65,
            roadDistanceFactor = 1.4,
        )
        repo.update(changed)
        assertEquals(changed, repo.settings().first())
    }
}
