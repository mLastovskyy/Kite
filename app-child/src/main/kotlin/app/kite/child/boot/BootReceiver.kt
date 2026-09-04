package app.kite.child.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.kite.child.location.LocationService
import app.kite.child.usage.UsageCollectScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESTART_ACTIONS) return
        UsageCollectScheduler.schedule(context)
        UsageCollectScheduler.runNow(context)
        runCatching { LocationService.start(context) }
    }

    private companion object {
        val RESTART_ACTIONS =
            setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_LOCKED_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                "android.intent.action.QUICKBOOT_POWERON",
            )
    }
}
