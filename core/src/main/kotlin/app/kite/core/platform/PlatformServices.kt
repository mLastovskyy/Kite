package app.kite.core.platform

import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over vendor mobile services (GMS / HMS / plain AOSP).
 *
 * Hard rule from CLAUDE.md: nothing outside `src/gms` may reference
 * `com.google.android.gms.*` and nothing outside `src/hms` may reference `com.huawei.*`.
 * Feature code depends on this interface only; the concrete implementation is chosen once
 * at startup by `PlatformServicesFactory`, which exists per flavor.
 */
interface PlatformServices {
    /** Which backing implementation was selected at runtime. */
    val variant: PlatformVariant

    /** Push token for wake-up delivery (FCM / HMS Push Kit). Null when unavailable. */
    suspend fun pushToken(): String?

    /**
     * Continuous location updates. Implementations must not throw when permissions are
     * missing — they emit nothing instead; permission UX is handled by the caller.
     */
    fun locationUpdates(spec: LocationRequestSpec): Flow<GeoPoint>

    /** Registers a geofence with the vendor service or our own radius check on AOSP. */
    suspend fun addGeofence(spec: GeofenceSpec): Result<Unit>

    suspend fun removeGeofence(id: String): Result<Unit>

    /**
     * URL reputation lookup. GMS and AOSP use the local blocklist (Google Safe Browsing is
     * licensed non-commercial only — never use it); HMS may use Safety Detect URLCheck.
     */
    suspend fun checkUrl(url: String): UrlVerdict
}

enum class PlatformVariant { GMS, HMS, AOSP }

data class LocationRequestSpec(val intervalMillis: Long, val minUpdateDistanceMeters: Float = 0f, val highAccuracy: Boolean = false)

data class GeoPoint(val latitude: Double, val longitude: Double, val accuracyMeters: Float?, val timestampMillis: Long)

data class GeofenceSpec(val id: String, val latitude: Double, val longitude: Double, val radiusMeters: Float)

enum class UrlVerdict { SAFE, MALICIOUS, PHISHING, UNKNOWN }
