package com.nuelto.camperexperience.ui.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * "Pick on map" buttons for the stop editor.
 */
@Composable
fun LocationSection(
    onLocationChange: (LatLng) -> Unit,
    onPickOnMap: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    fun fetchLocation() {
        status = "Getting GPS fix…"
        scope.launch {
            val location = LocationProvider(context).currentLocation()
            if (location != null) {
                onLocationChange(location)
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

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        fetchLocation()
                    } else {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
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
        status?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
