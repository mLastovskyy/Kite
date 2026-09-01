package app.kite.child.permissions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Holds the live grant status of every applicable requirement and derives wizard position.
 * Re-checking is explicit ([refresh]) so the UI can call it from onResume and advance
 * automatically — no "I did it" button, ever (CLAUDE.md).
 */
class WizardController(private val inspector: ProtectionInspector) {
    val requirements: List<ProtectionRequirement> = inspector.requirements

    var vendorAutostartConfirmed: Boolean = false
        private set

    var statuses by mutableStateOf<Map<ProtectionRequirement, Boolean>>(emptyMap())
        private set

    fun setVendorAutostartConfirmed(confirmed: Boolean) {
        vendorAutostartConfirmed = confirmed
        refresh()
    }

    fun refresh() {
        statuses = requirements.associateWith { inspector.isSatisfied(it, vendorAutostartConfirmed) }
    }

    fun isSatisfied(requirement: ProtectionRequirement): Boolean = statuses[requirement] == true

    /** First requirement not yet satisfied, or null when everything is granted. */
    val firstUnsatisfied: ProtectionRequirement?
        get() = requirements.firstOrNull { statuses[it] != true }

    val grantedCount: Int get() = requirements.count { statuses[it] == true }

    val total: Int get() = requirements.size

    /** After the accessibility step the app is functional (CLAUDE.md): «настроить позже» is offered. */
    fun functionalReached(): Boolean {
        val accessibilityIndex = requirements.indexOf(ProtectionRequirement.ACCESSIBILITY)
        if (accessibilityIndex < 0) return grantedCount == total
        return (0..accessibilityIndex).all { statuses[requirements[it]] == true }
    }
}
