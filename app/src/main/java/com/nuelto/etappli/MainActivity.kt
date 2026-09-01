package com.nuelto.etappli

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import com.nuelto.etappli.domain.SharedPlace
import com.nuelto.etappli.ui.auth.SignInScreen
import com.nuelto.etappli.ui.map.LocalMapProvider
import com.nuelto.etappli.ui.nav.AppNavHost
import com.nuelto.etappli.ui.theme.CamperTheme
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as CamperApp).container
        addOnNewIntentListener(::onShared)
        // The cold start owns its launch intent; a recreate must not file the share twice,
        // and neither may a task Android restores with that same intent still attached.
        if (savedInstanceState == null) container.shareIntake.offer(sharedPlace(intent))
        setContent {
            CamperTheme {
                AppRoot(container)
            }
        }
    }

    /** A share arriving while the app runs. A plain relaunch is not one, and changes nothing. */
    internal fun onShared(intent: Intent) {
        sharedPlace(intent)?.let((application as CamperApp).container.shareIntake::offer)
    }

    /** EXTRA_TEXT is declared CharSequence: getStringExtra returns null for a styled one. */
    internal fun sharedPlace(intent: Intent): SharedPlace? = when (intent.action) {
        Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        Intent.ACTION_VIEW -> intent.dataString
        else -> null
    }?.let { SharedPlace.parse(it, intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()) }
}

@Composable
internal fun AppRoot(container: AppContainer) {
    CompositionLocalProvider(LocalMapProvider provides container.mapProvider) {
        val shared by container.shareIntake.pending.collectAsState()
        val authRepository = container.authRepository
        if (authRepository == null) {
            // Local-only mode: Firebase not configured in this build.
            AppNavHost(shared, container.shareIntake::consume)
        } else {
            val user by authRepository.authState.collectAsState(initial = authRepository.currentUser)
            if (user == null) {
                // A share arriving now waits in the intake until there is a NavHost.
                SignInScreen(authRepository)
            } else {
                AppNavHost(shared, container.shareIntake::consume)
            }
        }
    }
}
