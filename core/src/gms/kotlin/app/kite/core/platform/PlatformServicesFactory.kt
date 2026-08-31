package app.kite.core.platform

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * gms-flavor factory. Detection order from CLAUDE.md is Google → Huawei → AOSP; the HMS SDK
 * is not shipped in this flavor, so here the chain is Google → AOSP, and the hms flavor
 * covers the Huawei step. Must never crash when services are missing — a sideloaded gms
 * build on a GMS-less Huawei device silently degrades to the AOSP fallback.
 */
object PlatformServicesFactory {
    fun create(context: Context): PlatformServices {
        val gmsAvailable =
            runCatching {
                GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
            }.getOrDefault(false)

        return if (gmsAvailable) {
            Log.i(TAG, "Google Play services available, using GmsPlatformServices")
            GmsPlatformServices(context)
        } else {
            Log.i(TAG, "Google Play services unavailable, using FallbackPlatformServices")
            FallbackPlatformServices(context)
        }
    }

    private const val TAG = "PlatformServices"
}
