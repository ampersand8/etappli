package com.nuelto.camperexperience.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nuelto.camperexperience.data.model.LatLng
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/** Fullscreen map with a fixed center crosshair; confirming returns the map center. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    initial: LatLng?,
    onPicked: (LatLng) -> Unit,
    onCancel: () -> Unit,
) {
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = initial?.let { Position(longitude = it.longitude, latitude = it.latitude) }
                ?: Position(longitude = 8.2, latitude = 46.8),
            zoom = if (initial != null) 12.0 else 6.0,
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pick location") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val target = cameraState.position.target
                    onPicked(LatLng(target.latitude, target.longitude))
                },
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                text = { Text("Use this spot") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (LocalMapEnabled.current) {
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    baseStyle = BaseStyle.Uri(MAP_STYLE_URL),
                    cameraState = cameraState,
                )
            }
            Crosshair(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun Crosshair(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(modifier.size(36.dp)) {
        val c = center
        val r = size.minDimension / 2
        drawCircle(color = color, radius = r / 3, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        drawCircle(color = Color.White, radius = r / 3 + 2f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        drawLine(color, start = c.copy(y = c.y - r), end = c.copy(y = c.y - r / 2), strokeWidth = 4f)
        drawLine(color, start = c.copy(y = c.y + r / 2), end = c.copy(y = c.y + r), strokeWidth = 4f)
        drawLine(color, start = c.copy(x = c.x - r), end = c.copy(x = c.x - r / 2), strokeWidth = 4f)
        drawLine(color, start = c.copy(x = c.x + r / 2), end = c.copy(x = c.x + r), strokeWidth = 4f)
    }
}
