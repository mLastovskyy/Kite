package app.kite.core.platform

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow

class HmsPlatformServices(private val context: Context) : PlatformServices {
    private val fallback = FallbackPlatformServices(context)

    override val variant: PlatformVariant = PlatformVariant.HMS

    override suspend fun pushToken(): String? {
        Log.d(TAG, "pushToken: HMS Push Kit is not integrated; the app polls instead")
        return null
    }

    override fun locationUpdates(spec: LocationRequestSpec): Flow<GeoPoint> = fallback.locationUpdates(spec)

    override suspend fun addGeofence(spec: GeofenceSpec): Result<Unit> = fallback.addGeofence(spec)

    override suspend fun removeGeofence(id: String): Result<Unit> = fallback.removeGeofence(id)

    override suspend fun checkUrl(url: String): UrlVerdict = fallback.checkUrl(url)

    private companion object {
        const val TAG = "HmsPlatform"
    }
}
