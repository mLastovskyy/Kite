package app.kite.core.platform

import android.content.Context
import android.util.Log
import com.huawei.hms.api.ConnectionResult
import com.huawei.hms.api.HuaweiApiAvailability

/**
 * hms-flavor factory. Detection order from CLAUDE.md is Google → Huawei → AOSP; the GMS SDK
 * is not shipped in this flavor, so here the chain is Huawei → AOSP, and the gms flavor
 * covers the Google step. Must never crash when services are missing.
 */
object PlatformServicesFactory {
    fun create(context: Context): PlatformServices {
        val hmsAvailable =
            runCatching {
                HuaweiApiAvailability.getInstance()
                    .isHuaweiMobileServicesAvailable(context) == ConnectionResult.SUCCESS
            }.getOrDefault(false)

        return if (hmsAvailable) {
            Log.i(TAG, "Huawei Mobile Services available, using HmsPlatformServices")
            HmsPlatformServices(context)
        } else {
            Log.i(TAG, "Huawei Mobile Services unavailable, using FallbackPlatformServices")
            FallbackPlatformServices(context)
        }
    }

    private const val TAG = "PlatformServices"
}
