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
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.domain.ShareIntake
import com.nuelto.camperexperience.location.LocationProvider
import com.nuelto.camperexperience.location.ShareLinkResolver
import com.nuelto.camperexperience.ui.map.PlaceholderMapProvider
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
    // runs, and a local-only install with a key still gets Google Maps.
    val mapProvider: MapProvider = PlaceholderMapProvider,
    // One-shot GPS fix. The caller must hold ACCESS_FINE_LOCATION; the screens ask, and
    // this returns null when they have not.
    val currentLocation: suspend () -> LatLng? = { null },
    // Where a shared place waits for a NavHost to exist. See domain/ShareIntake.
    val shareIntake: ShareIntake = ShareIntake(),
    // Follows a shared short link to the Maps URL behind it; null without a connection.
    val expandShareLink: suspend (String) -> String? = { null },
) {
    val firebaseEnabled: Boolean get() = authRepository != null

    companion object {
        fun inMemory(
            mapProvider: MapProvider = PlaceholderMapProvider,
            currentLocation: suspend () -> LatLng? = { null },
            expandShareLink: suspend (String) -> String? = { null },
        ) = AppContainer(
            null, InMemoryTripRepository(), InMemorySettingsRepository(), mapProvider, currentLocation,
            expandShareLink = expandShareLink,
        )
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
        // No key: the app runs, the map is simply blank.
        val map = googleMapProvider(this) ?: PlaceholderMapProvider
        val locate: suspend () -> LatLng? = { LocationProvider(this).currentLocation() }
        val expand: suspend (String) -> String? = { ShareLinkResolver.expand(it) }
        return firebaseContainer(this, map, locate, expand) ?: AppContainer.inMemory(map, locate, expand)
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
