package app.kite.child.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.kite.child.usage.UsageCollectScheduler

class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in WATCHED_ACTIONS) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
        UsageCollectScheduler.runNow(context)
    }

    private companion object {
        val WATCHED_ACTIONS =
            setOf(
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_FULLY_REMOVED,
            )
    }
}
