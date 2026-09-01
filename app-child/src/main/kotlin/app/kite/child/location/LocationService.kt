package app.kite.child.location

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.kite.child.identity.MemberIdentity
import app.kite.child.notifications.Channels
import app.kite.core.location.DeviceLocationRemote
import app.kite.core.location.DeviceLocationRow
import app.kite.core.location.LocationDao
import app.kite.core.location.LocationPointEntity
import app.kite.core.platform.LocationRequestSpec
import app.kite.core.platform.PlatformServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastUploadAt = 0L

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        scope.launch { collect() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private suspend fun collect() {
        val spec = LocationRequestSpec(intervalMillis = INTERVAL_MS, minUpdateDistanceMeters = MIN_DISTANCE_M, highAccuracy = true)
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
            if (now - lastUploadAt >= UPLOAD_THROTTLE_MS) {
                lastUploadAt = now
                uploadLatest(point.latitude, point.longitude, point.accuracyMeters, point.timestampMillis)
            }
        }
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
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
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
        private const val INTERVAL_MS = 60_000L
        private const val MIN_DISTANCE_M = 25f
        private const val UPLOAD_THROTTLE_MS = 60_000L
        private const val RETENTION_MS = 90L * 24 * 60 * 60 * 1000

        fun start(context: Context) {
            val intent = Intent(context, LocationService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
