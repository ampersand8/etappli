package com.nuelto.camperexperience.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.ui.map.AllTripsMapScreen
import com.nuelto.camperexperience.ui.map.LocationPickerScreen
import com.nuelto.camperexperience.ui.map.LocationSection
import com.nuelto.camperexperience.ui.settings.SettingsScreen
import com.nuelto.camperexperience.ui.tripdetail.TripDetailScreen
import com.nuelto.camperexperience.ui.tripedit.StopEditScreen
import com.nuelto.camperexperience.ui.tripedit.StopEditViewModel
import com.nuelto.camperexperience.ui.tripedit.TripEditScreen
import com.nuelto.camperexperience.ui.triplist.TripListScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = TripListRoute) {
        composable<TripListRoute> {
            TripListScreen(
                onTripClick = { tripId -> navController.navigate(TripDetailRoute(tripId)) },
                onAddTrip = { navController.navigate(TripEditRoute()) },
                onOpenMap = { navController.navigate(AllTripsMapRoute()) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<TripDetailRoute> {
            TripDetailScreen(
                onBack = { navController.popBackStack() },
                onEditTrip = { tripId -> navController.navigate(TripEditRoute(tripId)) },
                onAddStop = { tripId -> navController.navigate(StopEditRoute(tripId)) },
                onEditStop = { tripId, stopId -> navController.navigate(StopEditRoute(tripId, stopId)) },
                onOpenTripMap = { tripId -> navController.navigate(AllTripsMapRoute(tripId)) },
            )
        }
        composable<TripEditRoute> {
            TripEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { tripId, isNew ->
                    navController.popBackStack()
                    if (isNew) navController.navigate(TripDetailRoute(tripId))
                },
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
                    viewModel.setLocation(LatLng(lat, lon))
                    backStackEntry.savedStateHandle[PICKED_LOCATION_KEY] = null
                }
            }
            StopEditScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
                locationSection = {
                    LocationSection(
                        onLocationChange = viewModel::setLocation,
                        onPickOnMap = {
                            val location = viewModel.uiState.value.location
                            navController.navigate(
                                LocationPickerRoute(location?.latitude, location?.longitude),
                            )
                        },
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
                onPicked = { location ->
                    navController.previousBackStackEntry?.savedStateHandle?.set(
                        PICKED_LOCATION_KEY,
                        doubleArrayOf(location.latitude, location.longitude),
                    )
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

private const val PICKED_LOCATION_KEY = "picked_location"

private operator fun DoubleArray.component1() = this[0]

private operator fun DoubleArray.component2() = this[1]
