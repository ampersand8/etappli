package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.UserSettings
import java.time.LocalDate

/**
 * Where a plan starts and ends. A new plan opens with the home address as its first stop
 * and as its last, so the first leg is the drive out of the door and the last the drive
 * back — the distance, the fuel and the map count both without knowing anything about
 * "home".
 *
 * It is a real [Stop] rather than a setting the rest of the app has to special-case: the
 * only thing that makes it different is its [StopKind], and being a kind means the
 * timeline, the map and the estimator already handle it. That also makes it editable and
 * removable like any other stop — a trip that starts or ends somewhere else just says so.
 */
object HomeStop {

    const val DEFAULT_NAME = "Home"

    fun name(settings: UserSettings): String = settings.homeName.ifBlank { DEFAULT_NAME }

    /**
     * The stops a new plan opens with: home to set out from, and home to come back to.
     * Empty when no home is set, which leaves the plan starting wherever you add first.
     */
    fun forNewPlan(tripId: String, settings: UserSettings, startDate: LocalDate): List<Stop> {
        val location = settings.homeLocation ?: return emptyList()
        val home = Stop(
            tripId = tripId,
            name = name(settings),
            location = location,
            arrivalDate = startDate,
            nights = 0,
            orderIndex = 0,
            kind = StopKind.HOME,
        )
        return listOf(home, home.copy(orderIndex = 1))
    }

    /** The home a plan sets out from: its first stop, when that is a home. */
    fun departure(stops: List<Stop>): Stop? =
        stops.minByOrNull { it.orderIndex }?.takeIf { it.kind == StopKind.HOME }

    /**
     * The drive home a plan still ends with: its last stop, when that is a home not yet
     * reached with something in front of it. A lone home is where a plan starts, not
     * where it ends, so stops added to that plan follow it.
     */
    fun returning(stops: List<Stop>): Stop? {
        val last = stops.maxByOrNull { it.orderIndex } ?: return null
        val ahead = stops.any { it.orderIndex < last.orderIndex }
        return last.takeIf { it.kind == StopKind.HOME && it.state == StopState.PLANNED && ahead }
    }
}
