package com.nuelto.camperexperience.ui.tripedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.nuelto.camperexperience.domain.PlaceSuggestion

/**
 * Type-ahead place search for the stop editor: additive to "I'm here" and "Pick on map",
 * which stay usable when the lookup is unavailable. Plain rows, not a lazy list — the
 * editor is already inside a vertical scroll.
 */
@Composable
fun PlaceSearchField(
    state: StopEditUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onPick: (PlaceSuggestion) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = state.placeQuery,
            onValueChange = onQueryChange,
            label = { Text("Search a place") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.placeQuery.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        searchStatusText(state.searchStatus)?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.suggestions.forEach { suggestion ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        keyboard?.hide()
                        onPick(suggestion)
                    }
                    .padding(vertical = 6.dp),
            ) {
                Text(suggestion.name, style = MaterialTheme.typography.bodyLarge)
                if (suggestion.label.isNotBlank()) {
                    Text(
                        suggestion.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun searchStatusText(status: PlaceSearchStatus): String? = when (status) {
    PlaceSearchStatus.IDLE -> null
    PlaceSearchStatus.SEARCHING -> "Searching…"
    PlaceSearchStatus.EMPTY -> "Nothing found."
    PlaceSearchStatus.UNAVAILABLE -> "Search unavailable — pick on the map instead."
}
