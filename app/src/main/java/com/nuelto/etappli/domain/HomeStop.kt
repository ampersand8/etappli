package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.UserSettings
import java.time.LocalDate

/**
 * Where a plan starts from. A new plan opens with the home address as its first stop, so
 * the first leg is the drive out of the door rather than a guess — the distance, the fuel
 * and the map all count it without knowing anything about "home".
 *
 * It is a real [Stop] rather than a setting the rest of the app has to special-case: the
 * only thing that makes it different is its [StopKind], and being a kind means the
 * timeline, the map and the estimator already handle it. That also makes it editable and
 * removable like any other stop — a trip that starts somewhere else just says so.
 */
object HomeStop {

    const val DEFAULT_NAME = "Home"

    /** Null when no home is set, which leaves a new plan starting wherever you add first. */
    fun forNewPlan(tripId: String, settings: UserSettings, startDate: LocalDate): Stop? {
        val location = settings.homeLocation ?: return null
        return Stop(
            tripId = tripId,
            name = settings.homeName.ifBlank { DEFAULT_NAME },
            location = location,
            arrivalDate = startDate,
            nights = 0,
            orderIndex = 0,
            kind = StopKind.HOME,
        )
    }
}
