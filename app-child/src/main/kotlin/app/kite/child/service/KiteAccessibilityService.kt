package app.kite.child.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.kite.child.enforce.EnforcementController
import app.kite.child.enforce.GuardOverlay
import app.kite.child.enforce.UninstallGuard
import app.kite.child.request.AskParentActivity
import app.kite.child.request.ChildRequestSender
import app.kite.core.approval.ApprovalRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Parental-control accessibility service (Play policy permits this use with parent
 * consent; deliberately NOT flagged isAccessibilityTool). It drives two things:
 *  - M5 enforcement: every window change feeds the foreground package into the controller;
 *  - M6 uninstall guard: Settings screens are inspected and, if the child is trying to
 *    remove the app or its admin, we bounce home and show the permission screen.
 */
class KiteAccessibilityService :
    AccessibilityService(),
    KoinComponent {
    private val controller: EnforcementController by inject()
    private val guard: UninstallGuard by inject()
    private val guardOverlay: GuardOverlay by inject()
    private val requestSender: ChildRequestSender by inject()
    private var scope: CoroutineScope? = null

    override fun onServiceConnected() {
        Log.i(TAG, "accessibility service connected")
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        controller.start(serviceScope)
        // «Попросить разрешение» on the guard screen: an uninstall request for the parent.
        guardOverlay.onRequestRemoval = {
            if (requestSender.needsChoice()) {
                startActivity(AskParentActivity.intent(this, ApprovalRequest.TYPE_REMOVAL))
            } else {
                serviceScope.launch { requestSender.send(ApprovalRequest.TYPE_REMOVAL, target = null) }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        // M6 guard first: it may need to override whatever is on screen.
        if (handleUninstallGuard(packageName, event.className)) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // Left Settings without a threat — drop any guard screen still up.
            if (guardOverlay.isShown) guardOverlay.hide()
            controller.onForeground(packageName)
        }
    }

    /** Returns true when the event was a removal attempt we handled. */
    private fun handleUninstallGuard(packageName: String, className: CharSequence?): Boolean {
        val text = if (packageName.contains("settings", true) || packageName.startsWith("com.")) collectWindowText() else ""
        if (!guard.isRemovalThreat(packageName, className, text)) return false
        performGlobalAction(GLOBAL_ACTION_HOME)
        guardOverlay.show()
        return true
    }

    /** Flattens the active window's visible text + content descriptions, depth/size capped. */
    private fun collectWindowText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            node ?: return
            if (depth > MAX_DEPTH || sb.length > MAX_CHARS) return
            node.text?.let { sb.append(it).append(' ') }
            node.contentDescription?.let { sb.append(it).append(' ') }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        return sb.toString()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        controller.stop()
        guardOverlay.hide()
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "KiteAccessibility"
        const val MAX_DEPTH = 40
        const val MAX_CHARS = 4000
    }
}
