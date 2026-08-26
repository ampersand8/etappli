package dev.simon.camperexperience.data

import dev.simon.camperexperience.data.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface SettingsRepository {
    fun settings(): Flow<UserSettings>
    suspend fun update(settings: UserSettings)
}

class InMemorySettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(UserSettings())
    override fun settings(): Flow<UserSettings> = state
    override suspend fun update(settings: UserSettings) {
        state.value = settings
    }
}
