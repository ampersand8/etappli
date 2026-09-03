package com.nuelto.etappli.domain

import com.nuelto.etappli.data.InMemoryTripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.StopRegion
import com.nuelto.etappli.data.model.Trip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionResolverTest {

    private val repo = InMemoryTripRepository(seed = false)
    private val here = LatLng(46.17, 8.79)
    private val there = LatLng(47.0, 7.0)
    private val ticino = StopRegion(here, "Ticino", "Switzerland")

    private suspend fun seed(vararg stops: Stop) {
        repo.upsertTrip(Trip(id = "t1"))
        stops.forEach { repo.upsertStop(it) }
    }

    private suspend fun stop(id: String) = repo.stops("t1").first().first { it.id == id }

    @Test
    fun `due are the located stops with no region, or one looked up for another spot`() {
        val fresh = Stop(id = "a", location = here, region = ticino)
        val moved = Stop(id = "b", location = there, region = ticino)
        val missing = Stop(id = "c", location = here)
        val nowhere = Stop(id = "d")
        assertEquals(listOf(moved, missing), RegionResolver.due(listOf(fresh, moved, missing, nowhere)))
    }

    @Test
    fun `the region is written for the spot it was asked for, and names the trip`() = runTest {
        seed(Stop(id = "s1", tripId = "t1", location = here, orderIndex = 0))
        val asked = mutableListOf<LatLng>()
        val resolver = RegionResolver(repo) { at ->
            asked += at
            StopRegion(name = "Ticino", country = "Switzerland")
        }
        assertEquals(1, resolver.resolve("t1"))
        assertEquals(listOf(here), asked)
        assertEquals(ticino, stop("s1").region)
        assertEquals("Ticino", repo.trip("t1").first()!!.region)
        // Settled: nothing is due any more.
        assertEquals(0, resolver.resolve("t1"))
        assertEquals(1, asked.size)
    }

    @Test
    fun `a lookup that finds nothing leaves the stop alone`() = runTest {
        seed(Stop(id = "s1", tripId = "t1", location = here))
        assertEquals(0, RegionResolver(repo) { null }.resolve("t1"))
        assertNull(stop("s1").region)
    }

    @Test
    fun `a stop moved or deleted while the lookup ran is not written`() = runTest {
        seed(
            Stop(id = "s1", tripId = "t1", location = here, orderIndex = 0),
            Stop(id = "s2", tripId = "t1", location = there, orderIndex = 1),
        )
        val resolver = RegionResolver(repo) { at ->
            if (at == here) repo.upsertStop(stop("s1").copy(location = LatLng(46.0, 6.0))) else repo.deleteStop("t1", "s2")
            StopRegion(name = "Ticino", country = "Switzerland")
        }
        assertEquals(0, resolver.resolve("t1"))
        assertNull(repo.stops("t1").first().single().region)
    }

    @Test
    fun `an edit made during the lookup survives the write`() = runTest {
        seed(Stop(id = "s1", tripId = "t1", location = here, nights = 1))
        val resolver = RegionResolver(repo) {
            repo.upsertStop(stop("s1").copy(nights = 3))
            StopRegion(name = "Ticino", country = "Switzerland")
        }
        assertEquals(1, resolver.resolve("t1"))
        assertEquals(3, stop("s1").nights)
        assertEquals(ticino, stop("s1").region)
    }
}
