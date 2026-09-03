package app.kite.child.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import app.kite.child.identity.MemberIdentity
import app.kite.core.apps.AppIconsRemote
import app.kite.core.apps.ChildApp
import app.kite.core.apps.ChildAppsRemote
import java.io.ByteArrayOutputStream

/**
 * Publishes the phone's LAUNCHABLE apps to `child_apps` so the parent can toggle or limit an
 * app before the child ever opens it (Kids360 parity). Only apps with a MAIN/LAUNCHER entry,
 * as package + label + system flag; no icons, no usage. Listed on «Что видит родитель».
 *
 * QUERY_ALL_PACKAGES justification (Play Console): parental control needs the installed-app
 * list to let the parent choose which apps to limit — this class and [UsageSyncer] are the
 * only readers. Uploads are throttled to once per [MIN_INTERVAL_MS] unless [force]d (a new
 * install or removal); the server list is made equal to ours, so uninstalls disappear too.
 */
class InstalledAppsPublisher(
    private val context: Context,
    private val remote: ChildAppsRemote,
    private val icons: AppIconsRemote,
    private val identity: MemberIdentity,
) {
    private val prefs = context.getSharedPreferences("child_apps", Context.MODE_PRIVATE)

    suspend fun publish(force: Boolean = false): Result<Unit> {
        val familyId = identity.familyId() ?: return Result.success(Unit) // not paired yet
        val memberId = identity.memberId() ?: return Result.success(Unit)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong(KEY_LAST, 0L) < MIN_INTERVAL_MS) return Result.success(Unit)

        val apps = launchableApps(memberId, familyId)
        // Same list as last time → nothing to send (labels rarely change; installs force).
        val signature = apps.joinToString("|") { "${it.packageName}=${it.label}" }.hashCode()
        if (!force && signature == prefs.getInt(KEY_SIGNATURE, 0) && prefs.contains(KEY_SIGNATURE)) {
            prefs.edit().putLong(KEY_LAST, now).apply()
            return Result.success(Unit)
        }
        return remote.replaceAll(memberId, familyId, apps).onSuccess {
            prefs.edit().putLong(KEY_LAST, now).putInt(KEY_SIGNATURE, signature).apply()
            uploadMissingIcons(memberId, apps)
        }
    }

    /**
     * 64 px PNG per app, uploaded once (the set of done packages lives in prefs), at most
     * [ICONS_PER_RUN] per run so a first sync stays short. A few KB each; the parent falls
     * back to a letter until the icon lands.
     */
    private suspend fun uploadMissingIcons(memberId: String, apps: List<ChildApp>) {
        val done = prefs.getStringSet(KEY_ICONS_DONE, emptySet()).orEmpty().toMutableSet()
        val pm = context.packageManager
        var sent = 0
        for (app in apps) {
            if (sent >= ICONS_PER_RUN) break
            if (app.packageName in done) continue
            val png =
                runCatching {
                    val bitmap = pm.getApplicationIcon(app.packageName).toBitmap(AppIconsRemote.ICON_PX, AppIconsRemote.ICON_PX)
                    ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                }.getOrNull() ?: continue
            if (icons.upload(memberId, app.packageName, png).isSuccess) {
                done += app.packageName
                sent++
            }
        }
        prefs.edit().putStringSet(KEY_ICONS_DONE, done).apply()
    }

    /** Apps a person can open from the launcher, minus this app itself. */
    fun launchableApps(memberId: String, familyId: String): List<ChildApp> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching { pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
        return resolved
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                ChildApp(
                    memberId = memberId,
                    familyId = familyId,
                    packageName = info.packageName,
                    label = runCatching {
                        pm.getApplicationLabel(info).toString()
                    }.getOrDefault(info.packageName).take(120).ifBlank { info.packageName },
                    isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private companion object {
        const val KEY_LAST = "last_publish"
        const val KEY_SIGNATURE = "signature"
        const val KEY_ICONS_DONE = "icons_done"
        const val ICONS_PER_RUN = 40
        const val MIN_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
