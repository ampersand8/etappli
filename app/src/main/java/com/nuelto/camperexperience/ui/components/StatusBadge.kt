package com.nuelto.camperexperience.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.ui.theme.statusColor

val TripStatus.displayName: String
    get() = when (this) {
        TripStatus.PLANNED -> "Planned"
        TripStatus.ACTIVE -> "On the road"
        TripStatus.DONE -> "Done"
    }

@Composable
fun StatusBadge(status: TripStatus, modifier: Modifier = Modifier) {
    val color = statusColor(status)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Text(
            status.displayName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}
