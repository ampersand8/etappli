package com.nuelto.camperexperience.ui.tripdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuelto.camperexperience.domain.EstimateBreakdown
import com.nuelto.camperexperience.domain.VignetteTable
import com.nuelto.camperexperience.ui.formatCurrency

/**
 * Live ≈ breakdown for planned/active trips: trip-level number, per-category rows,
 * one-tap vignette chips. Deliberately no per-leg precision.
 */
@Composable
fun EstimateCard(
    estimate: EstimateBreakdown,
    nights: Int,
    recordedTotal: Double?,
    currency: String,
    vignetteSuggestions: List<VignetteTable.Choice>,
    onAddVignette: (VignetteTable.Choice) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (recordedTotal != null) "Projected total" else "Estimated total",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    amountText(estimate.total, estimate.hasEstimates, currency),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                buildString {
                    append(if (nights == 1) "1 night" else "$nights nights")
                    if (recordedTotal != null) {
                        append(" · ${formatCurrency(recordedTotal, currency)} recorded so far")
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            if (estimate.fuel > 0) {
                EstimateRow(
                    if (estimate.fuelIsEstimate) "Fuel (estimate)" else "Fuel",
                    formatCurrency(estimate.fuel, currency),
                )
            }
            if (estimate.camping > 0) {
                EstimateRow("Camping", campingText(estimate, currency))
            }
            if (estimate.roadTax > 0) {
                EstimateRow("Road tax", formatCurrency(estimate.roadTax, currency))
            }
            if (estimate.other > 0) {
                EstimateRow("Other", formatCurrency(estimate.other, currency))
            }
            if (vignetteSuggestions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vignetteSuggestions.forEach { choice ->
                        AssistChip(
                            onClick = { onAddVignette(choice) },
                            label = {
                                Text(
                                    "+ ${choice.label} ≈ ${formatCurrency(
                                        VignetteTable.convert(choice.total, choice.vignette.currency, currency),
                                        currency,
                                    )}",
                                )
                            },
                        )
                    }
                }
            }
            Text(
                "≈ = estimated — enter real prices on stops to refine. " +
                    "Vignette prices: ${VignetteTable.YEAR}.",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun amountText(amount: Double, isEstimate: Boolean, currency: String): String =
    if (isEstimate) "≈ ${formatCurrency(amount, currency)}" else formatCurrency(amount, currency)

/** "CHF 186 + ≈ CHF 105" — recorded prices vs default-rate estimates. */
private fun campingText(estimate: EstimateBreakdown, currency: String): String = when {
    estimate.campingKnown > 0 && estimate.campingEstimated > 0 ->
        "${formatCurrency(estimate.campingKnown, currency)} + ≈ ${formatCurrency(estimate.campingEstimated, currency)}"
    estimate.campingEstimated > 0 -> "≈ ${formatCurrency(estimate.campingEstimated, currency)}"
    else -> formatCurrency(estimate.campingKnown, currency)
}

@Composable
private fun EstimateRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
