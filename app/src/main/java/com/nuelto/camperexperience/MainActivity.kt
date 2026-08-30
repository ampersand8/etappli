package com.nuelto.camperexperience

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import com.nuelto.camperexperience.ui.auth.SignInScreen
import com.nuelto.camperexperience.ui.map.LocalMapProvider
import com.nuelto.camperexperience.ui.nav.AppNavHost
import com.nuelto.camperexperience.ui.theme.CamperTheme
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CamperApp).container
        setContent {
            CamperTheme {
                AppRoot(container)
            }
        }
    }
}

@Composable
internal fun AppRoot(container: AppContainer) {
    CompositionLocalProvider(LocalMapProvider provides container.mapProvider) {
        val authRepository = container.authRepository
        if (authRepository == null) {
            // Local-only mode: Firebase not configured in this build.
            AppNavHost()
        } else {
            val user by authRepository.authState.collectAsState(initial = authRepository.currentUser)
            if (user == null) {
                SignInScreen(authRepository)
            } else {
                AppNavHost()
            }
        }
    }
}
