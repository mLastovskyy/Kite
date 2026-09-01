package app.kite.core.auth

import app.kite.core.secure.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Owns the current [Session]: loads it from [SecureStore] on start, persists it on every
 * change, refreshes the access token when it is about to expire, and clears everything on
 * sign-out. The single source of truth the UI observes via [authState].
 *
 * Offline-first: a stored session is trusted until a refresh actually fails with an auth
 * error — a network blip must not sign the user out.
 */
class SessionManager(
    private val authClient: SupabaseAuthClient,
    private val secureStore: SecureStore,
    private val json: Json,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val refreshMutex = Mutex()
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Call once at startup. Loads any persisted session without a network round-trip. */
    fun bootstrap() {
        val stored = secureStore.getString(KEY_SESSION)?.let { raw -> runCatching { json.decodeFromString<Session>(raw) }.getOrNull() }
        _authState.value = if (stored != null) AuthState.SignedIn(stored) else AuthState.SignedOut
    }

    suspend fun signUp(email: String, password: String): Result<Session> = authClient.signUp(email, password).map { persist(it) }

    suspend fun signIn(email: String, password: String): Result<Session> = authClient.signIn(email, password).map { persist(it) }

    suspend fun sendPasswordReset(email: String): Result<Unit> = authClient.sendPasswordReset(email)

    suspend fun setPassword(newPassword: String): Result<Unit> {
        val token = currentSession()?.accessToken ?: return Result.failure(AuthException("Нет активной сессии"))
        return authClient.updatePassword(token, newPassword)
    }

    /** Returns a valid access token, refreshing first when it is within [SKEW_SECONDS] of expiry. */
    suspend fun validAccessToken(): String? {
        val session = currentSession() ?: return null
        if (session.expiresAt - now() > SKEW_SECONDS) return session.accessToken
        return refreshMutex.withLock {
            val fresh = currentSession() ?: return null
            if (fresh.expiresAt - now() > SKEW_SECONDS) {
                fresh.accessToken
            } else {
                authClient.refresh(fresh.refreshToken)
                    .map { persist(it).accessToken }
                    .getOrElse { throwable ->
                        // Only a real auth failure ends the session; a network error keeps it.
                        if (throwable is AuthException && throwable.message.contains("сервер").not()) signOutLocal()
                        null
                    }
            }
        }
    }

    suspend fun signOut() {
        currentSession()?.let { authClient.signOut(it.accessToken) }
        signOutLocal()
    }

    private fun signOutLocal() {
        secureStore.remove(KEY_SESSION)
        _authState.value = AuthState.SignedOut
    }

    private fun currentSession(): Session? = (_authState.value as? AuthState.SignedIn)?.session

    private fun persist(token: TokenResponse): Session {
        val existing = currentSession()
        val session =
            Session(
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                userId = token.user?.id ?: existing?.userId.orEmpty(),
                email = token.user?.email ?: existing?.email,
                expiresAt = if (token.expiresAt > 0) token.expiresAt else now() + DEFAULT_TTL_SECONDS,
                emailConfirmed = token.user?.emailConfirmedAt != null || existing?.emailConfirmed == true,
            )
        secureStore.putString(KEY_SESSION, json.encodeToString(Session.serializer(), session))
        _authState.value = AuthState.SignedIn(session)
        return session
    }

    private companion object {
        const val KEY_SESSION = "supabase_session"
        const val SKEW_SECONDS = 60L
        const val DEFAULT_TTL_SECONDS = 3600L
    }
}
