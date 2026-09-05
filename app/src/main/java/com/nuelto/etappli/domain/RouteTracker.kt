package com.nuelto.etappli.domain

import com.nuelto.etappli.data.TripRepository
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.data.model.Stop
import com.nuelto.etappli.data.model.TripStatus
import java.time.ZonedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The stop an active trip is driving to: which trip, where the vehicle is actually driven
 * to ([target], see Tracks.target), and whether the tour has started — before its start
 * date the drive is not underway, so nothing is recorded and no service runs, though the
 * distance still reads.
 */
data class Heading(val tripId: String, val stop: Stop, val target: LatLng?, val underway: Boolean)

/**
 * Follows the drive underway on the active trip: every GPS fix goes onto the track of the
 * stop being headed for and, throttled by [LiveDrive], buys a route from there to it —
 * what the NowCard and the tracking notification both show. Standing at the stop for
 * [Stay.CHECK_IN_AFTER] checks you in. One per app (AppContainer): the foreground service
 * feeds it fixes in the background, the timeline feeds it one when it opens, and both read
 * the same answer.
 */
class RouteTracker(
    private val tripRepository: TripRepository,
    private val drive: suspend (from: LatLng, to: LatLng) -> RoutedLeg? = { _, _ -> null },
    private val now: () -> ZonedDateTime = { ZonedDateTime.now() },
) {
    data class State(
        // How far the target is from the last fix; null until routed, and again once
        // there. Only ever shown against the heading it was asked for: readers compare
        // [DriveFromHere.to].
        val drive: DriveFromHere? = null,
        // The last fix handed in, recorded or not — changes with every fix, so the
        // service can re-post.
        val lastFix: LatLng? = null,
        // The trip whose drive the background service is recording; null while it is not running.
        val recordingTripId: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val lock = Mutex()

    // Where the last route was asked from and to, whatever came of it: a failed ask must
    // not be repeated on every fix.
    private var askedFrom: LatLng? = null
    private var askedTo: LatLng? = null

    // The stop already reached: what the phone does after that — the ride up from the
    // parking spot, the run to the shop from the campsite — is not the drive.
    private var arrivedFor: String? = null

    // The fixes at the stop being headed for so far: Stay.CHECK_IN_AFTER of them is arriving.
    private var dwell: Stay.Dwell? = null

    // A stop not to check in at until a fix has left it: the one just checked in, so that
    // taking that back is not undone by the next fix, or the one the user took back.
    private var heldOff: String? = null

    /**
     * Where the active trip is heading, re-evaluated as trips and stops change: null
     * between check-in and check-out and with no active trip. Two active trips: the one
     * started last.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun heading(): Flow<Heading?> = tripRepository.trips()
        .map { trips -> trips.filter { it.status == TripStatus.ACTIVE }.maxByOrNull { it.startDate } }
        // Totals and the region change under a trip without moving its heading.
        .distinctUntilChanged { a, b -> a?.id == b?.id && a?.startDate == b?.startDate }
        .flatMapLatest { trip ->
            if (trip == null) return@flatMapLatest flowOf(null)
            tripRepository.stops(trip.id).map { stops ->
                val today = now().toLocalDate()
                CurrentStop.heading(stops, today)?.let {
                    Heading(trip.id, it, Tracks.target(stops, it), underway = !today.isBefore(trip.startDate))
                }
            }
        }

    /**
     * One fix from the phone. Onto the track (only while underway, and nothing from the
     * arrival fix on), then whether you have arrived for good, then how far the target
     * still is. [tripId] from a screen keeps a fix off another active trip's drive; the
     * service passes none. Serialized: the service and a screen can both deliver one.
     */
    suspend fun fix(point: LatLng, tripId: String? = null) {
        lock.withLock {
            val at = now()
            val heading = heading().first()
            if (heading == null) {
                _state.update { it.copy(drive = null, lastFix = point) }
                return
            }
            if (tripId != null && heading.tripId != tripId) return
            val target = heading.target
            if (target != null && LiveDrive.arrived(point, target)) arrivedFor = heading.stop.id
            val arrived = arrivedFor == heading.stop.id
            if (heading.underway && !arrived && Tracks.accepts(heading.stop.track, point)) {
                tripRepository.appendTrack(heading.tripId, heading.stop.id, point)
            }
            _state.update { it.copy(lastFix = point) }
            // Before the arrival return below: the fixes that check you in are the ones inside it.
            if (stay(heading, point, at)) {
                _state.update { it.copy(drive = null) }
                return
            }
            if (target == null || arrived) {
                _state.update { it.copy(drive = null) }
                return
            }
            if (!LiveDrive.needsFetch(askedFrom, askedTo, point, target)) return
            askedFrom = point
            askedTo = target
            // A route that was due and did not come back leaves nothing behind: an answer
            // from kilometres back would be wrong now.
            val leg = drive(point, target)
            _state.update { state ->
                state.copy(drive = leg?.let { DriveFromHere(point, target, it.distanceMeters, it.durationSeconds) })
            }
        }
    }

    /**
     * The dwell that checks you in: [Stay.CHECK_IN_AFTER] of fixes at the stop being headed
     * for (Stay.near), on a drive underway — stamped with the first of them, which is when
     * you got there. True once the check-in is written.
     */
    private suspend fun stay(heading: Heading, point: LatLng, at: ZonedDateTime): Boolean {
        val stop = heading.stop
        val left = Stay.left(point, stop)
        if (heldOff == stop.id && left) heldOff = null
        if (!heading.underway || heldOff == stop.id || left) {
            dwell = null
            return false
        }
        // Between near and left, or not yet the stop's day: neither at it nor away from it.
        if (!Stay.near(point, stop, at.toLocalDate())) return false
        val here = Stay.dwell(dwell, stop.id, at).also { dwell = it }
        if (!here.checksIn) return false
        val stops = tripRepository.stops(heading.tripId).first()
        // The write ends the drive, which stops the service, which cancels the coroutine
        // this runs in: the repository must still get to settle the plan behind the write.
        withContext(NonCancellable) {
            tripRepository.upsertStops(Stay.checkIn(stops, stop.id, here.since.toLocalDateTime()))
        }
        // The next fix starts over by itself: no heading, or this stop held off.
        heldOff = stop.id
        return true
    }

    /** You are not at [stopId] after all: no check-in there until a fix has left it, and the drive to it is on again. */
    suspend fun holdOff(stopId: String) = lock.withLock {
        heldOff = stopId
        arrivedFor = null
    }

    /** The service reports itself. */
    fun recording(tripId: String?) = _state.update { it.copy(recordingTripId = tripId) }
}
