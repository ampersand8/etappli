package com.nuelto.camperexperience

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nuelto.camperexperience.data.AuthRepository
import com.nuelto.camperexperience.data.InMemorySettingsRepository
import com.nuelto.camperexperience.data.InMemoryTripRepository
import com.nuelto.camperexperience.data.SettingsRepository
import com.nuelto.camperexperience.data.TripRepository

/**
 * Hand-rolled DI. When Firebase is configured (google-services.json present at build
 * time) everything is backed by Firestore with offline persistence; otherwise the app
 * runs fully local with in-memory data. Tests inject their own container.
 */
class AppContainer(
    val authRepository: AuthRepository?,
    val tripRepository: TripRepository,
    val settingsRepository: SettingsRepository,
) {
    val firebaseEnabled: Boolean get() = authRepository != null

    companion object {
        fun inMemory() = AppContainer(null, InMemoryTripRepository(), InMemorySettingsRepository())
    }
}

open class CamperApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    protected open fun createContainer(): AppContainer =
        firebaseContainer(this) ?: AppContainer.inMemory()
}

val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as CamperApp).container

/** Shorthand for building a ViewModel factory that has access to the AppContainer. */
inline fun <reified VM : ViewModel> containerViewModelFactory(
    crossinline create: CreationExtras.(AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(appContainer) }
}
