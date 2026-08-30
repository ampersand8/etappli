package com.nuelto.camperexperience.ui.tripdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.nuelto.camperexperience.containerViewModelFactory
import com.nuelto.camperexperience.data.SettingsRepository
import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.data.model.Expense
import com.nuelto.camperexperience.data.model.ExpenseType
import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.data.model.Trip
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.data.model.UserSettings
import com.nuelto.camperexperience.domain.CostCalculator
import com.nuelto.camperexperience.domain.CountryGuess
import com.nuelto.camperexperience.domain.CurrentStop
import com.nuelto.camperexperience.domain.DateCascade
import com.nuelto.camperexperience.domain.EstimateBreakdown
import com.nuelto.camperexperience.domain.FuelEstimator
import com.nuelto.camperexperience.domain.GapRow
import com.nuelto.camperexperience.domain.StopRow
import com.nuelto.camperexperience.domain.PlaceCacheSweeper
import com.nuelto.camperexperience.domain.Timeline
import com.nuelto.camperexperience.domain.TimelineRow
import com.nuelto.camperexperience.domain.TripEstimator
import com.nuelto.camperexperience.domain.TripStarter
import com.nuelto.camperexperience.domain.VignetteTable
import com.nuelto.camperexperience.ui.nav.TripDetailRoute
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TripDetailUiState(
    val trip: Trip? = null,
    val stops: List<Stop> = emptyList(),
    // The stops with the unplanned nights between them spelled out.
    val rows: List<TimelineRow> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val breakdown: Map<ExpenseType, Double> = emptyMap(),
    // Automatic fuel estimate from driving distance; null once real fuel is logged.
    val fuelEstimate: Double? = null,
    // Live ≈ estimate — only for PLANNED/ACTIVE trips (DONE shows actuals).
    val estimate: EstimateBreakdown? = null,
    // First upcoming stop of an ACTIVE trip: tonight's destination.
    val currentStopId: String? = null,
    val vignetteSuggestions: List<VignetteTable.Choice> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val loading: Boolean = true,
)

class TripDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository,
    // Renews Google Places coordinates that hit their 30-day retention, and deletes the
    // ones it cannot renew. Null when the map provider has no place lookup of its own.
    private val placeCacheSweeper: PlaceCacheSweeper? = null,
) : ViewModel() {

    private val tripId: String = savedStateHandle.toRoute<TripDetailRoute>().tripId

    init {
        placeCacheSweeper?.let { sweeper -> viewModelScope.launch { sweeper.sweep(tripId) } }
    }

    val uiState: StateFlow<TripDetailUiState> =
        combine(
            tripRepository.trip(tripId),
            tripRepository.stops(tripId),
            tripRepository.expenses(tripId),
            settingsRepository.settings(),
        ) { trip, stops, expenses, settings ->
            val planning = trip != null && trip.status != TripStatus.DONE
            TripDetailUiState(
                trip = trip,
                stops = stops,
                rows = Timeline.rows(stops),
                expenses = expenses,
                breakdown = CostCalculator.breakdown(stops, expenses),
                fuelEstimate = FuelEstimator.autoTripFuelCost(stops, expenses, settings),
                estimate = if (planning) TripEstimator.estimate(stops, expenses, settings) else null,
                currentStopId = if (trip?.status == TripStatus.ACTIVE) {
                    CurrentStop.of(stops, LocalDate.now())
                } else {
                    null
                },
                vignetteSuggestions = if (planning) vignetteSuggestions(stops, expenses) else emptyList(),
                settings = settings,
                loading = false,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailUiState())

    /** Cheapest vignette per detected country that has no ROAD_TAX line yet. */
    private fun vignetteSuggestions(stops: List<Stop>, expenses: List<Expense>): List<VignetteTable.Choice> =
        CountryGuess.vignetteCountries(stops)
            .filter { code ->
                expenses.none { it.type == ExpenseType.ROAD_TAX && it.label.startsWith(code) }
            }
            .mapNotNull { code -> VignetteTable.cheapestCovering(code, CountryGuess.daysIn(code, stops)) }

    // Serializes stop mutations and reads fresh state inside the lock: rapid taps on
    // the stepper/arrive/skip must not act on a stale snapshot (Firestore emissions
    // lag behind the fire-and-forget writes).
    private val writeLock = Mutex()

    private suspend fun freshStop(stopId: String): Stop? =
        tripRepository.stops(tripId).first().find { it.id == stopId }

    fun deleteStop(stopId: String) {
        viewModelScope.launch { tripRepository.deleteStop(tripId, stopId) }
    }

    fun deleteExpense(expenseId: String) {
        viewModelScope.launch { tripRepository.deleteExpense(tripId, expenseId) }
    }

    fun upsertExpense(expense: Expense) {
        viewModelScope.launch { tripRepository.upsertExpense(expense.copy(tripId = tripId)) }
    }

    fun deleteTrip(onDeleted: () -> Unit) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
            onDeleted()
        }
    }

    /** ±1 night on a stop; the downstream planned schedule shifts along. */
    fun changeNights(stopId: String, delta: Int) {
        viewModelScope.launch {
            writeLock.withLock {
                val stop = freshStop(stopId) ?: return@withLock
                val nights = (stop.nights + delta).coerceIn(0, 365)
                if (nights == stop.nights) return@withLock
                tripRepository.upsertStop(stop.copy(nights = nights))
                shiftAfter(stop.orderIndex, (nights - stop.nights).toLong())
            }
        }
    }

    /** Check-in: stamps today as the actual arrival; a late arrival shifts what follows. */
    fun arrived(stopId: String) {
        viewModelScope.launch {
            writeLock.withLock {
                val stop = freshStop(stopId) ?: return@withLock
                val today = LocalDate.now()
                tripRepository.upsertStop(stop.copy(state = StopState.DONE, arrivalDate = today))
                shiftAfter(stop.orderIndex, ChronoUnit.DAYS.between(stop.arrivalDate, today))
            }
        }
    }

    /** Typing the real price at check-in is the whole plan→actual reconciliation. */
    fun setStopPrice(stopId: String, price: Double) {
        viewModelScope.launch {
            writeLock.withLock {
                val stop = freshStop(stopId) ?: return@withLock
                tripRepository.upsertStop(stop.copy(campingCostTotal = price, costKnown = true))
            }
        }
    }

    fun skip(stopId: String) = setState(stopId, StopState.SKIPPED)

    /** Undo for skip — but never resurrect a planned stop inside a finished trip. */
    fun restore(stopId: String) {
        if (uiState.value.trip?.status == TripStatus.DONE) return
        setState(stopId, StopState.PLANNED)
    }

    private fun setState(stopId: String, state: StopState) {
        viewModelScope.launch {
            writeLock.withLock {
                val stop = freshStop(stopId) ?: return@withLock
                tripRepository.upsertStop(stop.copy(state = state))
            }
        }
    }

    private suspend fun shiftAfter(orderIndex: Int, days: Long) {
        val stops = tripRepository.stops(tripId).first()
        DateCascade.shift(stops, orderIndex, days).forEach { tripRepository.upsertStop(it) }
    }

    /**
     * Drops the dragged row at [toIndex] and returns the key it carries afterwards —
     * a moved gap is renamed after the stop it now sits in front of. The rows the user
     * sees are the input: a drag has to rearrange what is on screen, not a snapshot
     * the pending writes have already moved on from.
     */
    fun moveRow(key: String, toIndex: Int): String {
        val rows = Timeline.move(uiState.value.rows, key, toIndex) ?: return key
        applyRows(rows)
        return Timeline.keyAt(rows, rows.indexOfFirst { it.key == key })
    }

    /** Resizes a gap; [nights] of 0 deletes it and pulls the rest of the plan forward. */
    fun setGapNights(key: String, nights: Int) {
        applyRows(
            uiState.value.rows.mapNotNull { row ->
                when {
                    row !is GapRow || row.key != key -> row
                    nights <= 0 -> null
                    else -> row.copy(nights = nights.coerceAtMost(365))
                }
            },
        )
    }

    /** Writes a rearranged timeline: the new stop order, then the dates it implies. */
    private fun applyRows(rows: List<TimelineRow>) {
        val start = uiState.value.stops.firstOrNull { it.state != StopState.SKIPPED }?.arrivalDate ?: return
        viewModelScope.launch {
            writeLock.withLock {
                tripRepository.reorderStops(tripId, rows.filterIsInstance<StopRow>().map { it.stop.id })
                DateCascade.resequence(rows, start).forEach { tripRepository.upsertStop(it) }
            }
        }
    }

    fun addVignette(choice: VignetteTable.Choice) {
        val state = uiState.value
        viewModelScope.launch {
            tripRepository.upsertExpense(
                Expense(
                    tripId = tripId,
                    type = ExpenseType.ROAD_TAX,
                    amount = VignetteTable.convert(choice.total, choice.vignette.currency, state.settings.currency),
                    date = state.trip?.startDate ?: LocalDate.now(),
                    label = "${choice.label} (${VignetteTable.YEAR})",
                    isEstimate = true,
                ),
            )
        }
    }

    // Guards the copy-making actions: a double-tap must not mint two trips.
    private var copyInFlight = false

    /** Start the tour; lands on the (possibly new) ACTIVE trip via [onStarted]. */
    fun startTour(startDate: LocalDate, keepPlanAsTemplate: Boolean, onStarted: (String) -> Unit) {
        if (copyInFlight) return
        copyInFlight = true
        viewModelScope.launch {
            try {
                val id = TripStarter.start(
                    tripRepository, tripId, startDate, keepPlanAsTemplate, uiState.value.settings,
                )
                onStarted(id)
            } finally {
                copyInFlight = false
            }
        }
    }

    fun finishTrip() {
        val trip = uiState.value.trip ?: return
        viewModelScope.launch {
            tripRepository.upsertTrip(trip.copy(status = TripStatus.DONE, endDate = LocalDate.now()))
        }
    }

    fun planAgain(onCreated: (String) -> Unit) {
        if (copyInFlight) return
        copyInFlight = true
        viewModelScope.launch {
            try {
                onCreated(TripStarter.planAgain(tripRepository, tripId, LocalDate.now()))
            } finally {
                copyInFlight = false
            }
        }
    }

    companion object {
        val Factory = containerViewModelFactory { container ->
            TripDetailViewModel(
                createSavedStateHandle(),
                container.tripRepository,
                container.settingsRepository,
                container.mapProvider.placeCacheSweeper(container.tripRepository),
            )
        }
    }
}
