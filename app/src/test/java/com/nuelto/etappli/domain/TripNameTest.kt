package com.nuelto.etappli.domain

import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopKind
import com.nuelto.etappli.data.model.StopRegion
import com.nuelto.etappli.data.model.StopState
import com.nuelto.etappli.data.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Test

class TripNameTest {

    private fun region(name: String, country: String = "Switzerland") =
        StopRegion(LatLng(46.0, 8.0), name, country)

    private fun stop(
        order: Int,
        region: StopRegion?,
        kind: StopKind = StopKind.CAMPSITE,
        state: StopState = StopState.PLANNED,
    ) = Stop(id = "s$order", orderIndex = order, kind = kind, state = state, region = region)

    @Test
    fun `nothing to go on is no name`() {
        assertEquals("", TripName.region(emptyList()))
        assertEquals("", TripName.region(listOf(stop(0, null), stop(1, region("", "")))))
    }

    @Test
    fun `regions are listed in visiting order, without repeats`() {
        assertEquals("Ticino", TripName.region(listOf(stop(0, region("Ticino")))))
        val stops = listOf(
            stop(2, region("Valais")),
            stop(0, region("Ticino")),
            stop(1, region("Graubünden")),
            stop(3, region("Ticino")),
        )
        assertEquals("Ticino, Graubünden & Valais", TripName.region(stops))
        assertEquals("Ticino & Graubünden", TripName.region(stops.drop(0).filter { it.orderIndex != 2 }))
    }

    @Test
    fun `more than three regions fall back to the countries`() {
        val swiss = listOf("Ticino", "Graubünden", "Valais", "Bern").mapIndexed { i, r -> stop(i, region(r)) }
        assertEquals("Switzerland", TripName.region(swiss))
        assertEquals("Switzerland & Italy", TripName.region(swiss + stop(4, region("Lombardia", "Italy"))))
        val three = swiss + stop(4, region("Lombardia", "Italy")) + stop(5, region("Savoie", "France"))
        assertEquals("Switzerland, Italy & France", TripName.region(three))
        val grand = swiss + listOf("Italy", "France", "Spain").mapIndexed { i, c -> stop(4 + i, region("R$i", c)) }
        assertEquals("Switzerland, Italy, France & more", TripName.region(grand))
        // Nowhere to fall back to: the regions themselves, cut short.
        val stateless = listOf("A", "B", "C", "D").mapIndexed { i, r -> stop(i, region(r, "")) }
        assertEquals("A, B, C & more", TripName.region(stateless))
    }

    @Test
    fun `home and skipped stops never name a tour, and a nameless region is its country`() {
        val stops = listOf(
            stop(0, region("Luzern"), kind = StopKind.HOME),
            stop(1, region("Ticino")),
            stop(2, region("", "Italy")),
            stop(3, region("Valais"), state = StopState.SKIPPED),
            stop(4, region("Luzern"), kind = StopKind.HOME),
        )
        assertEquals("Ticino & Italy", TripName.region(stops))
    }

    @Test
    fun `a made-up name follows from the id alone`() {
        assertEquals("Curious Marmot", TripName.generated(""))
        assertEquals("Rusty Edelweiss", TripName.generated("t1"))
        assertEquals("Dusty Edelweiss", TripName.generated("t2"))
        assertEquals("Windy Meadow", TripName.generated("demo-jura"))
        // A negative hash still lands in the lists.
        assertEquals("Sunny Fox", TripName.generated("plan-7"))
        assertEquals("Gentle Lynx", TripName.generated("Ticino"))
    }

    @Test
    fun `the title is the name, else where it goes, else made up`() {
        assertEquals("Jura", Trip(id = "t1", name = "Jura", region = "Ticino").title)
        assertEquals("Ticino", Trip(id = "t1", name = " ", region = "Ticino").title)
        assertEquals("Rusty Edelweiss", Trip(id = "t1").title)
    }
}
