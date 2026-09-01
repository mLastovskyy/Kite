package app.kite.child.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Parental-control accessibility service. M2 ships it as a stub so the onboarding wizard
 * has a real Settings toggle to enable; M5 adds foreground-app detection
 * (TYPE_WINDOW_STATE_CHANGED → current package) and M6 watches for the child opening the
 * app-details Settings screen to guard uninstall.
 */
class KiteAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        Log.i(TAG, "accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // M5: track window state changes here. Intentionally empty in M2.
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val TAG = "KiteAccessibility"
    }
}
