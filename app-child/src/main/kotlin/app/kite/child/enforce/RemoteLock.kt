package app.kite.child.enforce

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import app.kite.child.admin.KiteDeviceAdminReceiver
import app.kite.child.findphone.FindPhoneRinger
import app.kite.child.identity.MemberIdentity
import app.kite.core.commands.CommandsRemote
import app.kite.core.commands.DeviceCommand
import java.time.LocalDate
import java.time.ZoneId

/**
 * Applies remote commands (M5+): lock/unlock, find-phone ring, and grant_time (bonus
 * screen-time minutes for today). Lock state persists across reboot; the overlay enforces
 * it (dialer stays reachable for emergency calls). Commands are acknowledged best-effort —
 * an offline ack just retries on the next poll.
 */
class RemoteLock(
    private val context: Context,
    private val commandsRemote: CommandsRemote,
    private val identity: MemberIdentity,
    private val ringer: FindPhoneRinger,
    private val bonusStore: BonusStore,
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
            DeviceCommand.GRANT_TIME -> {
                val today = LocalDate.now(ZoneId.systemDefault()).toString()
                bonusStore.add(today, command.minutes ?: 0)
            }
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
