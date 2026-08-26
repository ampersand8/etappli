package com.nuelto.camperexperience.ui.tripdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import com.nuelto.camperexperience.ui.map.TripMap
import com.nuelto.camperexperience.ui.map.TripMapData
import com.nuelto.camperexperience.ui.map.tripColor
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.ui.formatCurrency
import com.nuelto.camperexperience.ui.formatDate
import com.nuelto.camperexperience.ui.formatDateRange

val ExpenseType.displayName: String
    get() = when (this) {
        ExpenseType.CAMPING -> "Camping"
        ExpenseType.FUEL -> "Fuel"
        ExpenseType.ROAD_TAX -> "Road tax"
        ExpenseType.OTHER -> "Other"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    onBack: () -> Unit,
    onEditTrip: (String) -> Unit,
    onAddStop: (String) -> Unit,
    onEditStop: (String, String) -> Unit,
    onOpenTripMap: (String) -> Unit,
    viewModel: TripDetailViewModel = viewModel(factory = TripDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trip = state.trip
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExpenseSheet by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(trip?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trip != null) {
                        IconButton(onClick = { onEditTrip(trip.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit trip")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete trip")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (trip != null) {
                ExtendedFloatingActionButton(
                    onClick = { onAddStop(trip.id) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Stop") },
                )
            }
        },
    ) { padding ->
        if (trip == null) return@Scaffold

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(
                        formatDateRange(trip.startDate, trip.endDate),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (trip.notes.isNotBlank()) {
                        Text(
                            trip.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.stops.any { it.location != null }) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(MaterialTheme.shapes.medium),
                    ) {
                        TripMap(
                            data = listOf(
                                TripMapData(trip, state.stops, tripColor(trip.id.hashCode())),
                            ),
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Transparent overlay: tap anywhere on the mini map to go fullscreen.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clickable { onOpenTripMap(trip.id) },
                        )
                    }
                }
            }

            item {
                CostBreakdownCard(
                    total = trip.totalCost,
                    nights = trip.nights,
                    breakdown = state.breakdown,
                    fuelEstimate = state.fuelEstimate,
                    currency = state.settings.currency,
                )
            }

            item {
                Text("Stops", style = MaterialTheme.typography.titleMedium)
            }
            if (state.stops.isEmpty()) {
                item {
                    Text(
                        "No stops yet — add where you camped.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.stops, key = { it.id }) { stop ->
                StopCard(
                    stop = stop,
                    currency = state.settings.currency,
                    onClick = { onEditStop(trip.id, stop.id) },
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Expenses", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = {
                        editingExpense = null
                        showExpenseSheet = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add expense")
                    }
                }
            }
            if (state.expenses.isEmpty()) {
                item {
                    Text(
                        "No expenses yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.expenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    currency = state.settings.currency,
                    onClick = {
                        editingExpense = expense
                        showExpenseSheet = true
                    },
                    onDelete = { viewModel.deleteExpense(expense.id) },
                )
            }
        }
    }

    if (showExpenseSheet && trip != null) {
        ExpenseEditSheet(
            initial = editingExpense,
            stops = state.stops,
            settings = state.settings,
            onSave = { expense ->
                viewModel.upsertExpense(expense)
                showExpenseSheet = false
            },
            onDismiss = { showExpenseSheet = false },
        )
    }

    if (showDeleteDialog && trip != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete trip?") },
            text = { Text("\"${trip.name}\" and all its stops and expenses will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteTrip(onDeleted = onBack)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun CostBreakdownCard(
    total: Double,
    nights: Int,
    breakdown: Map<ExpenseType, Double>,
    fuelEstimate: Double?,
    currency: String,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (fuelEstimate != null) {
                        "≈ ${formatCurrency(total + fuelEstimate, currency)}"
                    } else {
                        formatCurrency(total, currency)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                if (nights == 1) "1 night" else "$nights nights",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (breakdown.isNotEmpty() || fuelEstimate != null) {
                HorizontalDivider()
                // A fuel estimate only exists when there is no FUEL entry in the
                // breakdown, so this keeps the category order stable.
                breakdown[ExpenseType.CAMPING]?.let { BreakdownRow(ExpenseType.CAMPING.displayName, it, currency) }
                fuelEstimate?.let { BreakdownRow("${ExpenseType.FUEL.displayName} (estimate)", it, currency) }
                for (type in listOf(ExpenseType.FUEL, ExpenseType.ROAD_TAX, ExpenseType.OTHER)) {
                    breakdown[type]?.let { BreakdownRow(type.displayName, it, currency) }
                }
                if (fuelEstimate != null) {
                    Text(
                        "Fuel is estimated by driving distance between stops — log a fuel expense to replace it.",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, amount: Double, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(formatCurrency(amount, currency), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StopCard(stop: Stop, currency: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stop.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    formatCurrency(stop.campingCostTotal, currency),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text(
                "${formatDate(stop.arrivalDate)} · ${if (stop.nights == 1) "1 night" else "${stop.nights} nights"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    currency: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    expense.label.ifBlank { expense.type.displayName },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (expense.isEstimate) {
                    Text(
                        "  (estimate)",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "${expense.type.displayName} · ${formatDate(expense.date)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatCurrency(expense.amount, currency), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete expense",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
