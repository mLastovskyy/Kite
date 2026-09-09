package app.kite.child.push

import androidx.core.app.NotificationManagerCompat
import app.kite.child.enforce.RemoteLock
import app.kite.child.enforce.RulesSyncer
import app.kite.child.location.PlacesMonitor
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.location.PlacesRemote
import app.kite.core.notifications.Channels
import app.kite.core.push.PushTokenRemote
import app.kite.core.rules.ACTION_RULES
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Push Kit entry point on the child (hms), matching [app.kite.child.push.FcmService] key for
 * key. On EMUI this is the path that matters most: the system kills the app's own sockets
 * within hours, and a silent data push is what wakes it to apply a lock the parent just sent.
 * Nothing is shown to the child for those — only a payload with a title becomes a notification.
 */
class HmsPushService :
    HmsMessageService(),
    KoinComponent {
    private val pushTokenRemote: PushTokenRemote by inject()
    private val sessionManager: SessionManager by inject()
    private val remoteLock: RemoteLock by inject()
    private val placesMonitor: PlacesMonitor by inject()
    private val rulesSyncer: RulesSyncer by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            if (sessionManager.authState.value is AuthState.SignedIn) {
                pushTokenRemote.register(PushTokenRemote.PLATFORM_HMS, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.dataOfMap.orEmpty()
        if (data["action"] == "command") {
            scope.launch { runCatching { remoteLock.pollPending() } }
        }
        if (data["action"] == PlacesRemote.ACTION_PLACES) {
            scope.launch { runCatching { placesMonitor.refresh() } }
        }
        if (data["action"] == ACTION_RULES) {
            scope.launch {
                runCatching { rulesSyncer.refresh() }
                runCatching { remoteLock.pollPending() }
            }
        }
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body ?: ""
        val channel = data["channel"] ?: Channels.STATUS
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(title.hashCode(), Channels.build(this, channel, title, body))
        }
    }
}
