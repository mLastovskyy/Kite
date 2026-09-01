package app.kite.core.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Supabase GoTrue token response (grant_type password / refresh_token). */
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
)

/** What the UI observes. */
sealed interface AuthState {
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val session: Session) : AuthState
}
