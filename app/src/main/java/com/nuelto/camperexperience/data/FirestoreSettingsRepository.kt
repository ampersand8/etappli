package com.nuelto.camperexperience.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.nuelto.camperexperience.data.model.UserSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FirestoreSettingsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : SettingsRepository {

    private fun doc() = db.collection("users").document(requireNotNull(auth.currentUser).uid)

    override fun settings(): Flow<UserSettings> = callbackFlow {
        val registration = doc().addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else {
                val defaults = UserSettings()
                trySend(
                    UserSettings(
                        currency = snapshot?.getString("currency") ?: defaults.currency,
                        fuelConsumptionL100km = snapshot?.getDouble("fuelConsumptionL100km")
                            ?: defaults.fuelConsumptionL100km,
                        fuelPricePerLiter = snapshot?.getDouble("fuelPricePerLiter")
                            ?: defaults.fuelPricePerLiter,
                        roadDistanceFactor = snapshot?.getDouble("roadDistanceFactor")
                            ?: defaults.roadDistanceFactor,
                        campsitePerNight = snapshot?.getDouble("campsitePerNight")
                            ?: defaults.campsitePerNight,
                        stellplatzPerNight = snapshot?.getDouble("stellplatzPerNight")
                            ?: defaults.stellplatzPerNight,
                    ),
                )
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun update(settings: UserSettings) {
        doc().set(
            mapOf(
                "currency" to settings.currency,
                "fuelConsumptionL100km" to settings.fuelConsumptionL100km,
                "fuelPricePerLiter" to settings.fuelPricePerLiter,
                "roadDistanceFactor" to settings.roadDistanceFactor,
                "campsitePerNight" to settings.campsitePerNight,
                "stellplatzPerNight" to settings.stellplatzPerNight,
            ),
        )
    }
}
