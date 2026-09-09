package app.kite.parent.push

import androidx.core.app.NotificationManagerCompat
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.notifications.Channels
import app.kite.core.push.PushTokenRemote
import com.huawei.hms.push.HmsMessageService
import com.huawei.hms.push.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Push Kit entry point on the parent (hms) — the same job [app.kite.parent.push.FcmService]
 * does in the gms flavor, and deliberately the same shape: the server sends one payload and
 * both sides read the same keys, so a notification cannot look different on a Huawei phone.
 */
class HmsPushService :
    HmsMessageService(),
    KoinComponent {
    private val pushTokenRemote: PushTokenRemote by inject()
    private val sessionManager: SessionManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            if (sessionManager.authState.value is AuthState.SignedIn) {
                pushTokenRemote.register(PushTokenRemote.PLATFORM_HMS, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.payload()
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body ?: ""
        val channel = data["channel"] ?: Channels.REQUESTS
        val notificationId = (data["collapse"] ?: title).hashCode()
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(notificationId, Channels.build(this, channel, title, body))
        }
    }
}

/**
 * Push Kit carries the payload as one JSON string and parses it into a map itself — but only
 * when it feels like it. Reading the string back is the fallback, so a message can never arrive
 * empty just because the SDK did not do the parsing.
 */
private fun RemoteMessage.payload(): Map<String, String> {
    dataOfMap?.takeIf { it.isNotEmpty() }?.let { return it }
    val raw = data?.takeIf { it.isNotBlank() } ?: return emptyMap()
    return runCatching {
        val json = JSONObject(raw)
        json.keys().asSequence().associateWith { json.optString(it) }
    }.getOrDefault(emptyMap())
}
