package dev.simon.camperexperience

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import dev.simon.camperexperience.data.AuthRepository
import dev.simon.camperexperience.data.FirestoreSettingsRepository
import dev.simon.camperexperience.data.FirestoreTripRepository
import dev.simon.camperexperience.data.InMemorySettingsRepository
import dev.simon.camperexperience.data.InMemoryTripRepository
import dev.simon.camperexperience.data.SettingsRepository
import dev.simon.camperexperience.data.TripRepository

/**
 * Hand-rolled DI. When Firebase is configured (google-services.json present at build
 * time -> FirebaseApp.initializeApp succeeds) everything is backed by Firestore with
 * offline persistence; otherwise the app runs fully local with in-memory data.
 */
class AppContainer(firebaseApp: FirebaseApp?) {

    val firebaseEnabled: Boolean = firebaseApp != null

    val authRepository: AuthRepository? = if (firebaseApp != null) {
        AuthRepository(FirebaseAuth.getInstance(), BuildConfig.WEB_CLIENT_ID)
    } else {
        null
    }

    val tripRepository: TripRepository
    val settingsRepository: SettingsRepository

    init {
        if (firebaseApp != null) {
            val db = FirebaseFirestore.getInstance()
            db.firestoreSettings = firestoreSettings {
                setLocalCacheSettings(
                    persistentCacheSettings {
                        setSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    },
                )
            }
            val auth = FirebaseAuth.getInstance()
            tripRepository = FirestoreTripRepository(db, auth)
            settingsRepository = FirestoreSettingsRepository(db, auth)
        } else {
            tripRepository = InMemoryTripRepository()
            settingsRepository = InMemorySettingsRepository()
        }
    }
}

class CamperApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Returns null when no google-services config is baked into the build.
        val firebaseApp = FirebaseApp.initializeApp(this)
        container = AppContainer(firebaseApp)
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
