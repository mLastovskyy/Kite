package app.kite.core.auth

import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
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
 * Email verification never uses links. Our `auth-email` Edge Function asks GoTrue for a
 * 6-digit OTP (`admin/generate_link`) and delivers it through the project's own Gmail SMTP;
 * the app then redeems it with GoTrue's `POST /verify`, which hands back a real session.
 * Registration and password reset both work this way.
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
     * Step 1 of registration: the Edge Function creates the account (unconfirmed) or reuses
     * an existing unconfirmed one, stores [password] on it and emails a 6-digit code.
     * An already-confirmed email fails with [AuthException.ALREADY_REGISTERED].
     */
    suspend fun requestSignUpCode(email: String, password: String): Result<Unit> =
        callAuthEmail(mapOf("action" to "signup_code", "email" to email.trim(), "password" to password))

    /** Step 2 of registration: redeems the emailed code; the account becomes confirmed and signed in. */
    suspend fun verifySignUpCode(email: String, code: String): Result<TokenResponse> = verifyOtp("signup", email, code)

    /** Step 1 of password reset: emails a 6-digit code. Never reveals whether the address exists. */
    suspend fun requestPasswordResetCode(email: String): Result<Unit> =
        callAuthEmail(mapOf("action" to "recovery_code", "email" to email.trim()))

    /** Step 2 of password reset: redeems the code for a session; caller then sets the new password. */
    suspend fun verifyPasswordResetCode(email: String, code: String): Result<TokenResponse> = verifyOtp("recovery", email, code)

    /**
     * Anonymous parent → account, step 1: the Edge Function emails a 6-digit code to [email].
     * The bearer session must be anonymous (no email yet); the function keeps the hashed code
     * itself because GoTrue cannot mint an OTP for a user without an email.
     */
    suspend fun requestLinkEmailCode(accessToken: String, email: String, password: String): Result<Unit> = callAuthEmail(
        mapOf("action" to "link_email_code", "email" to email.trim(), "password" to password),
        accessToken = accessToken,
    )

    /**
     * Step 2: the code attaches [email] + [password] to the SAME auth user (the family stays
     * with that user id). The caller must refresh the session afterwards to pick up the email.
     */
    suspend fun verifyLinkEmail(accessToken: String, email: String, code: String, password: String): Result<Unit> = callAuthEmail(
        mapOf("action" to "link_email_verify", "email" to email.trim(), "code" to code.trim(), "password" to password),
        accessToken = accessToken,
    )

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

    /** Sets a new password for the current session (GoTrue PUT /user). */
    suspend fun updatePassword(accessToken: String, newPassword: String): Result<Unit> = runCatching {
        val response =
            httpClient.put("$authUrl/user") {
                commonHeaders()
                header("Authorization", "Bearer $accessToken")
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

    private suspend fun verifyOtp(type: String, email: String, code: String): Result<TokenResponse> = request {
        httpClient.post("$authUrl/verify") {
            commonHeaders()
            setBody(
                JsonObject(
                    mapOf(
                        "type" to JsonPrimitive(type),
                        "email" to JsonPrimitive(email.trim()),
                        "token" to JsonPrimitive(code.trim()),
                    ),
                ),
            )
        }
    }

    private suspend fun callAuthEmail(fields: Map<String, String>, accessToken: String? = null): Result<Unit> = runCatching {
        val response =
            httpClient.post("$functionsUrl/auth-email") {
                commonHeaders()
                // Link actions identify the caller; signup/recovery run before any session exists.
                if (accessToken != null) header("Authorization", "Bearer $accessToken")
                setBody(JsonObject(fields.mapValues { JsonPrimitive(it.value) }))
            }
        if (!response.status.isSuccess()) throw functionError(response)
    }.recoverMessage()

    private fun HttpRequestBuilder.commonHeaders() {
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

    /** Errors from our Edge Function: `{"error": "<code>"}`. */
    private suspend fun functionError(response: HttpResponse): AuthException {
        val text = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
        val code = FUNCTION_ERROR_CODES.firstOrNull { text.contains(it) }
        val message =
            when (code) {
                AuthException.ALREADY_REGISTERED -> "Этот email уже зарегистрирован — войдите или сбросьте пароль"
                "already_linked" -> "К этому аккаунту уже привязан email"
                "weak_password" -> "Пароль слишком короткий (минимум 6 символов)"
                "email_required" -> "Введите email"
                "mail_failed" -> "Не удалось отправить письмо — попробуйте позже"
                "invalid_code" -> "Неверный или устаревший код"
                "too_many_attempts" -> "Слишком много попыток — запросите новый код"
                "rate_limited" -> "Код уже отправлен — подождите минуту"
                "unauthorized" -> "Сессия истекла — перезапустите приложение"
                else -> "Ошибка сервера (${response.status.value})"
            }
        return AuthException(message, code, response.status.value)
    }

    /** Errors from GoTrue itself, mapped by `error_code` to Russian. */
    private suspend fun authError(response: HttpResponse): AuthException {
        val body = runCatching { json.decodeFromString<AuthErrorBody>(response.bodyAsText()) }.getOrNull()
        val code = body?.code()
        val raw = body?.message().orEmpty()
        val status = response.status
        val message =
            when {
                status == HttpStatusCode.TooManyRequests ||
                    code == "over_request_rate_limit" ||
                    code == "over_email_send_rate_limit" ||
                    raw.contains("rate limit", ignoreCase = true) ->
                    "Слишком много попыток — подождите немного и попробуйте снова"
                code == AuthException.OTP_EXPIRED -> "Неверный или устаревший код"
                code == AuthException.EMAIL_NOT_CONFIRMED -> "Почта не подтверждена"
                code == "invalid_credentials" || code == "invalid_grant" -> "Неверный email или пароль"
                code == "user_not_found" -> "Пользователь не найден"
                code == "weak_password" -> "Пароль слишком короткий (минимум 6 символов)"
                code == "same_password" -> "Новый пароль совпадает со старым"
                code == "anonymous_provider_disabled" -> "Анонимный вход отключён на сервере"
                code == "refresh_token_not_found" || code == "refresh_token_already_used" || code == "session_not_found" ->
                    "Сессия истекла — войдите снова"
                status == HttpStatusCode.BadRequest -> "Неверный email или пароль"
                status == HttpStatusCode.Forbidden -> "Неверный или устаревший код"
                else -> "Ошибка сервера (${code ?: status.value})"
            }
        return AuthException(message, code, status.value)
    }

    private fun <T> Result<T>.recoverMessage(): Result<T> = recoverCatching { throwable ->
        throw when (throwable) {
            is AuthException -> throwable
            else -> AuthException("Нет соединения с сервером")
        }
    }

    private companion object {
        val FUNCTION_ERROR_CODES =
            listOf(
                AuthException.ALREADY_REGISTERED,
                "already_linked",
                "weak_password",
                "email_required",
                "mail_failed",
                "signup_failed",
                "invalid_code",
                "too_many_attempts",
                "rate_limited",
                "unauthorized",
                "unknown_action",
            )
    }
}
