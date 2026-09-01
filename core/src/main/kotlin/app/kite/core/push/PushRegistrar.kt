package app.kite.core.push

import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.platform.PlatformServices
import app.kite.core.platform.PlatformVariant

/**
 * Registers this device's push token once the user is signed in. On gms the token comes
 * from FCM; on hms/AOSP [PlatformServices.pushToken] returns null and registration is a
 * no-op (those wake-up paths come later). Idempotent — safe to call on every sign-in.
 */
class PushRegistrar(
    private val platformServices: PlatformServices,
    private val pushTokenRemote: PushTokenRemote,
    private val sessionManager: SessionManager,
) {
    suspend fun ensureRegistered() {
        if (sessionManager.authState.value !is AuthState.SignedIn) return
        val token = platformServices.pushToken() ?: return
        val platform = if (platformServices.variant == PlatformVariant.HMS) PushTokenRemote.PLATFORM_HMS else PushTokenRemote.PLATFORM_FCM
        pushTokenRemote.register(platform, token)
    }
}
