package app.kite.parent.push

import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.push.PushTokenRemote
import app.kite.parent.notifications.ParentNotifier
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

    override fun onMessageReceived(message: RemoteMessage) {
        ParentNotifier.fromPush(this, message.data)
    }
}
