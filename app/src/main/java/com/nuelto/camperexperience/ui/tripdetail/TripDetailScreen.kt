package com.nuelto.camperexperience.ui.tripdetail

import android.content.Intent
import com.nuelto.camperexperience.ui.formatDriveFromHere
import com.nuelto.camperexperience.domain.DriveFromHere
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.RvHookup
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopKind
import com.nuelto.camperexperience.data.model.isStay
import com.nuelto.camperexperience.data.model.StopLeg
import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.domain.TripMapData
import com.nuelto.camperexperience.domain.Elevation
import com.nuelto.camperexperience.domain.GapRow
import com.nuelto.camperexperience.domain.StopRow
import com.nuelto.camperexperience.domain.TripEstimator
import com.nuelto.camperexperience.ui.components.DecimalField
import com.nuelto.camperexperience.ui.components.DateField
import com.nuelto.camperexperience.ui.components.StatusBadge
import com.nuelto.camperexperience.ui.components.parseDecimal
import com.nuelto.camperexperience.ui.components.rememberReorderState
import com.nuelto.camperexperience.ui.components.reorderable
import com.nuelto.camperexperience.ui.formatCurrency
import com.nuelto.camperexperience.ui.formatDate
import com.nuelto.camperexperience.ui.formatDrive
import com.nuelto.camperexperience.ui.tripedit.displayName
import com.nuelto.camperexperience.ui.formatTripDates
import com.nuelto.camperexperience.ui.map.TripMap
import com.nuelto.camperexperience.ui.theme.ActiveGreen
import com.nuelto.camperexperience.ui.theme.PlannedBlue
import com.nuelto.camperexperience.ui.theme.stopColor
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.launch

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
    onOpenTrip: (String) -> Unit,
    viewModel: TripDetailViewModel = viewModel(factory = TripDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val trip = state.trip
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExpenseSheet by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var fabMenuExpanded by remember { mutableStateOf(false) }
    var showStartSheet by remember { mutableStateOf(false) }
    var arrivalPromptStop by remember { mutableStateOf<Stop?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // Draggable set: rows hanging off a planned stop of a live trip. Done and skipped
    // rows — and the current stop, which is simply where you are — are anchors.
    val movableKeys = state.rows
        .filter {
            trip?.status != TripStatus.DONE &&
                it.anchor.state == StopState.PLANNED &&
                it.anchor.id != state.currentStopId
        }
        .mapTo(mutableSetOf()) { it.key }
    val reorder = rememberReorderState(listState, movableKeys) { from, to ->
        viewModel.moveRow(from, state.rows.indexOfFirst { it.key == to })
    }

    BackHandler(fabMenuExpanded) { fabMenuExpanded = false }

    fun skipWithUndo(stop: Stop) {
        viewModel.skip(stop.id)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Skipped ${stop.name}",
                actionLabel = "Undo",
                withDismissAction = true,
                // Self-dismissing: several skips must not queue indefinitely; the
                // row's restore icon stays as the durable undo.
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restore(stop.id)
        }
    }

    // "How far from here" needs a fix. Asked for on tap rather than on open: a permission
    // dialog the moment a trip is opened is not a trade the user agreed to.
    val locationContext = LocalContext.current
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                locationContext,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> locationGranted = granted }
    // Keyed on the grant too, so answering the dialog is what triggers the first fix —
    // the launcher callback only has to record it.
    LaunchedEffect(state.currentStopId, locationGranted) {
        if (locationGranted) viewModel.refreshDriveFromHere()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            trip?.name ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (trip != null) StatusBadge(trip.status)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trip != null) {
                        if (trip.status == TripStatus.DONE) {
                            IconButton(onClick = { viewModel.planAgain(onCreated = onOpenTrip) }) {
                                Icon(Icons.Default.Route, contentDescription = "Plan again")
                            }
                        }
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
        bottomBar = {
            if (trip != null && trip.status != TripStatus.DONE && state.estimate != null) {
                TimelineBottomBar(
                    status = trip.status,
                    totalText = if (state.estimate!!.hasEstimates) {
                        "≈ ${formatCurrency(state.estimate!!.total, state.settings.currency)}"
                    } else {
                        formatCurrency(state.estimate!!.total, state.settings.currency)
                    },
                    onStart = { showStartSheet = true },
                    onFinish = viewModel::finishTrip,
                )
            }
        },
        floatingActionButton = {
            if (trip != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedVisibility(fabMenuExpanded) {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    fabMenuExpanded = false
                                    onAddStop(trip.id)
                                },
                                icon = { Icon(Icons.Default.Place, contentDescription = null) },
                                text = { Text("Stop") },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            )
                            ExtendedFloatingActionButton(
                                onClick = {
                                    fabMenuExpanded = false
                                    editingExpense = null
                                    showExpenseSheet = true
                                },
                                icon = { Icon(Icons.Default.Receipt, contentDescription = null) },
                                text = { Text("Expense") },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            )
                        }
                    }
                    FloatingActionButton(onClick = { fabMenuExpanded = !fabMenuExpanded }) {
                        Icon(
                            if (fabMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = if (fabMenuExpanded) "Close add menu" else "Add",
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (trip == null) return@Scaffold

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).testTag("timeline"),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(
                        formatTripDates(trip),
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
                            data = listOf(TripMapData(trip, state.stops, currentStopId = state.currentStopId)),
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
                val estimate = state.estimate
                if (estimate != null) {
                    EstimateCard(
                        estimate = estimate,
                        nights = trip.nights,
                        recordedTotal = if (trip.status == TripStatus.ACTIVE) trip.totalCost else null,
                        currency = state.settings.currency,
                        vignetteSuggestions = state.vignetteSuggestions,
                        onAddVignette = viewModel::addVignette,
                    )
                } else {
                    CostBreakdownCard(
                        total = trip.totalCost,
                        nights = trip.nights,
                        breakdown = state.breakdown,
                        fuelEstimate = state.fuelEstimate,
                        currency = state.settings.currency,
                    )
                }
            }
            val plannedCost = trip.plannedCost
            if (trip.status == TripStatus.DONE && plannedCost != null) {
                item {
                    // The estimate snapshot taken at start time, for plan-vs-reality.
                    Text(
                        "Planned: ≈ ${formatCurrency(plannedCost, state.settings.currency)}" +
                            (trip.plannedNights?.let { " · ${if (it == 1) "1 night" else "$it nights"}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Text("Stops", style = MaterialTheme.typography.titleMedium)
            }
            if (state.stops.isEmpty()) {
                item {
                    Text(
                        if (trip.status == TripStatus.PLANNED) {
                            "No stops yet — add where you want to go."
                        } else {
                            "No stops yet — add where you camped."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.rows, key = { it.key }) { row ->
                val movable = row.key in movableKeys
                val rowModifier = Modifier
                    .testTag("row-${row.key}")
                    .reorderable(reorder, row.key, listState, enabled = movable)
                when (row) {
                    is GapRow -> GapCard(
                        row = row,
                        editable = movable,
                        onNights = { nights -> viewModel.setGapNights(row.key, nights) },
                        modifier = rowModifier,
                    )
                    is StopRow -> if (row.stop.id == state.currentStopId) {
                        NowCard(
                            stop = row.stop,
                            leg = state.drives[row.stop.id],
                            fromHere = state.driveFromHere,
                            onLocate = if (locationGranted) {
                                null
                            } else {
                                { locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                            },
                            settings = state.settings,
                            onClick = { onEditStop(trip.id, row.stop.id) },
                            onChangeNights = { delta -> viewModel.changeNights(row.stop.id, delta) },
                            onArrived = {
                                viewModel.arrived(row.stop.id)
                                if (row.stop.kind.isStay) arrivalPromptStop = row.stop
                            },
                            onSkip = { skipWithUndo(row.stop) },
                        )
                    } else {
                        TimelineStopRow(
                            stop = row.stop,
                            leg = state.drives[row.stop.id],
                            tripStatus = trip.status,
                            settings = state.settings,
                            movable = movable,
                            onClick = { onEditStop(trip.id, row.stop.id) },
                            onSkip = { skipWithUndo(row.stop) },
                            onRestore = { viewModel.restore(row.stop.id) },
                            modifier = rowModifier,
                        )
                    }
                }
            }

            item {
                Text("Expenses", style = MaterialTheme.typography.titleMedium)
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

    if (showStartSheet && trip != null) {
        StartTourSheet(
            onStart = { startDate, keepPlan ->
                showStartSheet = false
                viewModel.startTour(startDate, keepPlan) { startedId ->
                    if (startedId != trip.id) onOpenTrip(startedId)
                }
            },
            onDismiss = { showStartSheet = false },
        )
    }

    arrivalPromptStop?.let { stop ->
        ArrivalPriceSheet(
            stop = stop,
            settings = state.settings,
            onSave = { price ->
                viewModel.setStopPrice(stop.id, price)
                arrivalPromptStop = null
            },
            onDismiss = { arrivalPromptStop = null },
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
private fun TimelineBottomBar(
    status: TripStatus,
    totalText: String,
    onStart: () -> Unit,
    onFinish: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Inside the Surface, so its background still fills the gesture area while
                // the total and the button sit above the navigation bar. The app is
                // edge-to-edge (enableEdgeToEdge + targetSdk 36), and Scaffold does not
                // inset a bottom bar for you — it places it flush with the window.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                totalText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (status == TripStatus.PLANNED) PlannedBlue else ActiveGreen,
            )
            if (status == TripStatus.PLANNED) {
                Button(onClick = onStart) { Text("Start tour") }
            } else {
                Button(onClick = onFinish) { Text("Finish trip") }
            }
        }
    }
}

/** Tonight's stop on an active trip: check in, adjust the stay, hand off navigation. */
@Composable
private fun NowCard(
    stop: Stop,
    leg: StopLeg?,
    fromHere: DriveFromHere?,
    // Null once the location permission is held; otherwise, what to call to ask for it.
    onLocate: (() -> Unit)?,
    settings: UserSettings,
    onClick: () -> Unit,
    onChangeNights: (Int) -> Unit,
    onArrived: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().border(2.dp, ActiveGreen, MaterialTheme.shapes.medium),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (stop.kind.isStay) "TONIGHT" else "NEXT",
                style = MaterialTheme.typography.labelSmall,
                color = ActiveGreen,
                fontWeight = FontWeight.Bold,
            )
            // Where you are beats where you came from: on the stop you are driving to, the
            // live distance replaces the planned leg rather than sitting beside it.
            when {
                fromHere != null && fromHere.to == stop.location -> Text(
                    // "now" is read at composition: opening the trip gives a current
                    // answer, and every state change refreshes it. It does not tick on
                    // its own — an endless timer in a LaunchedEffect would leave the
                    // Compose test clock permanently busy.
                    formatDriveFromHere(
                        fromHere.distanceMeters,
                        fromHere.durationSeconds,
                        LocalDateTime.now(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = ActiveGreen,
                )
                onLocate != null && stop.location != null && stop.state != StopState.DONE -> Text(
                    "Show distance from here",
                    style = MaterialTheme.typography.labelSmall,
                    color = ActiveGreen,
                    modifier = Modifier.clickable(onClick = onLocate),
                )
                else -> DriveLine(leg)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StopDot(stop, TripStatus.ACTIVE)
                // Weighted, so the price stays pinned to the right edge; the name inside
                // is not, so the height badge sits against the name rather than drifting.
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stop.name,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ElevationBadge(stop)
                }
                if (stop.kind.isStay) {
                    Text(
                        stopPriceText(stop, TripStatus.ACTIVE, settings),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            if (stop.kind.isStay) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${formatDate(stop.arrivalDate)} ·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { onChangeNights(-1) }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "One night less")
                    }
                    Text(
                        if (stop.nights == 1) "1 night" else "${stop.nights} nights",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = { onChangeNights(1) }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "One night more")
                    }
                }
            }
            if (stop.state == StopState.DONE) {
                // Mid-stay: checked in, staying — only the stepper above matters.
                Text(
                    "Checked in",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ActiveGreen,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onArrived) { Text("✓ Arrived") }
                    if (stop.location != null) {
                        TextButton(onClick = {
                            val loc = stop.location
                            // Hand driving off to the maps app; we never build turn-by-turn.
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        "geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}".toUri(),
                                    ),
                                )
                            }
                        }) { Text("Navigate") }
                    }
                    TextButton(onClick = onSkip) { Text("Skip") }
                }
            }
        }
    }
}

/** Nights nobody planned: resize them, drop them, or drag them elsewhere in the plan. */
@Composable
private fun GapCard(
    row: GapRow,
    editable: Boolean,
    onNights: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.size(12.dp)) // where a stop would carry its dot
            Column(Modifier.weight(1f)) {
                Text(
                    "Nothing planned",
                    style = MaterialTheme.typography.titleSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatDate(row.from)} · " + if (row.nights == 1) "1 night" else "${row.nights} nights",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (editable) {
                IconButton(onClick = { onNights(row.nights - 1) }) {
                    Icon(Icons.Default.Remove, contentDescription = "One unplanned night less")
                }
                IconButton(onClick = { onNights(row.nights + 1) }) {
                    Icon(Icons.Default.Add, contentDescription = "One unplanned night more")
                }
                IconButton(onClick = { onNights(0) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove the unplanned nights")
                }
            }
        }
    }
}

@Composable
private fun TimelineStopRow(
    stop: Stop,
    leg: StopLeg?,
    tripStatus: TripStatus,
    settings: UserSettings,
    movable: Boolean,
    onClick: () -> Unit,
    onSkip: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StopDot(stop, tripStatus)
            Column(Modifier.weight(1f)) {
                DriveLine(leg)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stop.name,
                        // fill = false so the height sits against the name, not out at
                        // the far edge; unweighted, the badge keeps its width first.
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (stop.state == StopState.SKIPPED) TextDecoration.LineThrough else null,
                    )
                    ElevationBadge(stop)
                }
                Text(
                    stopMetaText(stop, tripStatus, settings),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (movable) {
                // Affordance only — the whole card is the drag target (long-press).
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tripStatus == TripStatus.ACTIVE && stop.state == StopState.PLANNED) {
                IconButton(onClick = onSkip) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Skip stop")
                }
            }
            if (tripStatus == TripStatus.ACTIVE && stop.state == StopState.SKIPPED) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Default.Refresh, contentDescription = "Restore stop")
                }
            }
        }
    }
}

/**
 * How far and how long the drive to this stop is, as Google routed it. Absent until the
 * route has been fetched, and while a stop edit has outdated it.
 */
@Composable
private fun DriveLine(leg: StopLeg?) {
    if (leg == null) return
    Text(
        "\u2192 ${formatDrive(leg)}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * What the spot is, in the lifecycle colour: blue = upcoming, green = current,
 * grey = done/skipped. Shape says the kind, colour says where it is in the trip.
 */
@Composable
private fun StopDot(stop: Stop, tripStatus: TripStatus) {
    val color = if (tripStatus == TripStatus.DONE) {
        stopColor(StopState.DONE, isCurrent = false)
    } else {
        stopColor(stop.state, isCurrent = false)
    }
    Icon(
        kindIcon(stop.kind),
        contentDescription = stop.kind.displayName,
        modifier = Modifier.size(18.dp),
        tint = color,
    )
}

private fun kindIcon(kind: StopKind): ImageVector = when (kind) {
    StopKind.CAMPSITE -> Icons.Default.Festival
    StopKind.STELLPLATZ -> Icons.Default.RvHookup
    StopKind.FREE_CAMP -> Icons.Default.Forest
    StopKind.VISIT -> Icons.Default.PhotoCamera
    StopKind.HOME -> Icons.Default.Home
}

/** How high the place is — a mountain, because "1469 m" alone reads as a distance. */
@Composable
private fun ElevationBadge(stop: Stop) {
    val meters = Elevation.ofStop(stop) ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            Icons.Default.Terrain,
            contentDescription = "Height above sea level",
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "$meters m",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun stopMetaText(stop: Stop, tripStatus: TripStatus, settings: UserSettings): String {
    val date = formatDate(stop.arrivalDate)
    return when {
        stop.state == StopState.SKIPPED -> "skipped"
        stop.kind == StopKind.HOME -> "$date · start"
        stop.kind == StopKind.VISIT -> "$date · visit"
        else -> {
            val nights = if (stop.nights == 1) "1 night" else "${stop.nights} nights"
            "$date · $nights · ${stopPriceText(stop, tripStatus, settings)}"
        }
    }
}

/** An unpriced stay on a live trip shows what the default rate would charge. */
private fun stopPriceText(stop: Stop, tripStatus: TripStatus, settings: UserSettings): String =
    if (!stop.costKnown && tripStatus != TripStatus.DONE) {
        "≈ ${formatCurrency(stop.nights * TripEstimator.nightlyRate(stop.kind, settings), settings.currency)}"
    } else {
        formatCurrency(stop.campingCostTotal, settings.currency)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTourSheet(
    onStart: (LocalDate, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var keepPlan by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Start this tour", style = MaterialTheme.typography.titleLarge)
            DateField(label = "Start date", date = startDate, onDateChange = { startDate = it })
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Keep plan as template", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "The tour stays in your library for next time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = keepPlan, onCheckedChange = { keepPlan = it })
            }
            Button(
                onClick = { onStart(startDate, keepPlan) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start") }
        }
    }
}

/** Check-in moment: one optional number turns the estimate into the actual price. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrivalPriceSheet(
    stop: Stop,
    settings: UserSettings,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var priceText by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Arrived at ${stop.name}", style = MaterialTheme.typography.titleLarge)
            DecimalField(
                label = "Price for the stay",
                value = priceText,
                onValueChange = { priceText = it },
                supportingText = if (stop.costKnown) {
                    null
                } else {
                    "≈ ${formatCurrency(stop.nights * TripEstimator.nightlyRate(stop.kind, settings), settings.currency)} estimated"
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { parseDecimal(priceText)?.let(onSave) }) { Text("Save price") }
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        }
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
