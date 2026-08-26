package dev.simon.camperexperience.ui.tripedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.simon.camperexperience.ui.components.DateField
import dev.simon.camperexperience.ui.components.DecimalField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopEditScreen(
    onBack: () -> Unit,
    viewModel: StopEditViewModel = viewModel(factory = StopEditViewModel.Factory),
    locationSection: (@Composable () -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New stop" else "Edit stop") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { viewModel.delete(onDeleted = onBack) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete stop")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Campsite / place") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                label = "Arrival",
                date = state.arrivalDate,
                onDateChange = viewModel::setArrivalDate,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Nights", style = MaterialTheme.typography.bodyLarge)
                IconButton(onClick = { viewModel.setNights(state.nights - 1) }) {
                    Icon(Icons.Default.Remove, contentDescription = "Fewer nights")
                }
                Text("${state.nights}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { viewModel.setNights(state.nights + 1) }) {
                    Icon(Icons.Default.Add, contentDescription = "More nights")
                }
            }
            DecimalField(
                label = "Camping cost (total for stay)",
                value = state.campingCost,
                onValueChange = viewModel::setCampingCost,
            )

            Text("Location", style = MaterialTheme.typography.titleMedium)
            // GPS + map picker buttons are provided by the nav layer from M3 on.
            locationSection?.invoke()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField(
                    label = "Latitude",
                    value = state.latText,
                    onValueChange = viewModel::setLatText,
                    modifier = Modifier.weight(1f),
                )
                DecimalField(
                    label = "Longitude",
                    value = state.lonText,
                    onValueChange = viewModel::setLonText,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.save(onSaved = onBack) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}
