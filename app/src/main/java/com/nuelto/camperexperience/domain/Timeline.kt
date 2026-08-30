package com.nuelto.camperexperience.domain

import com.nuelto.camperexperience.data.model.Stop
import com.nuelto.camperexperience.data.model.StopState
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A row of the trip timeline: a stop, or the nights nobody planned in front of one. */
sealed interface TimelineRow {
    val key: String

    /** The stop the row hangs off — itself, or the one a gap sits in front of. */
    val anchor: Stop
}

data class StopRow(val stop: Stop) : TimelineRow {
    override val key: String get() = stop.id
    override val anchor: Stop get() = stop
}

/** [nights] with nothing planned, from [from] until [before] is reached. */
data class GapRow(val nights: Int, val from: LocalDate, val before: Stop) : TimelineRow {
    override val key: String get() = key(before.id)
    override val anchor: Stop get() = before

    companion object {
        fun key(stopId: String) = "gap-$stopId"
    }
}

/**
 * The timeline as the screen shows it. Gaps are derived from the dates, never stored:
 * every empty night between one stop's departure and the next one's arrival becomes a
 * row of its own, so nothing in a plan is unaccounted for.
 */
object Timeline {

    fun rows(stops: List<Stop>): List<TimelineRow> {
        val rows = mutableListOf<TimelineRow>()
        var departure: LocalDate? = null
        for (stop in stops.sortedBy { it.orderIndex }) {
            // Skipped stops keep their place in the list but not in the schedule.
            if (stop.state == StopState.SKIPPED) {
                rows += StopRow(stop)
                continue
            }
            val from = departure
            if (from != null) {
                val nights = ChronoUnit.DAYS.between(from, stop.arrivalDate)
                if (nights > 0) rows += GapRow(nights.toInt(), from, stop)
            }
            rows += StopRow(stop)
            departure = stop.arrivalDate.plusDays(stop.nights.toLong())
        }
        return rows
    }

    /**
     * The row order after dropping [key] at [toIndex], or null if nothing moves.
     * DONE stops are anchors — history can't be corrupted by a drag — so a row only
     * moves inside the window between the nearest done row above and below it, and
     * gaps additionally stay between two stops.
     */
    fun move(rows: List<TimelineRow>, key: String, toIndex: Int): List<TimelineRow>? {
        val from = rows.indexOfFirst { it.key == key }
        if (from < 0 || locked(rows[from])) return null
        val rest = rows.toMutableList()
        val row = rest.removeAt(from)
        val window = window(rest, from, row) ?: return null
        val to = toIndex.coerceIn(window)
        if (to == from) return null
        rest.add(to, row)
        return rest
    }

    /** The key the row dropped at [index] carries once the timeline is redrawn. */
    fun keyAt(rows: List<TimelineRow>, index: Int): String = when (val row = rows[index]) {
        is StopRow -> row.key
        // Gaps are named after the stop they precede — and a gap dropped in front of
        // another one merges into it, under that same name.
        is GapRow -> GapRow.key(rows.drop(index + 1).filterIsInstance<StopRow>().first().stop.id)
    }

    private fun locked(row: TimelineRow) = row.anchor.state == StopState.DONE

    /** Drop positions open to [row], counted in the list it was lifted out of. */
    private fun window(rest: List<TimelineRow>, from: Int, row: TimelineRow): IntRange? {
        var lower = (from - 1 downTo 0).firstOrNull { locked(rest[it]) }?.plus(1) ?: 0
        var upper = (from..rest.lastIndex).firstOrNull { locked(rest[it]) } ?: rest.size
        if (row is GapRow) {
            lower = maxOf(lower, rest.indexOfFirst { it is StopRow } + 1)
            upper = minOf(upper, rest.indexOfLast { it is StopRow })
        }
        return if (lower > upper) null else lower..upper
    }
}
