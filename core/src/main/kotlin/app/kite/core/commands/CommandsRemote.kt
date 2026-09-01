package app.kite.core.commands

import app.kite.core.auth.AuthException
import app.kite.core.auth.AuthState
import app.kite.core.auth.SessionManager
import app.kite.core.config.SupabaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DeviceCommand(val id: String, @SerialName("member_id") val memberId: String, val command: String) {
    companion object {
        const val LOCK = "lock"
        const val UNLOCK = "unlock"
    }
}

/**
 * device_commands over PostgREST: the parent inserts lock/unlock, the child fetches
 * pending rows (polling fallback) and acknowledges execution. The instant path is the
 * Realtime WebSocket subscription on the child (see the child's CommandListener).
 */
class CommandsRemote(
    private val httpClient: HttpClient,
    private val json: Json,
    private val sessionManager: SessionManager,
    private val baseUrl: String = SupabaseConfig.URL,
    private val apiKey: String = SupabaseConfig.PUBLISHABLE_KEY,
) {
    private val restUrl get() = "$baseUrl/rest/v1"

    /** Parent: queue a command for the child device. */
    suspend fun send(memberId: String, familyId: String, command: String): Result<Unit> = runCatching {
        val userId =
            (sessionManager.authState.value as? AuthState.SignedIn)?.session?.userId
                ?: throw AuthException("Нет активной сессии")
        val body =
            buildString {
                append("{\"member_id\":\"").append(memberId).append('"')
                append(",\"family_id\":\"").append(familyId).append('"')
                append(",\"command\":\"").append(command).append('"')
                append(",\"created_by\":\"").append(userId).append("\"}")
            }
        val response =
            httpClient.post("$restUrl/device_commands") {
                authHeaders(requireSession())
                header("Prefer", "return=minimal")
                setBody(body)
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    /** Child: commands not yet acknowledged, oldest first. */
    suspend fun pending(memberId: String): Result<List<DeviceCommand>> = runCatching {
        val response =
            httpClient.get("$restUrl/device_commands") {
                authHeaders(requireSession())
                parameter("member_id", "eq.$memberId")
                parameter("executed_at", "is.null")
                parameter("order", "created_at.asc")
                parameter("select", "id,member_id,command")
            }
        if (!response.status.isSuccess()) throw restError(response)
        json.decodeFromString<List<DeviceCommand>>(response.bodyAsText())
    }.mapNetworkError()

    /** Child: acknowledge execution. */
    suspend fun markExecuted(commandId: String): Result<Unit> = runCatching {
        val response =
            httpClient.patch("$restUrl/device_commands") {
                authHeaders(requireSession())
                parameter("id", "eq.$commandId")
                header("Prefer", "return=minimal")
                // Postgres parses the special timestamptz input value "now".
                setBody("""{"executed_at":"now"}""")
            }
        if (!response.status.isSuccess()) throw restError(response)
    }.mapNetworkError()

    private suspend fun requireSession(): String = sessionManager.validAccessToken() ?: throw AuthException("Нужно войти заново")

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(accessToken: String) {
        header("apikey", apiKey)
        header("Authorization", "Bearer $accessToken")
        contentType(ContentType.Application.Json)
    }

    private suspend fun restError(response: HttpResponse): Exception {
        runCatching { response.bodyAsText() }
        return AuthException("Ошибка сервера (${response.status.value})")
    }

    private fun <T> Result<T>.mapNetworkError(): Result<T> = recoverCatching { throwable ->
        throw if (throwable is AuthException) throwable else AuthException("Нет соединения с сервером")
    }
}
