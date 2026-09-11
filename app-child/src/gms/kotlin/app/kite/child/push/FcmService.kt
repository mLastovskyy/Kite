package app.kite.child.push

import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.push.PushTokenRemote
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FcmService :
    FirebaseMessagingService(),
    KoinComponent {
    private val pushTokenRemote: PushTokenRemote by inject()
    private val sessionManager: SessionManager by inject()
    private val handler: ChildPushHandler by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            if (sessionManager.authState.value is AuthState.SignedIn) {
                pushTokenRemote.register(PushTokenRemote.PLATFORM_FCM, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        handler.handle(this, message.data)
    }
}
