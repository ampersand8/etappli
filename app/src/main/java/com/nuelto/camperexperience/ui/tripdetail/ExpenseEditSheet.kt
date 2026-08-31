package com.nuelto.camperexperience.ui.tripdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.domain.FuelEstimator
import com.nuelto.camperexperience.ui.components.DateField
import com.nuelto.camperexperience.ui.components.DecimalField
import com.nuelto.camperexperience.ui.components.parseDecimal
import com.nuelto.camperexperience.ui.formatCurrency
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * Bottom sheet for creating/editing an expense. When type is FUEL an expandable
 * estimator prefills distance from the trip's stops and computes
 * distance x consumption / 100 x price.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditSheet(
    initial: Expense?,
    stops: List<Stop>,
    settings: UserSettings,
    onSave: (Expense) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember { mutableStateOf(initial?.type ?: ExpenseType.FUEL) }
    var amount by remember {
        mutableStateOf(initial?.amount?.takeIf { it != 0.0 }?.toString() ?: "")
    }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var isEstimate by remember { mutableStateOf(initial?.isEstimate ?: false) }
    var showEstimator by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (initial == null) "New expense" else "Edit expense",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseType.entries.forEach { entry ->
                    FilterChip(
                        selected = type == entry,
                        onClick = { type = entry },
                        label = { Text(entry.displayName) },
                    )
                }
            }
            DecimalField(
                label = "Amount",
                value = amount,
                onValueChange = {
                    amount = it
                    isEstimate = false
                },
                suffix = settings.currency,
            )
            if (type == ExpenseType.FUEL) {
                if (!showEstimator) {
                    TextButton(onClick = { showEstimator = true }) {
                        Text("Estimate from distance…")
                    }
                } else {
                    FuelEstimatorSection(
                        stops = stops,
                        settings = settings,
                        onUseAmount = { estimated ->
                            amount = String.format(Locale.ROOT, "%.2f", estimated)
                            isEstimate = true
                        },
                    )
                }
            }
            DateField(label = "Date", date = date, onDateChange = { date = it })
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val parsed = parseDecimal(amount) ?: return@Button
                    onSave(
                        (initial ?: Expense()).copy(
                            type = type,
                            amount = parsed,
                            date = date,
                            label = label.trim(),
                            isEstimate = isEstimate,
                        ),
                    )
                },
                enabled = parseDecimal(amount) != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun FuelEstimatorSection(
    stops: List<Stop>,
    settings: UserSettings,
    onUseAmount: (Double) -> Unit,
) {
    val routedKm = remember(stops, settings) { FuelEstimator.defaultTripDistanceKm(stops, settings) }
    var distance by remember {
        mutableStateOf(if (routedKm > 0) String.format(Locale.ROOT, "%.0f", routedKm) else "")
    }
    // What the climbing adds on top of the flat-road figure. Shown, not folded into the
    // distance — the distance field has to stay something the user can recognise.
    val tripClimbLiters = remember(stops, settings) { FuelEstimator.climbLiters(stops, settings) }
    var consumption by remember { mutableStateOf(settings.fuelConsumptionL100km.toString()) }
    var price by remember { mutableStateOf(settings.fuelPricePerLiter.toString()) }

    val entered = parseDecimal(distance)
    // The climb belongs to the routed drive, so price only the share of it the user kept.
    // Otherwise shortening the distance leaves the whole trip's climbing on a fraction of
    // it — and on a net-descent trip the credit would swamp what is left.
    val climbLiters =
        if (routedKm > 0 && entered != null) tripClimbLiters * entered / routedKm else 0.0

    val estimated = run {
        val c = parseDecimal(consumption)
        val p = parseDecimal(price)
        if (entered != null && c != null && p != null) {
            // Floored: the descent credit was capped against the consumption in Settings,
            // so a much lower figure typed here could otherwise turn the estimate negative
            // and save a negative fuel expense.
            (FuelEstimator.estimateCost(entered, c, p) + climbLiters * p).coerceAtLeast(0.0)
        } else {
            null
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fuel estimate", style = MaterialTheme.typography.titleSmall)
            Text(
                "Distance is prefilled from the stops — the road Google routed where there " +
                    "is one, straight line × road factor where there isn't. Adjust to your " +
                    "real route.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Both directions are worth saying: a descent that pays some fuel back is just
            // as surprising as a climb that costs extra.
            val climbNote = when {
                climbLiters > 0 -> "Plus %.1f l for the climbing on the way."
                climbLiters < 0 -> "Less %.1f l, given back on the way down."
                else -> null
            }
            if (climbNote != null) {
                Text(
                    String.format(Locale.ROOT, climbNote, abs(climbLiters)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DecimalField(label = "Distance", value = distance, onValueChange = { distance = it }, suffix = "km")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField(
                    label = "Consumption",
                    value = consumption,
                    onValueChange = { consumption = it },
                    suffix = "l/100km",
                    modifier = Modifier.weight(1f),
                )
                DecimalField(
                    label = "Price",
                    value = price,
                    onValueChange = { price = it },
                    suffix = "/l",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    estimated?.let { formatCurrency(it, settings.currency) } ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(
                    onClick = { estimated?.let(onUseAmount) },
                    enabled = estimated != null,
                ) {
                    Text("Use this amount")
                }
            }
        }
    }
}
