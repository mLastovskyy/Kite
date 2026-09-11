package app.kite.child.push

import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
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

class HmsPushService :
    HmsMessageService(),
    KoinComponent {
    private val pushTokenRemote: PushTokenRemote by inject()
    private val sessionManager: SessionManager by inject()
    private val handler: ChildPushHandler by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            if (sessionManager.authState.value is AuthState.SignedIn) {
                pushTokenRemote.register(PushTokenRemote.PLATFORM_HMS, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        handler.handle(this, message.payload())
    }
}

private fun RemoteMessage.payload(): Map<String, String> {
    dataOfMap?.takeIf { it.isNotEmpty() }?.let { return it }
    val raw = data?.takeIf { it.isNotBlank() } ?: return emptyMap()
    return runCatching {
        val json = JSONObject(raw)
        json.keys().asSequence().associateWith { json.optString(it) }
    }.getOrDefault(emptyMap())
}
