package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.Trip

/**
 * A tour does not need a name: it is called after where it goes, and until it goes
 * anywhere by a name made up from its id — the same one every time, so the list does
 * not shuffle, and never stored, so the stops take over the moment there are any.
 */
object TripName {

    private const val MAX_PARTS = 3

    /**
     * "Ticino", "Ticino & Graubünden", "Ticino, Graubünden & Valais": the regions the
     * stops lie in, in the order they are visited. More than three fall back to the
     * countries, and more than three of those end in "& more". Home is where every tour
     * starts, so it never names one; skipped stops were not visited.
     */
    fun region(stops: List<Stop>): String {
        val regions = stops
            .filterNot { it.state == StopState.SKIPPED || it.kind == StopKind.HOME }
            .sortedBy { it.orderIndex }
            .mapNotNull { it.region }
        val names = regions.map { it.name.ifBlank { it.country } }.filter { it.isNotBlank() }.distinct()
        if (names.size <= MAX_PARTS) return join(names)
        val coarse = regions.map { it.country }.filter { it.isNotBlank() }.distinct().ifEmpty { names }
        return join(coarse.take(MAX_PARTS), more = coarse.size > MAX_PARTS)
    }

    private fun join(parts: List<String>, more: Boolean = false): String = when {
        parts.isEmpty() -> ""
        more -> parts.joinToString(", ") + " & more"
        parts.size == 1 -> parts.single()
        else -> parts.dropLast(1).joinToString(", ") + " & " + parts.last()
    }

    /** A name for a tour that goes nowhere yet, the same one every time for the same id. */
    fun generated(id: String): String {
        val hash = id.hashCode()
        return "${ADJECTIVES[hash.mod(ADJECTIVES.size)]} ${NOUNS[(hash shr 4).mod(NOUNS.size)]}"
    }

    private val ADJECTIVES = listOf(
        "Curious", "Quiet", "Lazy", "Sunny", "Windy", "Wandering", "Restless", "Sleepy",
        "Golden", "Misty", "Hungry", "Brave", "Gentle", "Rusty", "Dusty", "Merry",
    )
    private val NOUNS = listOf(
        "Marmot", "Ibex", "Chamois", "Edelweiss", "Glacier", "Larch", "Meadow", "Ridge",
        "Heron", "Fox", "Otter", "Lynx", "Pine", "Comet", "Lantern", "Kettle",
    )
}

/** What a trip is called: its own name, else where it goes, else a made-up one. */
val Trip.title: String
    get() = name.ifBlank { region.ifBlank { TripName.generated(id) } }
