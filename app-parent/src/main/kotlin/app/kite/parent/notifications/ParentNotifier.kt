package app.kite.parent.notifications

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import app.kite.core.approval.ApprovalRequest
import app.kite.core.navigation.Destination
import app.kite.core.navigation.PendingDestination
import app.kite.core.notifications.Channels
import app.kite.parent.MainActivity

object ParentScreens {
    const val HOME = "home"
    const val REQUESTS = "requests"
    const val TASKS = "tasks"
    const val MAP = "map"

    fun forRequest(type: String?): String = if (type == ApprovalRequest.TYPE_TASK_REQUEST) TASKS else REQUESTS
}

object ParentNotifier {
    @SuppressLint("MissingPermission")
    fun show(context: Context, id: Int, channel: String, title: String, body: String, destination: Destination) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val tap = PendingDestination.tap(context, MainActivity::class.java, destination)
        manager.notify(id, Channels.build(context, channel, title, body, tap))
    }

    fun fromPush(context: Context, data: Map<String, String>) {
        val title = data["title"] ?: return
        val body = data["body"] ?: ""
        val channel = data["channel"] ?: Channels.REQUESTS
        val childId = data["child_member_id"]
        val requestId = data["request_id"]
        val destination =
            when {
                data["kind"] == "approval" -> Destination(ParentScreens.forRequest(data["type"]), childId)
                data["action"] == "place_event" -> Destination(ParentScreens.MAP, childId)
                else -> Destination(ParentScreens.HOME, childId)
            }
        if (requestId != null) SeenRequests.mark(context, requestId)
        val id = requestId?.hashCode() ?: (title + body + System.currentTimeMillis()).hashCode()
        show(context, id, channel, title, body, destination)
    }
}

object SeenRequests {
    private const val PREFS = "pending_requests"
    private const val KEY_SEEN = "seen_ids"

    fun all(context: Context): Set<String> = prefs(context).getStringSet(KEY_SEEN, emptySet()).orEmpty()

    fun mark(context: Context, id: String) = save(context, all(context) + id)

    fun save(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SEEN, ids).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
