package com.nuelto.etappli.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.UserSettings
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
                        vehicleMassKg = snapshot?.getDouble("vehicleMassKg")
                            ?: defaults.vehicleMassKg,
                        homeName = snapshot?.getString("homeName") ?: defaults.homeName,
                        homeLocation = snapshot?.getGeoPoint("homeLocation")
                            ?.let { LatLng(it.latitude, it.longitude) },
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
                "vehicleMassKg" to settings.vehicleMassKg,
                "homeName" to settings.homeName,
                "homeLocation" to settings.homeLocation?.let { GeoPoint(it.latitude, it.longitude) },
                "campsitePerNight" to settings.campsitePerNight,
                "stellplatzPerNight" to settings.stellplatzPerNight,
            ),
        )
    }
}
