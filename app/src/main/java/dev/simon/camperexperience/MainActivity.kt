package dev.simon.camperexperience

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.simon.camperexperience.ui.auth.SignInScreen
import dev.simon.camperexperience.ui.nav.AppNavHost
import dev.simon.camperexperience.ui.theme.CamperTheme
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
private fun AppRoot(container: AppContainer) {
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
