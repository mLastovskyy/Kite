package app.kite.core.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Plain-AOSP implementation used when neither GMS nor HMS is present — and the baseline
 * that must keep working on Huawei without GMS. Location comes from LocationManager
 * (less accurate than Fused/Location Kit, kept alive as the fallback, CLAUDE.md).
 *
 * Still stubbed here: push (WebSocket + polling instead), geofencing (M7+ own radius
 * check), URL reputation (local blocklist, M8 — out of scope for now).
 */
class FallbackPlatformServices(private val context: Context) : PlatformServices {
    override val variant: PlatformVariant = PlatformVariant.AOSP

    override suspend fun pushToken(): String? {
        Log.d(TAG, "pushToken: no vendor push on AOSP, delivery falls back to WebSocket + polling")
        return null
    }

    override fun locationUpdates(spec: LocationRequestSpec): Flow<GeoPoint> = callbackFlow {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        // Must not throw when permission or hardware is missing — just emit nothing.
        if (manager == null || !granted) {
            close()
            return@callbackFlow
        }
        val listener =
            LocationListener { location -> trySend(location.toGeoPoint()) }
        val providers =
            buildList {
                if (spec.highAccuracy && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
                if (isEmpty() && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            }
        try {
            providers.forEach { provider ->
                manager.requestLocationUpdates(
                    provider,
                    spec.intervalMillis,
                    spec.minUpdateDistanceMeters,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            // Seed with the last known fix so the map is not empty until the first update.
            providers.firstNotNullOfOrNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                ?.let { trySend(it.toGeoPoint()) }
        } catch (e: SecurityException) {
            Log.w(TAG, "location permission revoked mid-request", e)
            close()
            return@callbackFlow
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    private fun Location.toGeoPoint(): GeoPoint = GeoPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        timestampMillis = time,
    )

    override suspend fun addGeofence(spec: GeofenceSpec): Result<Unit> {
        Log.d(TAG, "addGeofence($spec): stub, own radius check arrives in M7+")
        return Result.success(Unit)
    }

    override suspend fun removeGeofence(id: String): Result<Unit> {
        Log.d(TAG, "removeGeofence($id): stub")
        return Result.success(Unit)
    }

    override suspend fun checkUrl(url: String): UrlVerdict {
        Log.d(TAG, "checkUrl: stub, local blocklist arrives in M8 (package=${context.packageName})")
        return UrlVerdict.UNKNOWN
    }

    private companion object {
        const val TAG = "FallbackPlatform"
    }
}
