package com.nuelto.camperexperience.ui.tripedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuelto.camperexperience.ui.components.DateField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripEditScreen(
    onBack: () -> Unit,
    onSaved: (tripId: String, isNew: Boolean) -> Unit,
    viewModel: TripEditViewModel = viewModel(factory = TripEditViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New trip" else "Edit trip") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
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
                label = { Text("Trip name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(
                label = "Start date",
                date = state.startDate,
                onDateChange = viewModel::setStartDate,
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                DateField(
                    label = "End date",
                    date = state.endDate,
                    onDateChange = viewModel::setEndDate,
                    placeholder = "Ongoing",
                    modifier = Modifier.weight(1f),
                )
                if (state.endDate != null) {
                    TextButton(onClick = { viewModel.setEndDate(null) }) { Text("Clear") }
                }
            }
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Notes") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}
