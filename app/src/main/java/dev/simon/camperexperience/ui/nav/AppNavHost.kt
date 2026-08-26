package dev.simon.camperexperience.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.simon.camperexperience.ui.tripdetail.TripDetailScreen
import dev.simon.camperexperience.ui.triplist.TripListScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = TripListRoute) {
        composable<TripListRoute> {
            TripListScreen(
                onTripClick = { tripId -> navController.navigate(TripDetailRoute(tripId)) },
                onAddTrip = { navController.navigate(TripEditRoute()) },
                onOpenMap = { navController.navigate(AllTripsMapRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        composable<TripDetailRoute> {
            TripDetailScreen(
                onBack = { navController.popBackStack() },
                onEditTrip = { tripId -> navController.navigate(TripEditRoute(tripId)) },
                onAddStop = { tripId -> navController.navigate(StopEditRoute(tripId)) },
                onEditStop = { tripId, stopId -> navController.navigate(StopEditRoute(tripId, stopId)) },
            )
        }
        // TripEditRoute, StopEditRoute: M2. AllTripsMapRoute: M3. SettingsRoute: M5.
    }
}
