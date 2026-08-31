package app.kite.core.platform

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Plain-AOSP implementation used when neither GMS nor HMS is present. The app must degrade
 * gracefully and never crash without vendor services.
 *
 * M1: logging stub. Real logic arrives later: LocationManager + own radius check in M7,
 * local blocklist in M8, WebSocket + polling instead of push in M3/M5.
 */
class FallbackPlatformServices(private val context: Context) : PlatformServices {
    override val variant: PlatformVariant = PlatformVariant.AOSP

    override suspend fun pushToken(): String? {
        Log.d(TAG, "pushToken: no vendor push on AOSP, delivery falls back to WebSocket + polling")
        return null
    }

    override fun locationUpdates(spec: LocationRequestSpec): Flow<GeoPoint> {
        Log.d(TAG, "locationUpdates($spec): stub, LocationManager implementation arrives in M7")
        return emptyFlow()
    }

    override suspend fun addGeofence(spec: GeofenceSpec): Result<Unit> {
        Log.d(TAG, "addGeofence($spec): stub, own radius check arrives in M7")
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
