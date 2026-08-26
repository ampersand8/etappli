package dev.simon.camperexperience

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.simon.camperexperience.data.InMemorySettingsRepository
import dev.simon.camperexperience.data.InMemoryTripRepository
import dev.simon.camperexperience.data.SettingsRepository
import dev.simon.camperexperience.data.TripRepository

/** Hand-rolled DI: one container on the Application, ViewModels pull from it. */
class AppContainer {
    val tripRepository: TripRepository = InMemoryTripRepository()
    val settingsRepository: SettingsRepository = InMemorySettingsRepository()
}

class CamperApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}

val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as CamperApp).container

/** Shorthand for building a ViewModel factory that has access to the AppContainer. */
inline fun <reified VM : ViewModel> containerViewModelFactory(
    crossinline create: CreationExtras.(AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(appContainer) }
}
