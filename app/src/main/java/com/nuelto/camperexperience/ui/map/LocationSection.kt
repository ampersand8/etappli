package com.nuelto.camperexperience.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nuelto.camperexperience.data.model.LatLng
import com.nuelto.camperexperience.location.LocationProvider
import kotlinx.coroutines.launch

/**
 * "Use current position" (one-shot GPS, permission requested lazily) and
 * "Pick on map" buttons for the stop editor. With [autoLocate] the GPS fix is
 * kicked off immediately (new stops default to the current location);
 * [onAutoLocateHandled] fires once so a recomposition never re-triggers it. That
 * unasked-for fix reports through [onAutoLocated], which may decline it — it can
 * land long after the user searched for somewhere else.
 */
@Composable
fun LocationSection(
    onLocationChange: (LatLng) -> Unit,
    onPickOnMap: () -> Unit,
    autoLocate: Boolean = false,
    onAutoLocateHandled: () -> Unit = {},
    onAutoLocated: (LatLng) -> Unit = onLocationChange,
    // Google Maps link for this stop, once it has somewhere to point at.
    shareUrl: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var unasked by remember { mutableStateOf(false) }

    fun fetchLocation() {
        status = "Getting GPS fix…"
        scope.launch {
            val location = LocationProvider(context).currentLocation()
            if (location != null) {
                if (unasked) onAutoLocated(location) else onLocationChange(location)
                status = null
            } else {
                status = "No GPS fix — try picking on the map."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            fetchLocation()
        } else {
            status = "Location permission denied — pick on the map instead."
        }
    }

    fun locate() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            fetchLocation()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(autoLocate) {
        if (autoLocate) {
            onAutoLocateHandled()
            unasked = true
            locate()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { unasked = false; locate() },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null)
                Text("  I'm here")
            }
            OutlinedButton(onClick = onPickOnMap, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Place, contentDescription = null)
                Text("  Pick on map")
            }
        }
        shareUrl?.let { url ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { context.startActivity(viewIntent(url)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Map, contentDescription = null)
                    Text("  Open in Maps")
                }
                OutlinedButton(
                    onClick = { context.startActivity(Intent.createChooser(shareIntent(url), null)) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text("  Share")
                }
            }
        }
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun viewIntent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

private fun shareIntent(url: String) = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, url)
}
