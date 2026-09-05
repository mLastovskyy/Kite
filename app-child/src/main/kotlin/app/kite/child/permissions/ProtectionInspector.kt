package app.kite.child.permissions

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.kite.child.admin.KiteDeviceAdminReceiver
import app.kite.child.service.KiteAccessibilityService

/**
 * Status checks and Settings deep links for every [ProtectionRequirement].
 * Never guesses: each check reads the real system state, so the wizard can re-check in
 * onResume and advance automatically — no "I did it" buttons. The one exception is
 * [ProtectionRequirement.VENDOR_AUTOSTART]: no API exposes that state, so it is the only
 * manually-confirmed step.
 */
class ProtectionInspector(private val context: Context) {
    /** Requirements applicable to THIS device, in wizard order. */
    val requirements: List<ProtectionRequirement> =
        ProtectionRequirement.entries.filter { requirement ->
            when (requirement) {
                ProtectionRequirement.VENDOR_AUTOSTART -> vendorAutostartIntent() != null
                else -> true
            }
        }

    fun isSatisfied(requirement: ProtectionRequirement, vendorAutostartConfirmed: Boolean): Boolean = when (requirement) {
        ProtectionRequirement.NOTIFICATIONS ->
            NotificationManagerCompat.from(context).areNotificationsEnabled()

        ProtectionRequirement.USAGE_ACCESS -> hasUsageAccess()

        ProtectionRequirement.OVERLAY -> Settings.canDrawOverlays(context)

        ProtectionRequirement.LOCATION_FOREGROUND ->
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        ProtectionRequirement.LOCATION_BACKGROUND ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                // Below Android 10 the foreground grant covers background too.
                isSatisfied(ProtectionRequirement.LOCATION_FOREGROUND, vendorAutostartConfirmed)
            }

        ProtectionRequirement.ACCESSIBILITY -> isAccessibilityServiceEnabled()

        ProtectionRequirement.BATTERY ->
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true

        // No API exposes vendor autostart state — the only manually-confirmed step.
        ProtectionRequirement.VENDOR_AUTOSTART -> vendorAutostartConfirmed

        ProtectionRequirement.DEVICE_ADMIN ->
            context.getSystemService(DevicePolicyManager::class.java)
                ?.isAdminActive(KiteDeviceAdminReceiver.componentName(context)) == true
    }

    /**
     * Deep link into the exact Settings screen (never print instructions instead).
     * Null → the requirement is granted through a runtime dialog, not a Settings screen.
     */
    fun settingsIntent(requirement: ProtectionRequirement): Intent? = when (requirement) {
        ProtectionRequirement.NOTIFICATIONS -> null
        ProtectionRequirement.LOCATION_FOREGROUND -> null

        ProtectionRequirement.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

        ProtectionRequirement.OVERLAY ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri())

        // On Android 11+ background location is only grantable from the app's
        // Settings page; the option name comes from getBackgroundPermissionOptionLabel.
        ProtectionRequirement.LOCATION_BACKGROUND ->
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())

        ProtectionRequirement.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        ProtectionRequirement.BATTERY ->
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri())

        ProtectionRequirement.VENDOR_AUTOSTART -> vendorAutostartIntent()

        ProtectionRequirement.DEVICE_ADMIN ->
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    KiteDeviceAdminReceiver.componentName(context),
                )
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Защищает приложение от удаления без разрешения родителя.",
                )
            }
    }

    /** Device-specific name of the «always allow» option, for the background-location step. */
    fun backgroundPermissionOptionLabel(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.packageManager.backgroundPermissionOptionLabel.toString()
    } else {
        null
    }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(context, KiteAccessibilityService::class.java)
        val enabled =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
        return enabled.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == expected
        }
    }

    /**
     * Vendor autostart screen. Component names are community-documented, not a public
     * API — every candidate is resolved before use and the caller falls back to the app
     * details page. // NEEDS_HUAWEI_TEST (and Xiaomi/Oppo/Vivo/Samsung on real devices)
     */
    fun vendorAutostartIntent(): Intent? {
        val candidates =
            when (Build.MANUFACTURER.lowercase()) {
                "huawei", "honor" ->
                    listOf(
                        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
                    )

                "xiaomi", "redmi", "poco" ->
                    listOf(
                        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    )

                "oppo", "realme" ->
                    listOf(
                        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    )

                "vivo" ->
                    listOf(
                        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                    )

                "samsung" ->
                    listOf(
                        "com.samsung.android.lool" to "com.samsung.android.sm.ui.battery.BatteryActivity",
                    )

                else -> emptyList()
            }
        return candidates
            .map { (pkg, cls) -> Intent().setClassName(pkg, cls) }
            .firstOrNull { intent -> intent.resolveActivity(context.packageManager) != null }
    }

    private fun packageUri(): Uri = Uri.fromParts("package", context.packageName, null)
}
