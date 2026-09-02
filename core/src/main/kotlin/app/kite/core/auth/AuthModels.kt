package app.kite.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Supabase GoTrue token response (grant_type password / refresh_token, and POST /verify). */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long = 0,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: AuthUser? = null,
)

@Serializable
data class AuthUser(val id: String, val email: String? = null, @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null)

/** GoTrue error body. */
@Serializable
data class AuthErrorBody(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val msg: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
) {
    fun message(): String = errorDescription ?: msg ?: error ?: "Ошибка авторизации"

    /** Machine-readable code: modern `error_code`, else the legacy OAuth-style `error`. */
    fun code(): String? = errorCode ?: error
}

/**
 * Auth failure with a Russian [message] for the UI. [code] is the GoTrue `error_code` (or our
 * Edge Function's `error`) when known, so callers can branch — e.g. `email_not_confirmed` on
 * sign-in routes to the confirmation-code screen. [status] is the HTTP status, null for
 * transport failures.
 */
class AuthException(override val message: String, val code: String? = null, val status: Int? = null) : Exception(message) {
    companion object {
        const val EMAIL_NOT_CONFIRMED = "email_not_confirmed"
        const val ALREADY_REGISTERED = "already_registered"
        const val OTP_EXPIRED = "otp_expired"
    }
}

/** Local session persisted (encrypted) between launches. */
@Serializable
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?,
    val expiresAt: Long,
    val emailConfirmed: Boolean,
) {
    /**
     * A parent who started without an account. Fully functional, but there is no way to sign
     * in to this family from another phone until an email is linked (Settings → Аккаунт).
     */
    val isAnonymous: Boolean get() = email.isNullOrBlank()
}

/** What the UI observes. */
sealed interface AuthState {
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val session: Session) : AuthState
}
