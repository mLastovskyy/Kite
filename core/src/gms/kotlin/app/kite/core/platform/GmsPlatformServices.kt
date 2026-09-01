package app.kite.core.platform

import android.content.Context
import android.util.Log
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
        // Sender id (project number) and API key are shared; the app id differs per package.
        val appId =
            when (context.packageName) {
                "app.kite.parent" -> "1:861362515851:android:a6b94a1a4e0a66cd70498e"
                else -> "1:861362515851:android:09deafb5b6fe9b5e70498e" // app.kite.child
            }
        val options =
            FirebaseOptions.Builder()
                .setProjectId("kite-669b4")
                .setApplicationId(appId)
                .setApiKey("AIzaSyBXaZ0NfDm_MCUorwB5tAvkTapc7BkAcaE")
                .setGcmSenderId("861362515851")
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
