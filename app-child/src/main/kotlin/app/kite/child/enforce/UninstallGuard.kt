package app.kite.child.enforce

import android.app.admin.DevicePolicyManager
import android.content.Context
import app.kite.child.admin.KiteDeviceAdminReceiver

/**
 * Tier-1 uninstall protection (M6). The AccessibilityService feeds it every Settings
 * window; when the child opens this app's details page (uninstall / force-stop / clear
 * data) or the device-admin deactivation screen, the guard tells the service to bounce
 * home and show the "parent permission required" screen.
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

    /**
     * True when [packageName]/[className] plus the on-screen [text] look like an attempt to
     * remove the app or its admin, and protection is NOT currently lifted.
     */
    fun isRemovalThreat(packageName: String?, className: CharSequence?, text: String): Boolean {
        if (protectionLifted()) return false
        val pkg = packageName ?: return false
        if (pkg !in SETTINGS_PACKAGES && !pkg.contains("settings", ignoreCase = true)) return false

        val cls = className?.toString().orEmpty()
        val classHit = ADMIN_CLASS_HINTS.any { cls.contains(it, ignoreCase = true) }
        val lower = text.lowercase()
        // The app-details screen for OUR app shows its label together with a remove action;
        // the device-admin screen shows a deactivate action. Require both an identity hit
        // and an action keyword so unrelated Settings pages don't trip the guard.
        val identityHit = lower.contains(APP_LABEL) || lower.contains(context.packageName)
        val actionHit = ACTION_KEYWORDS.any { lower.contains(it) }
        return classHit || (identityHit && actionHit)
    }

    private companion object {
        const val KEY_LIFTED_UNTIL = "lifted_until"
        const val LIFT_MINUTES = 10L
        const val APP_LABEL = "kite jr"

        val SETTINGS_PACKAGES =
            setOf(
                "com.android.settings",
                "com.huawei.systemmanager", // EMUI app-management / admin lives here too
                "com.samsung.android.settings",
            )

        val ADMIN_CLASS_HINTS =
            listOf(
                "InstalledAppDetails",
                "AppInfoDashboard",
                "DeviceAdminAdd",
                "DeviceAdminSettings",
                "UninstallerActivity",
            )

        val ACTION_KEYWORDS =
            listOf(
                "удал", "uninstall", // uninstall
                "деактив", "выключить", "отключить", "deactivate", "disable", // admin off
                "остановить", "force stop", // force stop
                "очистить", "clear data", "стереть", // clear data
                "администратор", "device admin",
            )
    }
}
