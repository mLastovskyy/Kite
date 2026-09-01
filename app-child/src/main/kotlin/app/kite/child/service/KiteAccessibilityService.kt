package app.kite.child.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import app.kite.child.enforce.EnforcementController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Parental-control accessibility service (Play policy permits this use with parent
 * consent; deliberately NOT flagged isAccessibilityTool). M5: every window change feeds
 * the foreground package into [EnforcementController]; M6 will additionally watch for the
 * app-details Settings screen to guard uninstall.
 */
class KiteAccessibilityService :
    AccessibilityService(),
    KoinComponent {
    private val controller: EnforcementController by inject()
    private var scope: CoroutineScope? = null

    override fun onServiceConnected() {
        Log.i(TAG, "accessibility service connected")
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        controller.start(serviceScope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        controller.onForeground(packageName)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        controller.stop()
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "KiteAccessibility"
    }
}
