package app.kite.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.kite.core.killswitch.KillSwitchRepository
import app.kite.core.killswitch.UpdateStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Self-update for builds that do not come from Play (CLAUDE.md: hms and direct APK use our
 * own updater; Play builds must not ship `REQUEST_INSTALL_PACKAGES`).
 *
 * Two paths, chosen at runtime by what the OS lets this build do:
 *  - [canInstallDirectly] — the `hms` manifest declares `REQUEST_INSTALL_PACKAGES` and the
 *    user allowed "install unknown apps": the APK is downloaded to the cache and handed to
 *    the package installer through a FileProvider.
 *  - otherwise the release URL opens in the browser, which downloads and installs it — the
 *    only Play-compliant option for the `gms` build.
 *
 * The FileProvider authority is `<applicationId>.updates` (declared in each app's manifest).
 */
class ApkInstaller(private val context: Context, private val httpClient: HttpClient) {
    /** What [update] ended up doing, so the UI can word its confirmation. */
    enum class Outcome { INSTALLER_OPENED, BROWSER_OPENED, NEEDS_INSTALL_PERMISSION }

    /**
     * One call for «Скачать и установить»: direct download + installer where allowed, the
     * system "allow installs" toggle when the build could but is not yet permitted, the
     * browser everywhere else (Play builds, or no APK URL in the manifest).
     */
    suspend fun update(status: UpdateStatus, onProgress: (Float) -> Unit = {}): Result<Outcome> {
        val url = status.apkUrl
        return when {
            url != null && canInstallDirectly() -> downloadAndInstall(url, onProgress).map { Outcome.INSTALLER_OPENED }
            url != null && declaresInstallPermission() -> {
                openInstallPermissionSettings()
                Result.success(Outcome.NEEDS_INSTALL_PERMISSION)
            }
            else -> {
                openInBrowser(url ?: KillSwitchRepository.RELEASES_PAGE_URL)
                Result.success(Outcome.BROWSER_OPENED)
            }
        }
    }

    /** True when this build may launch the package installer itself (API 26+ setting). */
    fun canInstallDirectly(): Boolean = runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    /**
     * True when the manifest declares `REQUEST_INSTALL_PACKAGES` (hms / direct builds) — the
     * user can then be sent to the system toggle with [openInstallPermissionSettings].
     */
    fun declaresInstallPermission(): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        info.requestedPermissions?.contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES) == true
    }.getOrDefault(false)

    fun openInstallPermissionSettings() {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Opens [url] in the browser; the browser downloads the APK and offers to install it. */
    fun openInBrowser(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Downloads [url] into the app cache (reporting 0..1 progress) and starts the system
     * installer. Requires [canInstallDirectly]; the caller decides the fallback.
     */
    suspend fun downloadAndInstall(url: String, onProgress: (Float) -> Unit = {}): Result<Unit> = runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "kite-update.apk")
        withContext(Dispatchers.IO) {
            httpClient.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) error("Сервер вернул ${response.status.value}")
                val total = response.contentLength() ?: -1L
                target.outputStream().use { out ->
                    // InputStream bridge: stable across Ktor versions, lets us report progress.
                    response.bodyAsChannel().toInputStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                        }
                        onProgress(1f)
                    }
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", target)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
