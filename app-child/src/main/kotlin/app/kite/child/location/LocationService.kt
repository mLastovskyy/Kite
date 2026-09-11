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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.kite.child.identity.DeviceReporter
import app.kite.child.identity.MemberIdentity
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.DeviceLocationRow
import app.kite.core.location.LocationDao
import app.kite.core.location.LocationPointEntity
import app.kite.core.net.ConnectivityObserver
import app.kite.core.notifications.Channels
import app.kite.core.platform.GeoPoint
import app.kite.core.platform.LocationRequestSpec
import app.kite.core.platform.PlatformServices
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import java.time.Instant
import java.time.format.DateTimeFormatter

class LocationService : Service() {
    private val platformServices: PlatformServices by inject()
    private val locationDao: LocationDao by inject()
    private val remote: DeviceLocationRemote by inject()
    private val identity: MemberIdentity by inject()
    private val trailUploader: TrailUploader by inject()
    private val placesMonitor: PlacesMonitor by inject()
    private val connectivity: ConnectivityObserver by inject()
    private val deviceReporter: DeviceReporter by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val online by lazy { connectivity.online(scope) }
    private val filter = FixFilter()
    private val settleAt = MutableStateFlow<Long?>(null)
    private var lastUploadAt = 0L
    private var lastTrailAt = 0L
    private var lastPlacesRefreshAt = 0L

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch {
            seedFilter()
            uploadLastKnown()
            collect()
        }
        scope.launch { settleLoop() }
        scope.launch {
            refreshPlaces()
            if (online.value) placesMonitor.flushQueue()
            rearmSettle()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LOCATE) scope.launch { locateOnce() }
        return START_STICKY
    }

    private suspend fun seedFilter() {
        locationDao.latest()?.let { filter.seed(it.toFix()) }
    }

    private fun LocationPointEntity.toFix() = AcceptedFix(latitude, longitude, accuracyMeters ?: FixFilter.DEFAULT_ACCURACY_M, recordedAt)

    private suspend fun uploadLastKnown() {
        systemLastKnown()?.let { accept(it) }
        val fix = filter.current ?: return
        runCatching { uploadLatest(fix) }
    }

    private suspend fun locateOnce() {
        var best: GeoPoint? = null
        val point =
            withTimeoutOrNull(LOCATE_TIMEOUT_MS) {
                platformServices.locationUpdates(LocationRequestSpec(intervalMillis = 1_000L, highAccuracy = true))
                    .filter { ageMs(it) <= LOCATE_FRESH_MS }
                    .onEach { candidate ->
                        val current = best
                        if (current == null || accuracyOf(candidate) < accuracyOf(current)) best = candidate
                    }
                    .firstOrNull { accuracyOf(it) <= LOCATE_GOOD_ACCURACY_M }
            } ?: best
        val fix = point?.let { accept(it) }
        if (fix != null) {
            lastUploadAt = System.currentTimeMillis()
            runCatching { uploadLatest(fix) }
        }
        runCatching { deviceReporter.report() }
    }

    private suspend fun collect(): Unit = coroutineScope {
        var interval = INTERVAL_MS
        while (isActive) {
            val nextInterval = CompletableDeferred<Long>()
            val job =
                launch {
                    collectAt(interval) { desired -> if (!nextInterval.isCompleted) nextInterval.complete(desired) }
                    if (!nextInterval.isCompleted) {
                        delay(RETRY_MS)
                        nextInterval.complete(interval)
                    }
                }
            interval = nextInterval.await()
            job.cancelAndJoin()
        }
    }

    private suspend fun collectAt(interval: Long, onIntervalChange: (Long) -> Unit) {
        val spec = LocationRequestSpec(intervalMillis = interval, minUpdateDistanceMeters = MIN_DISTANCE_M, highAccuracy = true)
        platformServices.locationUpdates(spec).collect { point ->
            val fix = accept(point) ?: return@collect
            report(fix)
            val desired = if (nearPlace(fix, fastNow = interval == NEAR_PLACE_INTERVAL_MS)) NEAR_PLACE_INTERVAL_MS else INTERVAL_MS
            if (desired != interval) onIntervalChange(desired)
        }
    }

    private suspend fun accept(point: GeoPoint): AcceptedFix? = when (val decision = filter.offer(point, ageMs(point))) {
        is FixDecision.Moved -> decision.fix.also { store(it) }
        is FixDecision.Stayed -> decision.fix
        FixDecision.Dropped -> null
    }

    private suspend fun store(fix: AcceptedFix) {
        locationDao.insert(
            LocationPointEntity(
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyM,
                recordedAt = fix.recordedAt,
            ),
        )
        locationDao.purgeBefore(System.currentTimeMillis() - RETENTION_MS)
    }

    private suspend fun report(fix: AcceptedFix) {
        val now = System.currentTimeMillis()
        val isOnline = online.value
        if (now - lastUploadAt >= UPLOAD_THROTTLE_MS) {
            lastUploadAt = now
            runCatching { uploadLatest(fix) }
        }
        placesMonitor.onFix(fix, isOnline)
        rearmSettle()
        if (isOnline && now - lastPlacesRefreshAt >= PLACES_REFRESH_MS) {
            refreshPlaces()
            placesMonitor.flushQueue()
        }
        if (isOnline && now - lastTrailAt >= TRAIL_BATCH_MS) {
            lastTrailAt = now
            runCatching { trailUploader.uploadPending() }
        }
    }

    private fun nearPlace(fix: AcceptedFix, fastNow: Boolean): Boolean {
        val toBoundary = placesMonitor.metersToNearestBoundary(fix.latitude, fix.longitude) ?: return false
        return when {
            toBoundary < NEAR_PLACE_METERS -> true
            toBoundary > FAR_PLACE_METERS -> false
            else -> fastNow
        }
    }

    private suspend fun settleLoop() {
        settleAt.collectLatest { at ->
            if (at == null) return@collectLatest
            delay((at - System.currentTimeMillis()).coerceAtLeast(0L))
            placesMonitor.settle(online.value)
            settleAt.value = null
            rearmSettle()
        }
    }

    private fun rearmSettle() {
        settleAt.value = placesMonitor.nextDeadline()
    }

    private fun systemLastKnown(): GeoPoint? = runCatching {
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
            ?.let {
                GeoPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    accuracyMeters = if (it.hasAccuracy()) it.accuracy else null,
                    timestampMillis = it.time,
                    elapsedRealtimeNanos = it.elapsedRealtimeNanos,
                )
            }
    }.getOrNull()

    private fun ageMs(point: GeoPoint): Long =
        if (point.elapsedRealtimeNanos == 0L) 0L else (SystemClock.elapsedRealtimeNanos() - point.elapsedRealtimeNanos) / 1_000_000

    private fun accuracyOf(point: GeoPoint): Float = point.accuracyMeters ?: FixFilter.DEFAULT_ACCURACY_M

    private suspend fun refreshPlaces() {
        lastPlacesRefreshAt = System.currentTimeMillis()
        runCatching { placesMonitor.refresh() }
    }

    private suspend fun uploadLatest(fix: AcceptedFix) {
        val familyId = identity.familyId() ?: return
        val memberId = identity.memberId() ?: return
        remote.upsert(
            DeviceLocationRow(
                memberId = memberId,
                familyId = familyId,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyM = fix.accuracyM,
                batteryPct = batteryPercent(),
                recordedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(fix.recordedAt)),
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
        private const val INTERVAL_MS = 2 * 60_000L
        private const val MIN_DISTANCE_M = 25f
        private const val NEAR_PLACE_INTERVAL_MS = 30_000L
        private const val NEAR_PLACE_METERS = 400.0
        private const val FAR_PLACE_METERS = 600.0
        private const val UPLOAD_THROTTLE_MS = 2 * 60_000L
        private const val TRAIL_BATCH_MS = 3 * 60_000L
        private const val PLACES_REFRESH_MS = 15 * 60_000L
        private const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000
        private const val RETRY_MS = 60_000L
        private const val LOCATE_TIMEOUT_MS = 30_000L
        private const val LOCATE_FRESH_MS = 30_000L
        private const val LOCATE_GOOD_ACCURACY_M = 50f
        private const val ACTION_LOCATE = "app.kite.child.action.LOCATE"

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun requestFixNow(context: Context) {
            val intent = Intent(context, LocationService::class.java).setAction(ACTION_LOCATE)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }
    }
}
