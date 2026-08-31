package app.kite.core.killswitch

import kotlinx.serialization.Serializable

/**
 * Contract of `update.json` published as a GitHub Release asset (CLAUDE.md, "Update
 * mechanism and kill switch"). Every field has a safe default so a partially filled or
 * future-versioned file can never crash a client.
 *
 * [disableEnforcement] is the kill switch: when true, the child app stops blocking apps
 * and lifts all locks while keeping reporting alive. Editing one JSON must be enough to
 * disarm every client — this is a safety requirement, not a nice-to-have.
 */
@Serializable
data class UpdateManifest(
    val latestVersionCode: Int = 0,
    val minSupportedVersionCode: Int = 0,
    val disableEnforcement: Boolean = false,
    val message: String? = null,
)
