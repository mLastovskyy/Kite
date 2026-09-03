package app.kite.child.push

import androidx.core.app.NotificationManagerCompat
import app.kite.child.enforce.RemoteLock
import app.kite.child.location.PlacesMonitor
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.location.PlacesRemote
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
 * FCM entry point on the child (gms). Most pushes are SILENT data messages that just wake
 * the device to apply a queued command (e.g. an instant lock) even if the app was killed —
 * no notification is shown to the child for that, matching the calm, non-nagging UX. A push
 * that carries an explicit title is shown as a tidy single-line notification.
 */
class FcmService :
    FirebaseMessagingService(),
    KoinComponent {
    private val pushTokenRemote: PushTokenRemote by inject()
    private val sessionManager: SessionManager by inject()
    private val remoteLock: RemoteLock by inject()
    private val placesMonitor: PlacesMonitor by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            if (sessionManager.authState.value is AuthState.SignedIn) {
                pushTokenRemote.register(PushTokenRemote.PLATFORM_FCM, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        // Wake to apply any pending remote commands (lock/unlock) — silent.
        if (data["action"] == "command") {
            scope.launch { runCatching { remoteLock.pollPending() } }
        }
        // The parent saved or edited a place: re-fetch now instead of at the next pull.
        if (data["action"] == PlacesRemote.ACTION_PLACES) {
            scope.launch { runCatching { placesMonitor.refresh() } }
        }
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body ?: ""
        val channel = data["channel"] ?: Channels.STATUS
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(title.hashCode(), Channels.build(this, channel, title, body))
        }
    }
}
