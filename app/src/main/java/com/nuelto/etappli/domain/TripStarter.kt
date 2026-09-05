package com.nuelto.etappli.domain

import com.nuelto.etappli.data.TripRepository
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.data.model.UserSettings
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first

/**
 * Plan → trip transitions, composed over the TripRepository interface so one
 * implementation serves both backends (no dual-repo parity risk). Reads come from the
 * already-hot flows and writes are the normal fire-and-forget upserts: works offline.
 */
object TripStarter {

    /**
     * Starts a planned tour on [startDate]: its first stop lands on that day and the rest
     * keep their spacing. Snapshots the estimate for planned-vs-actual. With
     * [keepPlanAsTemplate] the plan is deep-copied and survives untouched; otherwise it
     * converts in place. Returns the ACTIVE trip's id.
     */
    suspend fun start(
        repository: TripRepository,
        tripId: String,
        startDate: LocalDate,
        keepPlanAsTemplate: Boolean,
        settings: UserSettings,
    ): String {
        val trip = checkNotNull(repository.trip(tripId).first())
        val stops = repository.stops(tripId).first()
        val expenses = repository.expenses(tripId).first()
        val shift = ChronoUnit.DAYS.between(DateCascade.start(stops) ?: trip.startDate, startDate)
        // A tour starts by leaving home, so the home it sets out from is already reached:
        // the first night's stop is what you are heading for, not your own front door.
        val departed = HomeStop.departure(stops)?.id
        val started = trip.copy(
            status = TripStatus.ACTIVE,
            startDate = startDate,
            endDate = null,
            plannedCost = TripEstimator.estimate(stops, expenses, settings).total,
            plannedNights = CostCalculator.tripNights(stops),
        )

        if (!keepPlanAsTemplate) {
            repository.upsertTrip(started)
            repository.upsertStops(
                stops.mapNotNull { stop ->
                    stop.copy(
                        arrivalDate = stop.arrivalDate.plusDays(shift),
                        state = if (stop.id == departed) StopState.DONE else stop.state,
                    ).takeIf { it != stop }
                },
            )
            return tripId
        }

        // Fresh copies start with clean denormalized totals; the stop/expense upserts
        // below rebuild them (Firestore only recomputes on those mutation paths).
        val newId = repository.upsertTrip(started.copy(id = "", totalCost = 0.0, nights = 0))
        repository.upsertStops(
            stops.map {
                it.copy(
                    id = "",
                    tripId = newId,
                    state = if (it.id == departed) StopState.DONE else StopState.PLANNED,
                    arrivalDate = it.arrivalDate.plusDays(shift),
                    arrivalTime = null,
                )
            },
        )
        // Only planned costs travel to the new trip; stop links would dangle, drop them.
        expenses.filter { it.isEstimate }.forEach {
            repository.upsertExpense(
                it.copy(id = "", tripId = newId, stopId = null, date = it.date.plusDays(shift)),
            )
        }
        return newId
    }

    /**
     * Copies a trip into a fresh plan whose first stop is on [startDate]; skipped stops
     * stay behind. Both copies shift from the first stop, not from [Trip.startDate]: a
     * plan's stops are what the user dated, and the trip's own date may lag behind them.
     */
    suspend fun planAgain(repository: TripRepository, tripId: String, startDate: LocalDate): String {
        val trip = checkNotNull(repository.trip(tripId).first())
        val stops = repository.stops(tripId).first()
        val expenses = repository.expenses(tripId).first()
        val shift = ChronoUnit.DAYS.between(DateCascade.start(stops) ?: trip.startDate, startDate)

        val newId = repository.upsertTrip(
            trip.copy(
                id = "",
                status = TripStatus.PLANNED,
                startDate = startDate,
                endDate = null,
                plannedCost = null,
                plannedNights = null,
                totalCost = 0.0,
                nights = 0,
            ),
        )
        repository.upsertStops(
            stops.filterNot { it.state == StopState.SKIPPED }.map {
                it.copy(
                    id = "",
                    tripId = newId,
                    state = StopState.PLANNED,
                    arrivalDate = it.arrivalDate.plusDays(shift),
                    arrivalTime = null,
                    track = emptyList(),
                )
            },
        )
        expenses.filter { it.isEstimate }.forEach {
            repository.upsertExpense(
                it.copy(id = "", tripId = newId, stopId = null, date = it.date.plusDays(shift)),
            )
        }
        return newId
    }
}
