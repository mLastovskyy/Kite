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

    suspend fun signUp(email: String, password: String): Result<TokenResponse> = request {
        httpClient.post("$authUrl/signup") {
            commonHeaders()
            setBody(credentialsBody(email, password))
        }
    }

    suspend fun signIn(email: String, password: String): Result<TokenResponse> = request {
        httpClient.post("$authUrl/token") {
            commonHeaders()
            parameter("grant_type", "password")
            setBody(credentialsBody(email, password))
        }
    }

    suspend fun refresh(refreshToken: String): Result<TokenResponse> = request {
        httpClient.post("$authUrl/token") {
            commonHeaders()
            parameter("grant_type", "refresh_token")
            setBody(JsonObject(mapOf("refresh_token" to JsonPrimitive(refreshToken))))
        }
    }

    /** Sends the password-reset email (GoTrue /recover). */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        val response =
            httpClient.post("$authUrl/recover") {
                commonHeaders()
                setBody(JsonObject(mapOf("email" to JsonPrimitive(email))))
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

    private suspend fun authError(response: HttpResponse): Exception {
        val body = runCatching { json.decodeFromString<AuthErrorBody>(response.bodyAsText()) }.getOrNull()
        val message =
            when {
                body?.message() != null && body.message().isNotBlank() -> body.message()
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
