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
import com.nuelto.camperexperience.ui.map.MapLibreProvider
import com.nuelto.camperexperience.ui.map.MapProvider

/**
 * Hand-rolled DI. When Firebase is configured (google-services.json present at build
 * time) everything is backed by Firestore with offline persistence; otherwise the app
 * runs fully local with in-memory data. Tests inject their own container.
 */
class AppContainer(
    val authRepository: AuthRepository?,
    val tripRepository: TripRepository,
    val settingsRepository: SettingsRepository,
    // Independent of the Firebase switch: a Firestore install with no Maps key still
    // gets MapLibre, and a local-only install with a key still gets Google Maps.
    val mapProvider: MapProvider = MapLibreProvider,
) {
    val firebaseEnabled: Boolean get() = authRepository != null

    companion object {
        fun inMemory(mapProvider: MapProvider = MapLibreProvider) =
            AppContainer(null, InMemoryTripRepository(), InMemorySettingsRepository(), mapProvider)
    }
}

open class CamperApp : Application() {
    lateinit var container: AppContainer
        internal set // tests swap in their own repositories

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
    }

    protected open fun createContainer(): AppContainer {
        // Two independent switches: Firebase decides the store, the key decides the map.
        val map = googleMapProvider(this) ?: MapLibreProvider
        return firebaseContainer(this, map) ?: AppContainer.inMemory(map)
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
