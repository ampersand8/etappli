package com.nuelto.etappli.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.nuelto.etappli.CamperApp
import com.nuelto.etappli.MainActivity
import com.nuelto.etappli.R
import com.nuelto.etappli.data.model.LatLng
import com.nuelto.etappli.domain.DriveFromHere
import com.nuelto.etappli.domain.Heading
import com.nuelto.etappli.domain.RouteTracker
import com.nuelto.etappli.domain.Tracks
import com.nuelto.etappli.ui.formatTrackingText
import com.nuelto.etappli.ui.formatTrackingTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Records the drive underway: a GPS fix every [Tracks.FIX_INTERVAL_MS] into the
 * container's [RouteTracker], under an ongoing notification that says how far the stop
 * you are heading for still is. Started while the app is open (MainActivity's
 * TrackingTrigger, the NowCard) — Android 12+ refuses a location service started from
 * the background, and so does this one: no background-location permission, no
 * WorkManager, no boot receiver, not sticky. Stops itself once nothing is underway.
 */
class TrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tracker: RouteTracker get() = (application as CamperApp).container.tracker
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var started = false
    // What the notification shows now: a start landing on a running service must not blank it.
    private var shown: Notification? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            // One coroutine per batch keeps the fixes in the order they were taken.
            scope.launch { result.locations.forEach { runCatching { tracker.fix(LatLng(it.latitude, it.longitude)) } } }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Trip tracking")
                .build(),
        )
    }

    @SuppressLint("MissingPermission") // start() checks it.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val foreground = runCatching {
            ServiceCompat.startForeground(this, ID, shown ?: notification(null, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        }
        if (intent == null || foreground.isFailure) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            client.requestLocationUpdates(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, Tracks.FIX_INTERVAL_MS)
                    .setMinUpdateIntervalMillis(Tracks.FIX_INTERVAL_MS / 2)
                    .setMinUpdateDistanceMeters(Tracks.MIN_MOVE_METERS.toFloat())
                    .build(),
                callback,
                Looper.getMainLooper(),
            )
            scope.launch {
                runCatching {
                    combine(tracker.heading(), tracker.state) { heading, state -> heading to state }
                        .collect { (heading, state) ->
                            if (heading == null || !heading.underway) {
                                stopSelf()
                            } else {
                                tracker.recording(heading.tripId)
                                notify(heading, state.drive?.takeIf { it.to == heading.target })
                            }
                        }
                }.onFailure { if (it !is CancellationException) stopSelf() } // signing out makes the repository throw
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        scope.cancel()
        tracker.recording(null)
        super.onDestroy()
    }

    /** Re-posted on every state emission: each fix changes lastFix, so the text is at most one fix old. */
    private fun notify(heading: Heading, drive: DriveFromHere?) {
        val notification = notification(heading, drive).also { shown = it }
        runCatching { NotificationManagerCompat.from(this).notify(ID, notification) }
    }

    private fun notification(heading: Heading?, drive: DriveFromHere?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_tracking)
            .setContentTitle(heading?.let { formatTrackingTitle(it.stop.name) } ?: "Trip tracking")
            .setContentText(formatTrackingText(drive))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val ID = 1
        private const val CHANNEL = "tracking"

        /**
         * Starts tracking if precise location is granted — the product gate: a coarse-only
         * grant gives a 2 km grid, no track. Failing to start (the app is in the background
         * on 12+) is silent; the next foreground asks again.
         */
        fun start(context: Context) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java)) }
        }
    }
}
