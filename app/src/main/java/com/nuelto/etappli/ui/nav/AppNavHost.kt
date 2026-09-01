package com.nuelto.etappli.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.toRoute
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.domain.SharedPlace
import com.nuelto.etappli.ui.map.AllTripsMapScreen
import com.nuelto.etappli.ui.map.LocationPickerScreen
import com.nuelto.etappli.ui.map.LocationSection
import com.nuelto.etappli.ui.settings.SettingsScreen
import com.nuelto.etappli.ui.settings.SettingsViewModel
import com.nuelto.etappli.ui.share.AddToTripScreen
import com.nuelto.etappli.ui.tripdetail.TripDetailScreen
import com.nuelto.etappli.ui.tripedit.StopEditScreen
import com.nuelto.etappli.ui.tripedit.StopEditViewModel
import com.nuelto.etappli.ui.tripedit.TripEditScreen
import com.nuelto.etappli.ui.triplist.TripListScreen

/** [pending] is a place shared into the app, if one is waiting (see domain/ShareIntake). */
@Composable
fun AppNavHost(pending: SharedPlace? = null, onPendingConsumed: () -> Unit = {}) {
    val navController = rememberNavController()

    // Routed exactly once: consuming empties the intake, and from here the place rides
    // the back stack, so neither a rotation nor process death replays or loses it.
    LaunchedEffect(pending) {
        pending?.let {
            navController.navigate(
                AddToTripRoute(
                    it.name, it.location?.latitude, it.location?.longitude,
                    it.placeId, it.link, it.approximate,
                ),
            )
            onPendingConsumed()
        }
    }

    NavHost(navController = navController, startDestination = TripListRoute) {
        composable<TripListRoute> {
            TripListScreen(
                onTripClick = { tripId -> navController.navigate(TripDetailRoute(tripId)) },
                onAddTrip = { planned -> navController.navigate(TripEditRoute(planned = planned)) },
                onOpenMap = { navController.navigate(AllTripsMapRoute()) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<TripDetailRoute> {
            TripDetailScreen(
                onBack = { navController.popBackStack() },
                onEditTrip = { tripId -> navController.navigate(TripEditRoute(tripId)) },
                onAddStop = { tripId, insertBefore ->
                    navController.navigate(StopEditRoute(tripId, insertBefore = insertBefore))
                },
                onEditStop = { tripId, stopId -> navController.navigate(StopEditRoute(tripId, stopId)) },
                onOpenTripMap = { tripId -> navController.navigate(AllTripsMapRoute(tripId)) },
                // Start-as-copy and plan-again land on the freshly created trip.
                onOpenTrip = { tripId -> navController.navigate(TripDetailRoute(tripId)) },
            )
        }
        composable<TripEditRoute> {
            TripEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { tripId, isNew ->
                    // A tour planned to hold a shared place goes back to the chooser,
                    // which now lists it; anything else opens the new trip.
                    val fromChooser = navController.previousBackStackEntry
                        ?.destination?.hasRoute<AddToTripRoute>() == true
                    navController.popBackStack()
                    if (isNew && !fromChooser) navController.navigate(TripDetailRoute(tripId))
                },
            )
        }
        composable<AddToTripRoute> {
            AddToTripScreen(
                onCancel = { navController.popBackStack() },
                onPlanTrip = { navController.navigate(TripEditRoute(planned = true)) },
                onAddTo = navController::addToTrip,
            )
        }
        composable<StopEditRoute> { backStackEntry ->
            val viewModel: StopEditViewModel = viewModel(factory = StopEditViewModel.Factory)
            // Result from the location picker arrives via this entry's SavedStateHandle.
            val picked by backStackEntry.savedStateHandle
                .getStateFlow<DoubleArray?>(PICKED_LOCATION_KEY, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(picked) {
                picked?.let { (lat, lon) ->
                    // A place searched on the map comes back named; a crosshair pick doesn't.
                    val place = backStackEntry.savedStateHandle.get<Array<String>>(PICKED_PLACE_KEY)
                    viewModel.setPickedLocation(
                        LatLng(lat, lon),
                        place?.getOrNull(0).orEmpty(),
                        place?.getOrNull(1).orEmpty(),
                        place?.getOrNull(2).orEmpty(),
                    )
                    backStackEntry.savedStateHandle[PICKED_LOCATION_KEY] = null
                    backStackEntry.savedStateHandle[PICKED_PLACE_KEY] = null
                }
            }
            val stopEditState by viewModel.uiState.collectAsStateWithLifecycle()
            StopEditScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
                locationSection = {
                    LocationSection(
                        onLocationChange = viewModel::setLocation,
                        onPickOnMap = {
                            // Opens on this stop, or failing that near the one before it.
                            val start = viewModel.uiState.value.pickerStart
                            navController.navigate(
                                LocationPickerRoute(
                                    start?.latitude,
                                    start?.longitude,
                                    stopEditState.kind.name,
                                ),
                            )
                        },
                        autoLocate = stopEditState.autoLocatePending,
                        onAutoLocateHandled = viewModel::autoLocateHandled,
                        onAutoLocated = viewModel::setAutoLocation,
                        shareUrl = stopEditState.shareUrl,
                    )
                },
            )
        }
        composable<AllTripsMapRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AllTripsMapRoute>()
            AllTripsMapScreen(
                tripId = route.tripId,
                onBack = { navController.popBackStack() },
                onOpenTrip = { tripId -> navController.navigate(TripDetailRoute(tripId)) },
            )
        }
        composable<LocationPickerRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<LocationPickerRoute>()
            LocationPickerScreen(
                initial = if (route.lat != null && route.lon != null) LatLng(route.lat, route.lon) else null,
                prefer = route.kind?.let { runCatching { StopKind.valueOf(it) }.getOrNull() },
                onPicked = { location, place ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set(
                            PICKED_PLACE_KEY,
                            arrayOf(place?.name.orEmpty(), place?.label.orEmpty(), place?.id.orEmpty()),
                        )
                        set(PICKED_LOCATION_KEY, doubleArrayOf(location.latitude, location.longitude))
                    }
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> { backStackEntry ->
            val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            // Home comes back from the picker the same way a stop's location does.
            val picked by backStackEntry.savedStateHandle
                .getStateFlow<DoubleArray?>(PICKED_LOCATION_KEY, null)
                .collectAsStateWithLifecycle()
            LaunchedEffect(picked) {
                picked?.let { (lat, lon) ->
                    val place = backStackEntry.savedStateHandle.get<Array<String>>(PICKED_PLACE_KEY)
                    viewModel.setHome(LatLng(lat, lon), place?.getOrNull(0).orEmpty())
                    backStackEntry.savedStateHandle[PICKED_LOCATION_KEY] = null
                    backStackEntry.savedStateHandle[PICKED_PLACE_KEY] = null
                }
            }
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            SettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
                locationSection = {
                    LocationSection(
                        onLocationChange = { viewModel.setHome(it) },
                        onPickOnMap = {
                            val start = settings?.homeLocation
                            navController.navigate(
                                LocationPickerRoute(
                                    start?.latitude,
                                    start?.longitude,
                                    StopKind.HOME.name,
                                ),
                            )
                        },
                    )
                },
            )
        }
    }
}

/** The trip's timeline goes under the editor, so saving lands where the stop landed. */
private fun NavController.addToTrip(tripId: String, place: SharedPlace) {
    navigate(TripDetailRoute(tripId)) { popUpTo<AddToTripRoute> { inclusive = true } }
    navigate(
        StopEditRoute(
            tripId,
            lat = place.location?.latitude,
            lon = place.location?.longitude,
            placeName = place.name.ifBlank { null },
            placeId = place.placeId.ifBlank { null },
            fromShare = true,
        ),
    )
}

private const val PICKED_LOCATION_KEY = "picked_location"
private const val PICKED_PLACE_KEY = "picked_place"

private operator fun DoubleArray.component1() = this[0]

private operator fun DoubleArray.component2() = this[1]
