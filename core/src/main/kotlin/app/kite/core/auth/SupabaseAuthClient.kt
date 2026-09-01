package app.kite.core.auth

import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Thin Ktor client over Supabase GoTrue (Auth) REST. Email + password is the primary path
 * that must work on 100 % of devices (CLAUDE.md); phone OTP and social sign-in come later.
 *
 * Every call returns [Result] with a human-readable Russian message on failure. Nothing here
 * touches storage — [SessionManager] owns persistence.
 */
class SupabaseAuthClient(
    private val httpClient: HttpClient,
    private val json: Json,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val authUrl get() = "$baseUrl/auth/v1"
    private val functionsUrl get() = "$baseUrl/functions/v1"

    /**
     * Sign-up via our `auth-email` Edge Function: it creates the account through the admin
     * API (GoTrue sends nothing, so the built-in mailer's rate limit is never hit) and sends
     * a branded welcome through our own Gmail SMTP. The account is created already-confirmed,
     * so we sign in straight away and return the session.
     */
    suspend fun signUp(email: String, password: String): Result<TokenResponse?> = runCatching {
        val response =
            httpClient.post("$functionsUrl/auth-email") {
                commonHeaders()
                setBody(
                    JsonObject(
                        mapOf(
                            "action" to JsonPrimitive("signup"),
                            "email" to JsonPrimitive(email.trim()),
                            "password" to JsonPrimitive(password),
                        ),
                    ),
                )
            }
        if (!response.status.isSuccess()) throw signupError(response)
        signIn(email, password).getOrThrow()
    }.recoverMessage()

    suspend fun signIn(email: String, password: String): Result<TokenResponse> = request {
        httpClient.post("$authUrl/token") {
            commonHeaders()
            parameter("grant_type", "password")
            setBody(credentialsBody(email, password))
        }
    }

    /**
     * Anonymous sign-in for the child device: it needs a JWT to redeem a pairing invite,
     * but the child has no email. Requires «Allow anonymous sign-ins» enabled on the
     * project; otherwise GoTrue returns 422 and this surfaces a clear message.
     */
    suspend fun signInAnonymously(): Result<TokenResponse> = request {
        httpClient.post("$authUrl/signup") {
            commonHeaders()
            setBody(JsonObject(emptyMap()))
        }
    }

    suspend fun refresh(refreshToken: String): Result<TokenResponse> = request {
        httpClient.post("$authUrl/token") {
            commonHeaders()
            parameter("grant_type", "refresh_token")
            setBody(JsonObject(mapOf("refresh_token" to JsonPrimitive(refreshToken))))
        }
    }

    /**
     * Sends the password-reset email through our `auth-email` Edge Function (Gmail SMTP,
     * branded template) instead of GoTrue's rate-limited mailer. The function never reveals
     * whether the address exists, so this always succeeds on a reachable server.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        val response =
            httpClient.post("$functionsUrl/auth-email") {
                commonHeaders()
                setBody(
                    JsonObject(
                        mapOf(
                            "action" to JsonPrimitive("recovery"),
                            "email" to JsonPrimitive(email.trim()),
                        ),
                    ),
                )
            }
        if (!response.status.isSuccess()) throw authError(response)
    }.recoverMessage()

    /** Sets a new password / links an email for the current session (GoTrue PUT /user). */
    suspend fun updatePassword(accessToken: String, newPassword: String): Result<Unit> = runCatching {
        val response =
            httpClient.post("$authUrl/user") {
                commonHeaders()
                header("Authorization", "Bearer $accessToken")
                // GoTrue accepts PUT, but POST with X-HTTP-Method-Override keeps one path.
                header("X-HTTP-Method-Override", "PUT")
                setBody(JsonObject(mapOf("password" to JsonPrimitive(newPassword))))
            }
        if (!response.status.isSuccess()) throw authError(response)
    }.recoverMessage()

    suspend fun signOut(accessToken: String): Result<Unit> = runCatching {
        httpClient.post("$authUrl/logout") {
            commonHeaders()
            header("Authorization", "Bearer $accessToken")
        }
        Unit
    }.recoverMessage()

    private fun io.ktor.client.request.HttpRequestBuilder.commonHeaders() {
        header("apikey", apiKey)
        contentType(ContentType.Application.Json)
    }

    private fun credentialsBody(email: String, password: String): JsonObject = JsonObject(
        mapOf(
            "email" to JsonPrimitive(email.trim()),
            "password" to JsonPrimitive(password),
        ),
    )

    private suspend fun request(block: suspend () -> HttpResponse): Result<TokenResponse> = runCatching {
        val response = block()
        if (!response.status.isSuccess()) throw authError(response)
        response.body<TokenResponse>()
    }.recoverMessage()

    private suspend fun signupError(response: HttpResponse): Exception {
        val text = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        val message =
            when {
                text.contains("already_registered") -> "Этот email уже зарегистрирован"
                text.contains("weak_password") -> "Пароль слишком короткий (минимум 6 символов)"
                text.contains("email_required") -> "Введите email"
                else -> "Не удалось создать аккаунт"
            }
        return AuthException(message)
    }

    private suspend fun authError(response: HttpResponse): Exception {
        val body = runCatching { json.decodeFromString<AuthErrorBody>(response.bodyAsText()) }.getOrNull()
        val raw = body?.message()
        val message =
            when {
                // GoTrue's built-in mailer allows only a couple of emails per hour; the raw
                // "email rate limit exceeded" must never reach the UI in English.
                response.status == HttpStatusCode.TooManyRequests ||
                    raw?.contains("rate limit", ignoreCase = true) == true ->
                    "Слишком много попыток — подождите немного и попробуйте снова"
                !raw.isNullOrBlank() -> raw
                response.status == HttpStatusCode.BadRequest -> "Неверный email или пароль"
                else -> "Ошибка сети (${response.status.value})"
            }
        return AuthException(message)
    }

    private fun <T> Result<T>.recoverMessage(): Result<T> = recoverCatching { throwable ->
        throw when (throwable) {
            is AuthException -> throwable
            else -> AuthException("Нет соединения с сервером")
        }
    }
}

class AuthException(override val message: String) : Exception(message)
