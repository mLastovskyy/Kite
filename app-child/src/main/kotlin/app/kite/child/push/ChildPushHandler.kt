package app.kite.child.push

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.kite.child.enforce.RemoteLock
import app.kite.child.enforce.RulesSyncer
import app.kite.child.location.PlacesMonitor
import app.kite.child.nav.ChildScreens
import app.kite.child.status.ChildNotices
import app.kite.child.tasks.TasksSyncer
import app.kite.core.location.PlacesRemote
import app.kite.core.notifications.Channels
import app.kite.core.rules.ACTION_RULES
import app.kite.core.tasks.TasksRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChildPushHandler(
    private val remoteLock: RemoteLock,
    private val placesMonitor: PlacesMonitor,
    private val rulesSyncer: RulesSyncer,
    private val tasksSyncer: TasksSyncer,
    private val notices: ChildNotices,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @SuppressLint("MissingPermission")
    fun handle(context: Context, data: Map<String, String>) {
        when (data["action"]) {
            ACTION_COMMAND -> scope.launch { runCatching { remoteLock.pollPending() } }
            PlacesRemote.ACTION_PLACES -> scope.launch { runCatching { placesMonitor.refresh() } }
            ACTION_RULES ->
                scope.launch {
                    runCatching { rulesSyncer.refresh() }
                    runCatching { remoteLock.pollPending() }
                }
            TasksRemote.ACTION_TASKS -> scope.launch { runCatching { tasksSyncer.refresh(notices) } }
        }
        val title = data["title"] ?: return
        val body = data["body"] ?: ""
        val channel = data["channel"] ?: Channels.STATUS
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val tap = ChildScreens.tap(context, ChildScreens.STATUS)
        manager.notify(title.hashCode(), Channels.build(context, channel, title, body, tap))
    }

    private companion object {
        const val ACTION_COMMAND = "command"
    }
}
