package com.nuelto.camperexperience.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import com.nuelto.camperexperience.data.model.LatLng
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nuelto.camperexperience.BuildConfig
import com.nuelto.camperexperience.containerViewModelFactory
import com.nuelto.camperexperience.data.AuthRepository
import com.nuelto.camperexperience.data.SettingsRepository
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.ui.components.DecimalField
import com.nuelto.camperexperience.ui.components.parseDecimal
import com.nuelto.camperexperience.ui.map.LocalMapProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    val authRepository: AuthRepository?,
) : ViewModel() {

    val settings: StateFlow<UserSettings?> =
        settingsRepository.settings()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(settings: UserSettings) {
        viewModelScope.launch { settingsRepository.update(settings) }
    }

    /** Sets home from the picker or a GPS fix; an unnamed pick keeps the name it had. */
    fun setHome(location: LatLng, name: String = "") {
        val current = settings.value ?: return
        viewModelScope.launch {
            settingsRepository.update(
                current.copy(
                    homeLocation = location,
                    homeName = name.ifBlank { current.homeName },
                ),
            )
        }
    }

    fun clearHome() {
        val current = settings.value ?: return
        viewModelScope.launch {
            settingsRepository.update(current.copy(homeLocation = null, homeName = ""))
        }
    }

    fun signOut() {
        authRepository?.signOut()
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            SettingsViewModel(container.settingsRepository, container.authRepository)
        }
    }
}

private val commonCurrencies = listOf("CHF", "EUR", "USD", "GBP", "NOK", "SEK", "DKK", "CZK", "PLN")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    // No factory default: the nav layer owns the instance to feed it picker results,
    // the same reason StopEditScreen has none.
    viewModel: SettingsViewModel,
    // Injected by the nav layer, exactly as the stop editor's is: GPS, search and
    // pick-on-map all live behind the map provider.
    locationSection: @Composable () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CurrencyPicker(
                selected = current.currency,
                onSelect = { viewModel.update(current.copy(currency = it)) },
            )

            Text("Home", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = current.homeName,
                onValueChange = { viewModel.update(current.copy(homeName = it)) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                current.homeLocation?.let { "Set — new plans start here." }
                    ?: "Not set. Pick it below and new plans will start from it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            locationSection()
            if (current.homeLocation != null) {
                TextButton(onClick = viewModel::clearHome) { Text("Clear home") }
            }

            Text("Fuel estimator defaults", style = MaterialTheme.typography.titleMedium)
            NumberSetting(
                label = "Consumption",
                suffix = "l/100km",
                value = current.fuelConsumptionL100km,
                onCommit = { viewModel.update(current.copy(fuelConsumptionL100km = it)) },
            )
            NumberSetting(
                label = "Fuel price",
                suffix = "per liter",
                value = current.fuelPricePerLiter,
                onCommit = { viewModel.update(current.copy(fuelPricePerLiter = it)) },
            )
            NumberSetting(
                label = "Road distance factor",
                suffix = "× straight line",
                value = current.roadDistanceFactor,
                onCommit = { viewModel.update(current.copy(roadDistanceFactor = it)) },
            )
            Text(
                "Distance comes from the road Google routes between stops. This factor " +
                    "only fills in for legs with no route yet — no map key, or no signal " +
                    "when they were added.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberSetting(
                label = "Vehicle weight",
                suffix = "kg",
                value = current.vehicleMassKg,
                onCommit = { viewModel.update(current.copy(vehicleMassKg = it)) },
            )
            Text(
                "Used to price the climbing on a route: lifting the van over a pass costs " +
                    "fuel the flat-road consumption above does not account for.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Camping estimate defaults", style = MaterialTheme.typography.titleMedium)
            NumberSetting(
                label = "Campsite",
                suffix = "per night",
                value = current.campsitePerNight,
                onCommit = { viewModel.update(current.copy(campsitePerNight = it)) },
            )
            NumberSetting(
                label = "Stellplatz",
                suffix = "per night",
                value = current.stellplatzPerNight,
                onCommit = { viewModel.update(current.copy(stellplatzPerNight = it)) },
            )
            Text(
                "Used for planned stops without a price. Free camps and visits estimate at zero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (viewModel.authRepository != null) {
                HorizontalDivider()
                OutlinedButton(
                    onClick = viewModel::signOut,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sign out")
                }
            }

            HorizontalDivider()
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Attribution follows the data licence, so it follows the map provider.
            val attribution = LocalMapProvider.current.attribution
            if (attribution.isNotBlank()) {
                Text(
                    attribution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPicker(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Currency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            commonCurrencies.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Number field that commits on every valid parse; falls back to the stored value. */
@Composable
private fun NumberSetting(
    label: String,
    suffix: String,
    value: Double,
    onCommit: (Double) -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    DecimalField(
        label = label,
        suffix = suffix,
        value = text,
        onValueChange = { newText ->
            text = newText
            parseDecimal(newText)?.let { if (it > 0) onCommit(it) }
        },
    )
}
