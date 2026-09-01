package app.kite.child.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Tier-1 uninstall protection (M6): while this admin is active, Android refuses a plain
 * uninstall. Be honest: a determined teenager bypasses this via Safe Mode or a second
 * user profile — that is expected; tier 2 (Device Owner, M9) closes those holes.
 */
class KiteDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "device admin enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        // M6: notify the parent that protection dropped.
        Log.w(TAG, "device admin disabled")
    }

    companion object {
        private const val TAG = "KiteDeviceAdmin"

        fun componentName(context: Context): ComponentName = ComponentName(context, KiteDeviceAdminReceiver::class.java)
    }
}
