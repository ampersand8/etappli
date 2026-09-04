package com.nuelto.etappli

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nuelto.etappli.domain.SharedPlace
import com.nuelto.etappli.ui.auth.SignInScreen
import com.nuelto.etappli.ui.map.LocalMapProvider
import com.nuelto.etappli.ui.nav.AppNavHost
import com.nuelto.etappli.ui.theme.CamperTheme
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
            TrackingTrigger(container)
        } else {
            val user by authRepository.authState.collectAsState(initial = authRepository.currentUser)
            if (user == null) {
                // A share arriving now waits in the intake until there is a NavHost.
                SignInScreen(authRepository)
            } else {
                AppNavHost(shared, container.shareIntake::consume)
                // Only here: the Firestore repositories throw without a user.
                TrackingTrigger(container)
            }
        }
    }
}

/**
 * Starts tracking whenever the app is open with a drive underway: opening the app on a
 * travelling day is what starts it, and the service keeps going after. Asked again on
 * every return to the foreground in case the system ended it — a start landing on a
 * running service changes nothing.
 */
@Composable
internal fun TrackingTrigger(container: AppContainer) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(container) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            container.tracker.heading()
                .map { it?.takeIf { h -> h.underway }?.stop?.id }
                .distinctUntilChanged()
                // A plan that cannot be read starts nothing; the next STARTED asks again.
                .catch { }
                .collect { if (it != null) container.startTracking() }
        }
    }
}
