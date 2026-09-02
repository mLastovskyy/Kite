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
 * Offline-first and long-lived: a stored session is trusted until GoTrue definitively
 * rejects the refresh token. A network blip or a server outage must not sign the user out —
 * the parent should not have to remember credentials again for as long as the app is installed.
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

    /** Registration step 1: emails a 6-digit code. Also re-issues one for an unconfirmed account. */
    suspend fun requestSignUpCode(email: String, password: String): Result<Unit> = authClient.requestSignUpCode(email, password)

    /** Registration step 2: the code confirms the email and signs in. */
    suspend fun verifySignUpCode(email: String, code: String): Result<Session> =
        authClient.verifySignUpCode(email, code).map { persist(it) }

    suspend fun signIn(email: String, password: String): Result<Session> = authClient.signIn(email, password).map { persist(it) }

    /** Child device: anonymous session so it can redeem a pairing invite. */
    suspend fun signInAnonymously(): Result<Session> = authClient.signInAnonymously().map { persist(it) }

    /** Password reset step 1: emails a 6-digit code. */
    suspend fun requestPasswordResetCode(email: String): Result<Unit> = authClient.requestPasswordResetCode(email)

    /**
     * Password reset step 2: the code yields a recovery session, the new password is set on
     * it, and only then is the session kept — so a failed password update leaves the user
     * signed out with a clear error instead of half-reset.
     */
    suspend fun resetPassword(email: String, code: String, newPassword: String): Result<Session> = runCatching {
        val token = authClient.verifyPasswordResetCode(email, code).getOrThrow()
        authClient.updatePassword(token.accessToken, newPassword).getOrThrow()
        persist(token)
    }

    suspend fun setPassword(newPassword: String): Result<Unit> {
        val token = currentSession()?.accessToken ?: return Result.failure(AuthException("Нет активной сессии"))
        return authClient.updatePassword(token, newPassword)
    }

    /** Anonymous parent → account, step 1: emails a code that will attach [email] + [password]. */
    suspend fun requestLinkEmailCode(email: String, password: String): Result<Unit> {
        val token = validAccessToken() ?: return Result.failure(AuthException("Нет активной сессии"))
        return authClient.requestLinkEmailCode(token, email, password)
    }

    /**
     * Step 2: the server sets email + password on this very user, so the family stays. The
     * old JWT still says "anonymous, no email" — refresh to pick up the linked identity; if
     * that refresh fails (network), patch the local copy so the UI is truthful right away.
     */
    suspend fun linkEmail(email: String, code: String, password: String): Result<Session> = runCatching {
        val token = validAccessToken() ?: throw AuthException("Нет активной сессии")
        authClient.verifyLinkEmail(token, email, code, password).getOrThrow()
        val current = currentSession() ?: throw AuthException("Нет активной сессии")
        authClient.refresh(current.refreshToken)
            .map { persist(it) }
            .getOrElse { store(current.copy(email = email.trim(), emailConfirmed = true)) }
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
                        // Only a definitive 4xx rejection of the refresh token ends the session.
                        // No network (status null), 429 and 5xx keep it: retry next time.
                        val status = (throwable as? AuthException)?.status
                        if (status != null && status in REJECTED_STATUSES) signOutLocal()
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
        return store(session)
    }

    private fun store(session: Session): Session {
        secureStore.putString(KEY_SESSION, json.encodeToString(Session.serializer(), session))
        _authState.value = AuthState.SignedIn(session)
        return session
    }

    private companion object {
        const val KEY_SESSION = "supabase_session"
        const val SKEW_SECONDS = 60L
        const val DEFAULT_TTL_SECONDS = 3600L
        val REJECTED_STATUSES = 400..403
    }
}
