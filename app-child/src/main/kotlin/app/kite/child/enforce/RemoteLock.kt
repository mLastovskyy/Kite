package app.kite.child.enforce

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import app.kite.child.admin.KiteDeviceAdminReceiver
import app.kite.child.findphone.FindPhoneRinger
import app.kite.child.identity.MemberIdentity
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.DeviceCommand

/**
 * Instant remote lock (M5). The lock state persists in prefs so a reboot keeps the device
 * locked; the overlay enforces it (dialer stays reachable for emergency calls) and
 * lockNow() additionally turns the screen off when Device Admin is active. Commands are
 * acknowledged best-effort — an offline ack just retries on the next poll.
 */
class RemoteLock(
    private val context: Context,
    private val commandsRemote: CommandsRemote,
    private val identity: MemberIdentity,
    private val ringer: FindPhoneRinger,
) {
    private val prefs = context.getSharedPreferences("remote_lock", Context.MODE_PRIVATE)

    val locked: Boolean get() = prefs.getBoolean(KEY_LOCKED, false)

    suspend fun apply(command: DeviceCommand) {
        when (command.command) {
            DeviceCommand.LOCK -> {
                prefs.edit().putBoolean(KEY_LOCKED, true).apply()
                lockScreenNow()
            }
            DeviceCommand.UNLOCK -> prefs.edit().putBoolean(KEY_LOCKED, false).apply()
            DeviceCommand.RING -> ringer.start()
            DeviceCommand.STOP_RING -> ringer.stop()
            else -> return // unknown command from a newer app version — ignore, don't ack
        }
        commandsRemote.markExecuted(command.id)
    }

    /** Polling fallback for the Realtime socket; also drains the backlog on start. */
    suspend fun pollPending() {
        val memberId = identity.memberId() ?: return
        commandsRemote.pending(memberId).getOrNull()?.forEach { runCatching { apply(it) } }
    }

    private fun lockScreenNow() {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
        val admin = ComponentName(context, KiteDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) runCatching { dpm.lockNow() }
    }

    private companion object {
        const val KEY_LOCKED = "locked"
    }
}
