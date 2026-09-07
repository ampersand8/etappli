package com.nuelto.etappli.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * One "Pick spot" button for the stop editor and for home in Settings: the picker is
 * where a place is searched, pinned, or taken from GPS. With a [shareUrl] the stop can
 * also be opened in Google Maps or shared.
 */
@Composable
fun LocationSection(onPick: () -> Unit, shareUrl: String? = null) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Place, contentDescription = null)
            Text("  Pick spot")
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
    }
}

private fun viewIntent(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(url))

private fun shareIntent(url: String) = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, url)
}
