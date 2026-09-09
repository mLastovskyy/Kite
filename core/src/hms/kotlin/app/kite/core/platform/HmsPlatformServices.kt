package app.kite.core.platform

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.common.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class HmsPlatformServices(private val context: Context) : PlatformServices {
    private val fallback = FallbackPlatformServices(context)

    @Volatile private var pushError: String? = null

    override val lastPushError: String? get() = pushError

    override val variant: PlatformVariant = PlatformVariant.HMS

    /**
     * The Push Kit token, or null when this phone cannot give one — no HMS Core, no app id in
     * the manifest, or Huawei refusing (an unregistered signing fingerprint is the usual
     * reason). Null is not an error here: the app keeps its WebSocket and polling, push is the
     * wake-up on top.
     *
     * [HmsInstanceId.getToken] blocks and throws, so it runs off the main thread.
     */
    override suspend fun pushToken(): String? = withContext(Dispatchers.IO) {
        val appId = hmsAppId() ?: run {
            pushError = "В сборке нет идентификатора приложения Huawei — соберите с agconnect-services.json."
            Log.d(TAG, "pushToken: no com.huawei.hms.client.appid in the manifest")
            return@withContext null
        }
        val token =
            runCatching { HmsInstanceId.getInstance(context).getToken(appId, HCM_SCOPE) }
                .onFailure { Log.w(TAG, "pushToken: Push Kit refused (${it.message})") }
                .getOrElse { failure ->
                    pushError = reasonFor(failure)
                    return@withContext null
                }
        pushError = if (token.isNullOrBlank()) EMPTY_TOKEN_HINT else null
        token?.takeIf { it.isNotBlank() }
    }

    /**
     * Huawei answers with a status code and little else, and the one that actually happens means
     * something a person can fix: the app's signing certificate was never registered in AGC, or
     * Push Kit is not switched on for it.
     */
    private fun reasonFor(failure: Throwable): String {
        val code = (failure as? ApiException)?.statusCode
        val detail = listOfNotNull(code?.toString(), failure.message?.take(120)).joinToString(" · ")
        return "Huawei не выдал токен ($detail). Обычно это незарегистрированный SHA-256 ключа " +
            "в AppGallery Connect или выключенный Push Kit."
    }

    /** «appid=118934333» in the manifest — Huawei's own format, and it keeps the value a string. */
    private fun hmsAppId(): String? = runCatching {
        context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData
            ?.getString(APP_ID_META)
            ?.substringAfter("appid=")
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()

    override fun locationUpdates(spec: LocationRequestSpec): Flow<GeoPoint> = fallback.locationUpdates(spec)

    override suspend fun addGeofence(spec: GeofenceSpec): Result<Unit> = fallback.addGeofence(spec)

    override suspend fun removeGeofence(id: String): Result<Unit> = fallback.removeGeofence(id)

    override suspend fun checkUrl(url: String): UrlVerdict = fallback.checkUrl(url)

    private companion object {
        const val TAG = "HmsPlatform"
        const val APP_ID_META = "com.huawei.hms.client.appid"

        /** The only scope Push Kit defines for messaging. */
        const val HCM_SCOPE = "HCM"
        const val EMPTY_TOKEN_HINT = "Huawei вернул пустой токен — обычно HMS Core на телефоне устарел."
    }
}
