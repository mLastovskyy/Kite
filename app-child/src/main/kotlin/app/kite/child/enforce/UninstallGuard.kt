package app.kite.child.enforce

import android.app.admin.DevicePolicyManager
import android.content.Context
import app.kite.child.admin.KiteDeviceAdminReceiver

/**
 * The pure decision behind tier-1 uninstall protection, split out so it can be unit-tested:
 * the Android bits (prefs, DevicePolicyManager) live in [UninstallGuard].
 *
 * Two rules matter more than the keyword lists:
 *
 * 1. **With no active device admin the guard is inert.** Android allows the uninstall anyway
 *    in that state, so bouncing the child protects nothing — and it breaks our own setup,
 *    which is exactly the bug the owner hit: `ACTION_ADD_DEVICE_ADMIN` opens
 *    `DeviceAdminAdd`, the same Activity used to DEACTIVATE an admin, and the guard threw the
 *    user out of the screen that was about to enable protection in the first place.
 * 2. **A Settings screen we opened ourselves is not a threat.** The wizard and «Здоровье
 *    защиты» deep-link into Settings by design (background location and vendor autostart
 *    land on the app-details page, which otherwise looks exactly like an uninstall attempt).
 */
object RemovalThreat {
    fun isThreat(
        packageName: String?,
        className: String,
        windowText: String,
        ownPackage: String,
        adminActive: Boolean,
        protectionLifted: Boolean,
        selfLaunchedSettings: Boolean,
    ): Boolean {
        if (!adminActive || protectionLifted || selfLaunchedSettings) return false
        val pkg = packageName ?: return false
        if (pkg !in SETTINGS_PACKAGES && !pkg.contains("settings", ignoreCase = true)) return false

        // With the admin active, the admin screens can only mean deactivation.
        if (ADMIN_SCREEN_HINTS.any { className.contains(it, ignoreCase = true) }) return true
        if (APP_SCREEN_HINTS.any { className.contains(it, ignoreCase = true) }) return true

        // OEM Settings use their own class names, so fall back to what is on screen: our app
        // named together with a removal-ish action. Both halves are required so unrelated
        // Settings pages do not trip the guard.
        val lower = windowText.lowercase()
        val identityHit = lower.contains(APP_LABEL) || lower.contains(ownPackage)
        return identityHit && ACTION_KEYWORDS.any { lower.contains(it) }
    }

    private const val APP_LABEL = "kite jr"

    private val SETTINGS_PACKAGES =
        setOf(
            "com.android.settings",
            "com.huawei.systemmanager", // EMUI app-management / admin lives here too
            "com.samsung.android.settings",
        )

    /** Screens that activate OR deactivate a device admin — the same Activity does both. */
    private val ADMIN_SCREEN_HINTS = listOf("DeviceAdminAdd", "DeviceAdminSettings")

    /** App-details and uninstaller screens: nothing legitimate happens there once set up. */
    private val APP_SCREEN_HINTS = listOf("InstalledAppDetails", "AppInfoDashboard", "UninstallerActivity")

    private val ACTION_KEYWORDS =
        listOf(
            "удал", "uninstall", // uninstall
            "деактив", "выключить", "отключить", "deactivate", "disable", // admin off
            "остановить", "force stop", // force stop
            "очистить", "clear data", "стереть", // clear data
            "администратор", "device admin",
        )
}

/**
 * Marks the short window after the app itself sends the user into Settings. Kept in plain
 * prefs rather than memory because the accessibility service and the UI live in different
 * processes-worth of lifecycle: the wizard stamps it, the service reads it.
 *
 * This is the owner's «желательно чтобы это само приложение делало по согласию пользователя»
 * in practice: the app opens the exact Settings screen, and its own guard stands aside while
 * the user is there.
 */
object SelfLaunchedSettings {
    fun stamp(context: Context, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_UNTIL, now + GRACE_MS).apply()
    }

    fun active(context: Context, now: Long = System.currentTimeMillis()): Boolean = now < prefs(context).getLong(KEY_UNTIL, 0L)

    /** Setup finished / the user left on their own: close the window early. */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_UNTIL).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("uninstall_guard", Context.MODE_PRIVATE)

    private const val KEY_UNTIL = "self_launch_until"

    /** Long enough for the EMUI battery screen with its three toggles, short enough to matter. */
    private const val GRACE_MS = 3 * 60_000L
}

/**
 * Tier-1 uninstall protection (M6). The AccessibilityService feeds it every Settings window;
 * when the child opens this app's details page (uninstall / force-stop / clear data) or the
 * device-admin deactivation screen, the guard tells the service to bounce home and show the
 * "parent permission required" screen.
 *
 * Honest limitation (CLAUDE.md): Safe Mode or a second user profile defeats this; tier 2
 * (Device Owner, M9) closes those. Class names differ across OEMs, so detection also scans
 * the visible node text — NEEDS_HUAWEI_TEST on EMUI.
 */
class UninstallGuard(private val context: Context) {
    private val prefs = context.getSharedPreferences("uninstall_guard", Context.MODE_PRIVATE)

    /** Removal was authorised (correct offline code): protection is lifted until this time. */
    fun protectionLifted(now: Long = System.currentTimeMillis()): Boolean = now < prefs.getLong(KEY_LIFTED_UNTIL, 0L)

    fun liftProtection(minutes: Long = LIFT_MINUTES) {
        prefs.edit().putLong(KEY_LIFTED_UNTIL, System.currentTimeMillis() + minutes * 60_000L).apply()
        // Drop the admin so Android actually allows the uninstall during the window.
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val admin = KiteDeviceAdminReceiver.componentName(context)
        if (dpm?.isAdminActive(admin) == true) runCatching { dpm.removeActiveAdmin(admin) }
    }

    /** True when the device admin is currently active — i.e. there is protection to defend. */
    fun adminActive(): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
        return runCatching { dpm.isAdminActive(KiteDeviceAdminReceiver.componentName(context)) }.getOrDefault(false)
    }

    /**
     * True when [packageName]/[className] plus the on-screen [text] look like an attempt to
     * remove the app or its admin. See [RemovalThreat] for the rules.
     */
    fun isRemovalThreat(packageName: String?, className: CharSequence?, text: String): Boolean = RemovalThreat.isThreat(
        packageName = packageName,
        className = className?.toString().orEmpty(),
        windowText = text,
        ownPackage = context.packageName,
        adminActive = adminActive(),
        protectionLifted = protectionLifted(),
        selfLaunchedSettings = SelfLaunchedSettings.active(context),
    )

    private companion object {
        const val KEY_LIFTED_UNTIL = "lifted_until"
        const val LIFT_MINUTES = 10L
    }
}
