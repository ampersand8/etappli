package com.nuelto.etappli

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.ui.map.MapProvider
import com.nuelto.etappli.data.FirebaseAuthRepository
import com.nuelto.etappli.data.FirestoreSettingsRepository
import com.nuelto.etappli.data.FirestoreTripRepository

/** Firestore-backed container, or null when no google-services config is baked into the build. */
fun firebaseContainer(
    app: Application,
    mapProvider: MapProvider,
    currentLocation: suspend () -> LatLng? = { null },
    expandShareLink: suspend (String) -> String? = { null },
    startTracking: () -> Unit = {},
): AppContainer? {
    val firebaseApp = FirebaseApp.initializeApp(app) ?: return null
    val db = FirebaseFirestore.getInstance()
    db.firestoreSettings = firestoreSettings {
        setLocalCacheSettings(
            persistentCacheSettings {
                setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            },
        )
    }
    val auth = FirebaseAuth.getInstance()
    return AppContainer(
        mapProvider = mapProvider,
        authRepository = FirebaseAuthRepository(auth, BuildConfig.WEB_CLIENT_ID),
        tripRepository = FirestoreTripRepository(db, auth),
        settingsRepository = FirestoreSettingsRepository(db, auth),
        currentLocation = currentLocation,
        expandShareLink = expandShareLink,
        startTracking = startTracking,
    )
}
