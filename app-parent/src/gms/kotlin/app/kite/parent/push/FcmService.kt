package app.kite.parent.push

import androidx.core.app.NotificationManagerCompat
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.notifications.Channels
import app.kite.core.push.PushTokenRemote
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * FCM entry point on the parent (gms). Shows the child's requests (unlock / extra time /
 * removal) and protection alerts as calm single-line notifications; the channel decides
 * prominence (requests/alerts are HIGH). Copy comes from the server payload.
 */
class FcmService :
    FirebaseMessagingService(),
    KoinComponent {
    private val pushTokenRemote: PushTokenRemote by inject()
    private val sessionManager: SessionManager by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            if (sessionManager.authState.value is AuthState.SignedIn) {
                pushTokenRemote.register(PushTokenRemote.PLATFORM_FCM, token)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body ?: ""
        val channel = data["channel"] ?: Channels.REQUESTS
        val notificationId = (data["collapse"] ?: title).hashCode()
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(notificationId, Channels.build(this, channel, title, body))
        }
    }
}
