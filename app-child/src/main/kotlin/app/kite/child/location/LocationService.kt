package app.kite.child.location

import android.Manifest
import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.kite.child.identity.MemberIdentity
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.DeviceLocationRow
import app.kite.core.location.LocationDao
import app.kite.core.location.LocationPointEntity
import app.kite.core.net.ConnectivityObserver
import app.kite.core.notifications.Channels
import app.kite.core.platform.LocationRequestSpec
import app.kite.core.platform.PlatformServices
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Foreground service that records the child's location (M7). Every fix goes into Room (the
 * full history stays on the device) and the latest is upserted to the server for the
 * parent's live map, throttled so we do not hammer the free tier. foregroundServiceType
 * must be "location" or Android 14+ kills it; its notification cannot be hidden.
 */
class LocationService : Service() {
    private val platformServices: PlatformServices by inject()
    private val locationDao: LocationDao by inject()
    private val remote: DeviceLocationRemote by inject()
    private val identity: MemberIdentity by inject()
    private val trailUploader: TrailUploader by inject()
    private val placesMonitor: PlacesMonitor by inject()
    private val connectivity: ConnectivityObserver by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val online by lazy { connectivity.online(scope) }
    private var lastUploadAt = 0L
    private var lastTrailAt = 0L
    private var lastPlacesRefreshAt = 0L

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch { collect() }
        // Places must be current before the first fix is judged, and anything that happened
        // while the device was offline still owes the parent a notification.
        scope.launch {
            refreshPlaces()
            if (online.value) placesMonitor.flushQueue()
        }
        scope.launch { uploadLastKnown() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LOCATE) scope.launch { locateOnce() }
        return START_STICKY
    }

    /**
     * The parent tapped «Обновить»: take ONE fresh high-accuracy fix and upload it right away,
     * bypassing the 5-minute throttle. Bounded to 30 s so a phone in a basement does not keep
     * the GPS on; the regular cadence is untouched.
     */
    private suspend fun locateOnce() {
        val point =
            kotlinx.coroutines.withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
                platformServices.locationUpdates(LocationRequestSpec(intervalMillis = 1_000L, highAccuracy = true)).first()
            } ?: return
        locationDao.insert(
            LocationPointEntity(
                latitude = point.latitude,
                longitude = point.longitude,
                accuracyMeters = point.accuracyMeters,
                recordedAt = point.timestampMillis,
            ),
        )
        lastUploadAt = System.currentTimeMillis()
        runCatching { uploadLatest(point.latitude, point.longitude, point.accuracyMeters, point.timestampMillis) }
    }

    /**
     * Fixes come at [INTERVAL_MS] normally and at [NEAR_PLACE_INTERVAL_MS] while the phone is
     * within [NEAR_PLACE_METERS] of a saved place — an enter or exit is then reported in half a
     * minute instead of two, and the fast rate costs nothing anywhere else in town. The flow is
     * restarted (not merely throttled) so the OS actually changes how often it wakes the GPS.
     */
    private suspend fun collect(): Unit = coroutineScope {
        var interval = INTERVAL_MS
        while (isActive) {
            val nextInterval = CompletableDeferred<Long>()
            val job = launch { collectAt(interval) { desired -> if (!nextInterval.isCompleted) nextInterval.complete(desired) } }
            interval = nextInterval.await()
            job.cancelAndJoin()
        }
    }

    private suspend fun collectAt(interval: Long, onIntervalChange: (Long) -> Unit) {
        val spec = LocationRequestSpec(intervalMillis = interval, minUpdateDistanceMeters = MIN_DISTANCE_M, highAccuracy = true)
        platformServices.locationUpdates(spec).collectLatest { point ->
            locationDao.insert(
                LocationPointEntity(
                    latitude = point.latitude,
                    longitude = point.longitude,
                    accuracyMeters = point.accuracyMeters,
                    recordedAt = point.timestampMillis,
                ),
            )
            locationDao.purgeBefore(System.currentTimeMillis() - RETENTION_MS)

            val now = System.currentTimeMillis()
            val isOnline = online.value
            if (now - lastUploadAt >= UPLOAD_THROTTLE_MS) {
                lastUploadAt = now
                uploadLatest(point.latitude, point.longitude, point.accuracyMeters, point.timestampMillis)
            }
            // «Места»: judged on every fix, offline included — the event is queued if needed.
            placesMonitor.onFix(point.latitude, point.longitude, point.accuracyMeters, isOnline)
            if (isOnline && now - lastPlacesRefreshAt >= PLACES_REFRESH_MS) {
                lastPlacesRefreshAt = now
                refreshPlaces()
                placesMonitor.flushQueue()
            }
            // «Маршруты»: batched, online only; the synced flag in Room is the offline queue.
            if (isOnline && now - lastTrailAt >= TRAIL_BATCH_MS) {
                lastTrailAt = now
                runCatching { trailUploader.uploadPending() }
            }
            val nearPlace = placesMonitor.metersToNearestBoundary(point.latitude, point.longitude)?.let { it < NEAR_PLACE_METERS }
            val desired = if (nearPlace == true) NEAR_PLACE_INTERVAL_MS else INTERVAL_MS
            if (desired != interval) onIntervalChange(desired)
        }
    }

    private suspend fun uploadLastKnown() {
        val stored = locationDao.latest()
        val system = systemLastKnown()
        val newest =
            listOfNotNull(
                stored?.let { Triple(it.latitude, it.longitude, it.recordedAt) to it.accuracyMeters },
                system,
            ).maxByOrNull { it.first.third } ?: return
        val (position, accuracy) = newest
        runCatching { uploadLatest(position.first, position.second, accuracy, position.third) }
    }

    private fun systemLastKnown(): Pair<Triple<Double, Double, Long>, Float?>? = runCatching {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        manager.allProviders
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { Triple(it.latitude, it.longitude, it.time) to it.accuracy }
    }.getOrNull()

    private suspend fun refreshPlaces() {
        lastPlacesRefreshAt = System.currentTimeMillis()
        runCatching { placesMonitor.refresh() }
    }

    private suspend fun uploadLatest(lat: Double, lon: Double, accuracy: Float?, recordedAt: Long) {
        val familyId = identity.familyId() ?: return
        val memberId = identity.memberId() ?: return
        remote.upsert(
            DeviceLocationRow(
                memberId = memberId,
                familyId = familyId,
                latitude = lat,
                longitude = lon,
                accuracyM = accuracy,
                batteryPct = batteryPercent(),
                recordedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(recordedAt)),
            ),
        )
    }

    private fun batteryPercent(): Int? = (getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, Channels.SERVICE)
        .setSmallIcon(app.kite.core.R.drawable.ic_notification)
        .setContentTitle("Kite Jr")
        .setContentText("Защита активна")
        .setOngoing(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 42

        /**
         * Fix cadence, by the owner's call (05.09.2026): ~2 minutes. Fresh enough that the
         * map is not visibly stale, still far from a continuous stream — waking the GPS every
         * minute is the single biggest battery complaint about apps of this kind. The distance
         * filter suppresses fixes from a phone lying still, so a resting phone costs nothing.
         */
        private const val INTERVAL_MS = 2 * 60_000L
        private const val MIN_DISTANCE_M = 25f
        private const val NEAR_PLACE_INTERVAL_MS = 30_000L
        private const val NEAR_PLACE_METERS = 400.0
        private const val UPLOAD_THROTTLE_MS = 2 * 60_000L
        private const val TRAIL_BATCH_MS = 3 * 60_000L
        private const val PLACES_REFRESH_MS = 15 * 60_000L
        private const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
        private const val LOCATE_TIMEOUT_MS = 30_000L
        private const val ACTION_LOCATE = "app.kite.child.action.LOCATE"

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        /** `locate` command from the parent: one fresh fix now (starts the service if needed). */
        fun requestFixNow(context: Context) {
            val intent = Intent(context, LocationService::class.java).setAction(ACTION_LOCATE)
            runCatching { androidx.core.content.ContextCompat.startForegroundService(context, intent) }
        }
    }
}
