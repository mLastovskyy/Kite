package app.kite.core.platform

import android.content.Context
import android.util.Log
import app.kite.core.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * GMS-backed implementation. Only this source set may touch `com.google.android.gms.*` /
 * `com.google.firebase.*`. Firebase is initialised MANUALLY (no google-services Gradle
 * plugin) so the hms flavor never needs a google-services.json — the per-app options are
 * the non-secret ids from the Firebase console.
 *
 * Location still falls back to LocationManager (FallbackPlatformServices) for now;
 * FusedLocationProvider can be added here later for better accuracy.
 */
class GmsPlatformServices(private val context: Context) : PlatformServices {
    override val variant: PlatformVariant = PlatformVariant.GMS

    @Suppress("DEPRECATION")
    override suspend fun pushToken(): String? = runCatching {
        ensureFirebase()
        suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token -> cont.resume(token) }
                .addOnFailureListener { e ->
                    Log.w(TAG, "FCM token failed", e)
                    cont.resume(null)
                }
        }
    }.getOrNull()

    private fun ensureFirebase() {
        if (FirebaseApp.getApps(context).isNotEmpty()) return
        if (BuildConfig.FCM_API_KEY.isEmpty() || BuildConfig.FCM_APP_ID_CHILD.isEmpty()) return // not configured (e.g. CI)
        // Config comes from BuildConfig (local.properties / CI), never source. The app id
        // differs per package; sender id, api key and project id are shared.
        val appId =
            if (context.packageName == "app.kite.parent") BuildConfig.FCM_APP_ID_PARENT else BuildConfig.FCM_APP_ID_CHILD
        val options =
            FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FCM_PROJECT_ID)
                .setApplicationId(appId)
                .setApiKey(BuildConfig.FCM_API_KEY)
                .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
                .build()
        FirebaseApp.initializeApp(context, options)
    }

    override fun locationUpdates(spec: LocationRequestSpec): Flow<GeoPoint> {
        // LocationManager fallback covers this until FusedLocationProvider is wired here.
        Log.d(TAG, "locationUpdates($spec): using AOSP LocationManager fallback")
        return emptyFlow()
    }

    override suspend fun addGeofence(spec: GeofenceSpec): Result<Unit> {
        Log.d(TAG, "addGeofence($spec): stub, GeofencingClient arrives later")
        return Result.success(Unit)
    }

    override suspend fun removeGeofence(id: String): Result<Unit> {
        Log.d(TAG, "removeGeofence($id): stub")
        return Result.success(Unit)
    }

    override suspend fun checkUrl(url: String): UrlVerdict {
        Log.d(TAG, "checkUrl: local blocklist path (package=${context.packageName})")
        return UrlVerdict.UNKNOWN
    }

    private companion object {
        const val TAG = "GmsPlatform"
    }
}
