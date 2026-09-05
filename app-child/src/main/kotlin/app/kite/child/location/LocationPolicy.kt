package app.kite.child.location

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.util.Log
import app.kite.child.admin.KiteDeviceAdminReceiver

/**
 * Keeps location switched on where Android actually allows it: as Device Owner (the optional
 * tier-2 setup) the switch can be turned on and then locked, so «геолокация выключена» stops
 * being a way to disappear from the map. A plain Device Admin install cannot do this — Android
 * gives no such API — so there the child's own switch still wins and the parent is told about
 * it instead ([app.kite.child.identity.DeviceReporter] reports LOCATION_SERVICES_OFF).
 */
class LocationPolicy(private val context: Context) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
    private val admin = ComponentName(context, KiteDeviceAdminReceiver::class.java)

    val isDeviceOwner: Boolean get() = dpm?.isDeviceOwnerApp(context.packageName) == true

    /** True when location is now on and locked; false when this device cannot enforce it. */
    fun enforce(): Boolean {
        val manager = dpm ?: return false
        if (!isDeviceOwner || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return runCatching {
            manager.setLocationEnabled(admin, true)
            manager.addUserRestriction(admin, UserManager.DISALLOW_CONFIG_LOCATION)
            true
        }.onFailure { Log.w(TAG, "could not lock location on: ${it.message}") }.getOrDefault(false)
    }

    /** Releases the lock together with the rest of the protection. */
    fun release() {
        val manager = dpm ?: return
        if (!isDeviceOwner) return
        runCatching { manager.clearUserRestriction(admin, UserManager.DISALLOW_CONFIG_LOCATION) }
    }

    private companion object {
        const val TAG = "LocationPolicy"
    }
}
