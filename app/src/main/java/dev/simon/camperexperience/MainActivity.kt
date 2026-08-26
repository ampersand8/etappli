package dev.simon.camperexperience

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.simon.camperexperience.ui.nav.AppNavHost
import dev.simon.camperexperience.ui.theme.CamperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CamperTheme {
                AppNavHost()
            }
        }
    }
}
