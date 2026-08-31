package app.kite.core.platform

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * GMS-backed implementation. Only this source set may touch `com.google.android.gms.*`.
 *
 * M1: logging stub. Real integrations arrive later: FCM in M3/M5,
 * FusedLocationProvider + GeofencingClient in M7. URL reputation stays on the local
 * blocklist even here — Google Safe Browsing is licensed non-commercial only.
 */
class GmsPlatformServices(private val context: Context) : PlatformServices {
    override val variant: PlatformVariant = PlatformVariant.GMS

    override suspend fun pushToken(): String? {
        Log.d(TAG, "pushToken: stub, FCM arrives in M3")
        return null
    }

    override fun locationUpdates(spec: LocationRequestSpec): Flow<GeoPoint> {
        Log.d(TAG, "locationUpdates($spec): stub, FusedLocationProvider arrives in M7")
        return emptyFlow()
    }

    override suspend fun addGeofence(spec: GeofenceSpec): Result<Unit> {
        Log.d(TAG, "addGeofence($spec): stub, GeofencingClient arrives in M7")
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
        const val TAG = "GmsPlatform"
    }
}
