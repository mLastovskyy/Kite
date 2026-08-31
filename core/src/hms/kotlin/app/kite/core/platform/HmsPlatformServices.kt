package app.kite.core.platform

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * HMS-backed implementation. Only this source set may touch `com.huawei.*`.
 *
 * M1: logging stub. Real integrations arrive later: HMS Push Kit in M3/M5,
 * HMS Location Kit + Geofence in M7, Safety Detect URLCheck in M8.
 */
class HmsPlatformServices(private val context: Context) : PlatformServices {
    override val variant: PlatformVariant = PlatformVariant.HMS

    override suspend fun pushToken(): String? {
        Log.d(TAG, "pushToken: stub, HMS Push Kit arrives in M3")
        return null
    }

    override fun locationUpdates(spec: LocationRequestSpec): Flow<GeoPoint> {
        Log.d(TAG, "locationUpdates($spec): stub, HMS Location Kit arrives in M7")
        return emptyFlow()
    }

    override suspend fun addGeofence(spec: GeofenceSpec): Result<Unit> {
        Log.d(TAG, "addGeofence($spec): stub, HMS Geofence arrives in M7")
        return Result.success(Unit)
    }

    override suspend fun removeGeofence(id: String): Result<Unit> {
        Log.d(TAG, "removeGeofence($id): stub")
        return Result.success(Unit)
    }

    override suspend fun checkUrl(url: String): UrlVerdict {
        Log.d(TAG, "checkUrl: stub, Safety Detect URLCheck arrives in M8 (package=${context.packageName})")
        return UrlVerdict.UNKNOWN
    }

    private companion object {
        const val TAG = "HmsPlatform"
    }
}
