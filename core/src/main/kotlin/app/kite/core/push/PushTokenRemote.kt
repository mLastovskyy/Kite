package app.kite.core.push

import app.kite.core.auth.AuthException
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Registers this device's push token (FCM / HMS) in device_push_tokens so the server can
 * wake it. Upsert on (user_id, platform); the row is owned by the signed-in user via RLS.
 */
class PushTokenRemote(
    private val httpClient: HttpClient,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    suspend fun register(platform: String, token: String): Result<Unit> = runCatching {
        val accessToken = sessionManager.validAccessToken() ?: throw AuthException("Нужно войти заново")
        val userId =
            (sessionManager.authState.value as? AuthState.SignedIn)?.session?.userId
                ?: throw AuthException("Нет активной сессии")
        val body =
            JsonArray(
                listOf(
                    JsonObject(
                        mapOf(
                            "user_id" to JsonPrimitive(userId),
                            "platform" to JsonPrimitive(platform),
                            "token" to JsonPrimitive(token),
                        ),
                    ),
                ),
            )
        val response =
            httpClient.post("$baseUrl/rest/v1/device_push_tokens") {
                header("apikey", apiKey)
                header("Authorization", "Bearer $accessToken")
                header("Prefer", "resolution=merge-duplicates,return=minimal")
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.recoverCatching { throwable ->
        throw if (throwable is AuthException) throwable else AuthException("Нет соединения с сервером")
    }

    private suspend fun restError(response: HttpResponse): Exception {
        runCatching { response.bodyAsText() }
        return AuthException("Ошибка сервера (${response.status.value})")
    }

    companion object {
        const val PLATFORM_FCM = "fcm"
        const val PLATFORM_HMS = "hms"
    }
}
