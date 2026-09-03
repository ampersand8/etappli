package com.nuelto.etappli.domain

import com.nuelto.etappli.data.TripRepository
import com.nuelto.etappli.data.model.Trip
import com.nuelto.etappli.data.model.TripStatus
import com.nuelto.etappli.data.model.UserSettings
import java.time.LocalDate

/**
 * A plan made on the spot, with no form in the way: "Plan a tour" opens the stop editor.
 * It has no name until its stops say where it goes (TripName), starts today until its
 * first stop says when (the repositories re-date it), and sets out from home and comes
 * back to it when a home is set (HomeStop). All of that can be edited afterwards.
 */
object NewPlan {

    /** Returns the new plan's id. */
    suspend fun create(
        repository: TripRepository,
        settings: UserSettings,
        today: LocalDate = LocalDate.now(),
    ): String {
        val id = repository.upsertTrip(Trip(startDate = today, status = TripStatus.PLANNED))
        repository.upsertStops(HomeStop.forNewPlan(id, settings, today))
        return id
    }
}
