package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.TripRepository
import com.nuelto.camperexperience.data.model.StopState
import com.nuelto.camperexperience.data.model.TripStatus
import com.nuelto.camperexperience.data.model.UserSettings
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
     * Starts a planned tour on [startDate] (stop dates shift by the same delta) and
     * snapshots the estimate for planned-vs-actual. With [keepPlanAsTemplate] the plan
     * is deep-copied and survives untouched; otherwise it converts in place.
     * Returns the ACTIVE trip's id.
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
        val shift = ChronoUnit.DAYS.between(trip.startDate, startDate)
        val started = trip.copy(
            status = TripStatus.ACTIVE,
            startDate = startDate,
            endDate = null,
            plannedCost = TripEstimator.estimate(stops, expenses, settings).total,
            plannedNights = CostCalculator.tripNights(stops),
        )

        if (!keepPlanAsTemplate) {
            repository.upsertTrip(started)
            if (shift != 0L) {
                stops.forEach {
                    repository.upsertStop(it.copy(arrivalDate = it.arrivalDate.plusDays(shift)))
                }
            }
            return tripId
        }

        val newId = repository.upsertTrip(started.copy(id = ""))
        stops.forEach {
            repository.upsertStop(
                it.copy(
                    id = "",
                    tripId = newId,
                    state = StopState.PLANNED,
                    arrivalDate = it.arrivalDate.plusDays(shift),
                ),
            )
        }
        // Only planned costs travel to the new trip; stop links would dangle, drop them.
        expenses.filter { it.isEstimate }.forEach {
            repository.upsertExpense(
                it.copy(id = "", tripId = newId, stopId = null, date = it.date.plusDays(shift)),
            )
        }
        return newId
    }

    /** Copies a trip into a fresh plan starting [startDate]; skipped stops stay behind. */
    suspend fun planAgain(repository: TripRepository, tripId: String, startDate: LocalDate): String {
        val trip = checkNotNull(repository.trip(tripId).first())
        val stops = repository.stops(tripId).first()
        val expenses = repository.expenses(tripId).first()
        val shift = ChronoUnit.DAYS.between(trip.startDate, startDate)

        val newId = repository.upsertTrip(
            trip.copy(
                id = "",
                status = TripStatus.PLANNED,
                startDate = startDate,
                endDate = null,
                plannedCost = null,
                plannedNights = null,
            ),
        )
        stops.filterNot { it.state == StopState.SKIPPED }.forEach {
            repository.upsertStop(
                it.copy(
                    id = "",
                    tripId = newId,
                    state = StopState.PLANNED,
                    arrivalDate = it.arrivalDate.plusDays(shift),
                ),
            )
        }
        expenses.filter { it.isEstimate }.forEach {
            repository.upsertExpense(
                it.copy(id = "", tripId = newId, stopId = null, date = it.date.plusDays(shift)),
            )
        }
        return newId
    }
}
